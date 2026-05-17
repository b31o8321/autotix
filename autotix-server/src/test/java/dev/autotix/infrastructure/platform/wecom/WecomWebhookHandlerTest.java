package dev.autotix.infrastructure.platform.wecom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WecomWebhookHandler unit tests.
 * Real crypto is used for GET/POST round-trip tests; WecomClient is mocked for syncMsg.
 */
class WecomWebhookHandlerTest {

    private static final String CORP_ID     = "wwCORPIDtest1234";
    private static final String TOKEN       = "test_callback_token";
    private static final String AES_KEY_43  = buildAesKey43();

    private WecomClient mockClient;
    private WecomWebhookHandler handler;
    private Channel channel;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(WecomClient.class);
        handler = new WecomWebhookHandler(mockClient);
        channel = buildChannel();
    }

    // -----------------------------------------------------------------------
    // handleGet: valid echostr round-trip
    // -----------------------------------------------------------------------

    @Test
    void handleGet_validSignature_returnsDecryptedEchostr() {
        String echostrPlain = "HelloEchoStringFromWeCom";
        String encryptedEchostr = WecomCrypto.encrypt(AES_KEY_43, CORP_ID, echostrPlain);

        String timestamp = "1700000000";
        String nonce     = "nonce_abc";
        String sig = WecomCrypto.sha1Signature(TOKEN, timestamp, nonce, encryptedEchostr);

        String result = handler.handleGet(channel, sig, timestamp, nonce, encryptedEchostr);
        assertEquals(echostrPlain, result);
    }

    @Test
    void handleGet_invalidSignature_throwsAuthException() {
        String encrypted = WecomCrypto.encrypt(AES_KEY_43, CORP_ID, "echo");
        assertThrows(AutotixException.AuthException.class,
                () -> handler.handleGet(channel, "WRONG_SIG", "ts", "nc", encrypted));
    }

    // -----------------------------------------------------------------------
    // handlePost: signature mismatch
    // -----------------------------------------------------------------------

    @Test
    void handlePost_signatureMismatch_throwsAuthException() {
        String xml = "<xml><Encrypt><![CDATA[anything]]></Encrypt></xml>";
        assertThrows(AutotixException.AuthException.class,
                () -> handler.handlePost(channel, "BAD_SIG", "ts", "nc", xml));
    }

    // -----------------------------------------------------------------------
    // handlePost: valid encrypted body → pulls messages via syncMsg
    // -----------------------------------------------------------------------

    @Test
    void handlePost_validBody_returnsTextMessages() {
        // Build the inner notification XML (what WeCom sends inside <Encrypt>)
        String openKfId = "wk_kfid_12345";
        String voucher   = "VOUCHER_XYZ";
        String innerXml = "<xml><Event>kf_msg_or_event</Event>" +
                "<Token><![CDATA[" + voucher + "]]></Token>" +
                "<OpenKfId><![CDATA[" + openKfId + "]]></OpenKfId></xml>";

        String encryptedInner = WecomCrypto.encrypt(AES_KEY_43, CORP_ID, innerXml);
        String timestamp = "1700000001";
        String nonce     = "nonce_xyz";
        String sig = WecomCrypto.sha1Signature(TOKEN, timestamp, nonce, encryptedInner);
        String xmlBody = "<xml><Encrypt><![CDATA[" + encryptedInner + "]]></Encrypt></xml>";

        // Mock: getAccessToken → "ACCESS_TOKEN"
        when(mockClient.getAccessToken(CORP_ID, "test_secret")).thenReturn("ACCESS_TOKEN");

        // Mock: syncMsg → one text message, no more pages
        JSONObject syncResp = new JSONObject();
        syncResp.put("errcode", 0);
        syncResp.put("errmsg", "ok");
        syncResp.put("next_cursor", "");
        syncResp.put("has_more", 0);
        JSONArray msgList = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("msgtype", "text");
        msg.put("external_userid", "ext_user_001");
        msg.put("send_time", 1700000001L);
        JSONObject textNode = new JSONObject();
        textNode.put("content", "Hello from WeCom customer!");
        msg.put("text", textNode);
        msgList.add(msg);
        syncResp.put("msg_list", msgList);

        when(mockClient.syncMsg(eq("ACCESS_TOKEN"), eq(voucher), eq(openKfId), any(), eq(1000)))
                .thenReturn(syncResp);

        List<TicketEvent> events = handler.handlePost(channel, sig, timestamp, nonce, xmlBody);

        assertFalse(events.isEmpty(), "Should have at least one event");
        TicketEvent event = events.get(0);
        assertEquals(EventType.NEW_TICKET, event.type());
        assertEquals("ext_user_001@" + openKfId, event.externalTicketId());
        assertEquals("wecom:ext_user_001", event.customerIdentifier());
        assertEquals("Hello from WeCom customer!", event.messageBody());
    }

    @Test
    void handlePost_nonTextMessages_areIgnored() {
        String innerXml = "<xml><Token><![CDATA[VOUCHER]]></Token>" +
                "<OpenKfId><![CDATA[wk_kfid]]></OpenKfId></xml>";
        String encrypted = WecomCrypto.encrypt(AES_KEY_43, CORP_ID, innerXml);
        String ts = "1700000002";
        String nc = "nonce2";
        String sig = WecomCrypto.sha1Signature(TOKEN, ts, nc, encrypted);
        String xmlBody = "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>";

        when(mockClient.getAccessToken(CORP_ID, "test_secret")).thenReturn("AT");

        JSONObject syncResp = new JSONObject();
        syncResp.put("errcode", 0);
        syncResp.put("errmsg", "ok");
        syncResp.put("next_cursor", "");
        syncResp.put("has_more", 0);
        JSONArray msgList = new JSONArray();
        // Add a voice message — should be ignored
        JSONObject voiceMsg = new JSONObject();
        voiceMsg.put("msgtype", "voice");
        voiceMsg.put("external_userid", "ext_user_002");
        msgList.add(voiceMsg);
        syncResp.put("msg_list", msgList);

        when(mockClient.syncMsg(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(syncResp);

        List<TicketEvent> events = handler.handlePost(channel, sig, ts, nc, xmlBody);
        assertTrue(events.isEmpty(), "Voice messages should be ignored");
    }

    // -----------------------------------------------------------------------
    // extractXmlElement helper
    // -----------------------------------------------------------------------

    @Test
    void extractXmlElement_parsesSimpleElement() {
        String xml = "<xml><Encrypt><![CDATA[ABC123]]></Encrypt><MsgType>event</MsgType></xml>";
        assertEquals("ABC123", WecomWebhookHandler.extractXmlElement(xml, "Encrypt"));
        assertEquals("event", WecomWebhookHandler.extractXmlElement(xml, "MsgType"));
    }

    @Test
    void extractXmlElement_missingElement_returnsNull() {
        String xml = "<xml><Foo>bar</Foo></xml>";
        assertNull(WecomWebhookHandler.extractXmlElement(xml, "Encrypt"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Channel buildChannel() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("corpid",           CORP_ID);
        attrs.put("secret",           "test_secret");
        attrs.put("token",            TOKEN);
        attrs.put("encoding_aes_key", AES_KEY_43);
        attrs.put("open_kfid",        "wk_open_kfid_default");
        ChannelCredential cred = new ChannelCredential(null, null, null, attrs);
        return Channel.rehydrate(
                new ChannelId("ch-wecom-1"),
                PlatformType.WECOM,
                ChannelType.CHAT,
                "Test WeCom Channel",
                "webhook_token_abc",
                cred,
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    private static String buildAesKey43() {
        // Generate a stable 43-char base64 AES key for tests
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key).substring(0, 43);
    }
}
