package dev.autotix.infrastructure.platform.wecom;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles WeCom (企业微信客服) webhook GET (URL verification) and POST (message events).
 *
 * <h3>GET — URL verification handshake</h3>
 * WeCom sends {@code GET ?msg_signature=&timestamp=&nonce=&echostr=}.
 * We verify the signature, decrypt the echostr, and return the plaintext.
 *
 * <h3>POST — message notification</h3>
 * WeCom sends {@code POST ?msg_signature=&timestamp=&nonce=} with an XML body
 * containing an {@code <Encrypt>} element. We verify + decrypt, extract the
 * notification token and open_kfid, and pull actual messages via
 * {@link WecomClient#syncMsg}.
 */
@Component
public class WecomWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(WecomWebhookHandler.class);
    private static final int SYNC_MSG_LIMIT = 1000;

    private final WecomClient wecomClient;

    @Autowired
    public WecomWebhookHandler(WecomClient wecomClient) {
        this.wecomClient = wecomClient;
    }

    // -------------------------------------------------------------------------
    // GET: URL verification
    // -------------------------------------------------------------------------

    /**
     * Handles the WeCom callback URL verification GET request.
     *
     * @param channel      the WeCom channel
     * @param msgSignature signature from query param
     * @param timestamp    timestamp from query param
     * @param nonce        nonce from query param
     * @param echostr      encrypted echostr from query param
     * @return decrypted plaintext echostr to return as HTTP response body
     */
    public String handleGet(Channel channel, String msgSignature,
                            String timestamp, String nonce, String echostr) {
        WecomCredentials creds = WecomCredentials.from(channel.credential());
        verifySignature(creds.token, timestamp, nonce, echostr, msgSignature, channel.id().value());
        return WecomCrypto.decrypt(creds.encodingAesKey, creds.corpId, echostr);
    }

    // -------------------------------------------------------------------------
    // POST: message events
    // -------------------------------------------------------------------------

    /**
     * Handles a WeCom POST webhook event.
     * Returns a list of {@link TicketEvent}s (one per text message pulled from kf/sync_msg).
     *
     * @param channel      the WeCom channel
     * @param msgSignature signature from query param
     * @param timestamp    timestamp from query param
     * @param nonce        nonce from query param
     * @param xmlBody      raw request body (XML with {@code <Encrypt>} element)
     * @return list of parsed TicketEvents (may be empty if no text messages found)
     */
    public List<TicketEvent> handlePost(Channel channel, String msgSignature,
                                        String timestamp, String nonce, String xmlBody) {
        WecomCredentials creds = WecomCredentials.from(channel.credential());

        // 1. Parse <Encrypt> from XML body
        String encryptedMsg = extractXmlElement(xmlBody, "Encrypt");
        if (encryptedMsg == null || encryptedMsg.isEmpty()) {
            log.warn("[WeCom] POST body missing <Encrypt> element for channel {}", channel.id().value());
            return Collections.emptyList();
        }

        // 2. Verify signature
        verifySignature(creds.token, timestamp, nonce, encryptedMsg, msgSignature, channel.id().value());

        // 3. Decrypt
        String plainXml = WecomCrypto.decrypt(creds.encodingAesKey, creds.corpId, encryptedMsg);
        log.debug("[WeCom] Decrypted webhook XML: {}", plainXml);

        // 4. Parse notification fields
        String voucher = extractXmlElement(plainXml, "Token");
        String openKfId = extractXmlElement(plainXml, "OpenKfId");

        if (voucher == null || voucher.isEmpty()) {
            log.warn("[WeCom] Decrypted XML missing <Token> for channel {}", channel.id().value());
            return Collections.emptyList();
        }
        if (openKfId == null || openKfId.isEmpty()) {
            // Fall back to credentials open_kfid
            openKfId = creds.openKfId;
        }

        // 5. Pull messages via kf/sync_msg with cursor pagination
        String accessToken = wecomClient.getAccessToken(creds.corpId, creds.secret);
        return pullMessages(channel, accessToken, voucher, openKfId);
    }

    // -------------------------------------------------------------------------
    // Message pulling
    // -------------------------------------------------------------------------

    private List<TicketEvent> pullMessages(Channel channel, String accessToken,
                                           String voucher, String openKfId) {
        List<TicketEvent> result = new ArrayList<TicketEvent>();
        String cursor = "";
        boolean hasMore = true;

        while (hasMore) {
            JSONObject resp = wecomClient.syncMsg(accessToken, voucher, openKfId, cursor, SYNC_MSG_LIMIT);

            JSONArray msgList = resp.getJSONArray("msg_list");
            if (msgList != null) {
                for (int i = 0; i < msgList.size(); i++) {
                    JSONObject msg = msgList.getJSONObject(i);
                    if (msg == null) continue;

                    String msgType = msg.getString("msgtype");
                    if (!"text".equals(msgType)) {
                        log.debug("[WeCom] Skipping msgtype='{}' (not text)", msgType);
                        continue;
                    }

                    JSONObject textNode = msg.getJSONObject("text");
                    if (textNode == null) continue;
                    String content = textNode.getString("content");
                    if (content == null) content = "";

                    String externalUserId = msg.getString("external_userid");
                    if (externalUserId == null) externalUserId = "";

                    long sendTime = msg.getLongValue("send_time"); // unix seconds
                    Instant occurredAt = sendTime > 0 ? Instant.ofEpochSecond(sendTime) : Instant.now();

                    // externalThreadId: uniquely identifies a conversation thread
                    String externalThreadId = externalUserId + "@" + openKfId;
                    String customerIdentifier = "wecom:" + externalUserId;
                    String subject = content.length() > 60 ? content.substring(0, 60) : content;
                    if (subject.isEmpty()) subject = "WeCom message";

                    Map<String, Object> raw = new HashMap<String, Object>();
                    raw.put("externalUserId", externalUserId);
                    raw.put("openKfId", openKfId);
                    raw.put("msgtype", msgType);
                    raw.put("sendTime", sendTime);

                    result.add(new TicketEvent(
                            channel.id(),
                            EventType.NEW_TICKET,
                            externalThreadId,
                            customerIdentifier,
                            "",          // customerName — enriched separately if needed
                            subject,
                            content,
                            occurredAt,
                            raw,
                            Collections.<TicketEvent.InboundAttachment>emptyList()
                    ));
                }
            }

            cursor = resp.getString("next_cursor");
            int hasMoreInt = resp.getIntValue("has_more");
            hasMore = (hasMoreInt == 1) && (cursor != null && !cursor.isEmpty());
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    private void verifySignature(String token, String timestamp, String nonce,
                                  String encrypted, String expectedSig, String channelId) {
        String computed = WecomCrypto.sha1Signature(token, timestamp, nonce, encrypted);
        if (!computed.equalsIgnoreCase(expectedSig)) {
            throw new AutotixException.AuthException(
                    "WeCom webhook: signature mismatch for channel " + channelId +
                    " (expected=" + expectedSig + ", computed=" + computed + ")");
        }
    }

    // -------------------------------------------------------------------------
    // XML parsing
    // -------------------------------------------------------------------------

    /**
     * Extracts the text content of a named element from a simple XML string.
     * Uses Java's built-in XML parser; namespace-agnostic.
     */
    static String extractXmlElement(String xml, String elementName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = doc.getElementsByTagName(elementName);
            if (nodes.getLength() == 0) return null;
            return nodes.item(0).getTextContent();
        } catch (Exception e) {
            log.warn("[WeCom] Failed to parse XML element '{}': {}", elementName, e.getMessage());
            return null;
        }
    }
}
