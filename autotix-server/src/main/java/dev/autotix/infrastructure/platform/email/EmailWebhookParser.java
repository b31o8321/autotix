package dev.autotix.infrastructure.platform.email;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E2E-B: Converts a {@link MimeMessage} into a {@link TicketEvent}.
 *
 * <p>Threading: if the message has an In-Reply-To header pointing to a known Message-ID
 * (one previously stored in ticket_message.email_message_id), the caller (EmailInboxPoller)
 * handles that correlation and passes in the externalTicketId to use.
 *
 * <p>Attachment handling: all MIME parts with disposition ATTACHMENT or inline binary are
 * uploaded to StorageProvider before the TicketEvent is emitted.
 */
@Component
public class EmailWebhookParser {

    private static final Logger log = LoggerFactory.getLogger(EmailWebhookParser.class);

    private final StorageProvider storageProvider;

    public EmailWebhookParser(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    /**
     * Parse a MimeMessage into a TicketEvent.
     *
     * @param channel         the email Channel
     * @param mimeMessage     the incoming email
     * @param externalTicketId pre-computed externalTicketId (based on threading; caller responsible)
     */
    public TicketEvent parse(Channel channel, MimeMessage mimeMessage, String externalTicketId)
            throws MessagingException, IOException {

        // From address → customer identifier
        String customerIdentifier = extractFrom(mimeMessage);
        String customerName = extractFromName(mimeMessage);

        // Subject
        String subject = mimeMessage.getSubject();
        if (subject == null) {
            subject = "(no subject)";
        }

        // Own Message-ID (stripped of angle brackets)
        String rawMessageId = mimeMessage.getMessageID();
        String ownMessageId = stripAngleBrackets(rawMessageId);

        // Date
        Date sentDate = mimeMessage.getSentDate();
        Instant occurredAt = sentDate != null ? sentDate.toInstant() : Instant.now();

        // Body + attachments
        List<TicketEvent.InboundAttachment> attachments = new ArrayList<>();
        String body = extractBodyAndAttachments(mimeMessage, attachments);
        if (body == null || body.trim().isEmpty()) {
            body = "(no content)";
        }

        // Raw map for audit
        Map<String, Object> raw = new HashMap<>();
        raw.put("messageId", ownMessageId);
        raw.put("subject", subject);
        raw.put("from", customerIdentifier);
        raw.put("inReplyTo", extractHeader(mimeMessage, "In-Reply-To"));
        raw.put("references", extractHeader(mimeMessage, "References"));

        // The ownMessageId is stored via the event's raw map; the poller stores it on the Message row
        // by using a special externalTicketId convention. But we embed it in raw for the plugin to read.
        raw.put("ownEmailMessageId", ownMessageId);

        return new TicketEvent(
                channel.id(),
                EventType.NEW_MESSAGE,
                externalTicketId,
                customerIdentifier,
                customerName,
                subject,
                body,
                occurredAt,
                raw,
                attachments);
    }

    // -----------------------------------------------------------------------
    // MIME parsing helpers
    // -----------------------------------------------------------------------

    private String extractFrom(MimeMessage msg) throws MessagingException {
        Address[] froms = msg.getFrom();
        if (froms == null || froms.length == 0) {
            return "unknown@unknown.invalid";
        }
        Address addr = froms[0];
        if (addr instanceof InternetAddress) {
            String email = ((InternetAddress) addr).getAddress();
            return email != null ? email : addr.toString();
        }
        return addr.toString();
    }

    private String extractFromName(MimeMessage msg) throws MessagingException {
        Address[] froms = msg.getFrom();
        if (froms == null || froms.length == 0) {
            return null;
        }
        Address addr = froms[0];
        if (addr instanceof InternetAddress) {
            String personal = ((InternetAddress) addr).getPersonal();
            return personal != null && !personal.trim().isEmpty() ? personal : null;
        }
        return null;
    }

    /**
     * Extract text body, upload binary attachments.
     */
    private String extractBodyAndAttachments(Part part,
                                              List<TicketEvent.InboundAttachment> attachments)
            throws MessagingException, IOException {

        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }

        if (part.isMimeType("text/html")) {
            // Prefer plain text; if only HTML, strip tags naively
            String html = (String) part.getContent();
            return stripHtml(html);
        }

        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                String disposition = bp.getDisposition();
                if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
                    uploadAttachment(bp, attachments);
                } else {
                    String sub = extractBodyAndAttachments(bp, attachments);
                    if (sub != null && !sub.trim().isEmpty()) {
                        if (text.length() > 0) {
                            text.append("\n");
                        }
                        text.append(sub);
                    }
                }
            }
            return text.toString();
        }

        // Binary non-text part without explicit disposition — treat as attachment
        String fileName = part.getFileName();
        if (fileName != null) {
            uploadAttachment(part, attachments);
        }
        return null;
    }

    private void uploadAttachment(Part part, List<TicketEvent.InboundAttachment> attachments) {
        try {
            String fileName = part.getFileName();
            if (fileName == null) {
                fileName = "attachment";
            }
            String contentType = part.getContentType();
            if (contentType != null && contentType.contains(";")) {
                contentType = contentType.substring(0, contentType.indexOf(';')).trim();
            }
            String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            Date now = new Date();
            String year = String.format("%tY", now);
            String month = String.format("%tm", now);
            String key = "attachments/" + year + "/" + month + "/inbound/"
                    + UUID.randomUUID().toString() + "-" + safeFileName;

            InputStream is = part.getInputStream();
            byte[] bytes = readAllBytes(is);
            storageProvider.upload(key, new ByteArrayInputStream(bytes), bytes.length,
                    contentType != null ? contentType : "application/octet-stream");
            attachments.add(new TicketEvent.InboundAttachment(
                    fileName, contentType, bytes.length, key, "customer"));
            log.debug("[EMAIL] stored inbound attachment {} -> {}", fileName, key);
        } catch (Exception e) {
            log.warn("[EMAIL] failed to store inbound attachment: {}", e.getMessage());
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    static String stripAngleBrackets(String messageId) {
        if (messageId == null) {
            return null;
        }
        String s = messageId.trim();
        if (s.startsWith("<") && s.endsWith(">")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private String extractHeader(MimeMessage msg, String name) {
        try {
            String[] vals = msg.getHeader(name);
            if (vals != null && vals.length > 0) {
                return vals[0];
            }
        } catch (MessagingException e) {
            // ignore
        }
        return null;
    }

    private static String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
    }
}
