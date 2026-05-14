package dev.autotix.infrastructure.platform.email;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.ticket.Attachment;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * E2E-B: Sends email replies via SMTP.
 * SMTP credentials are read from channel.credential().attributes():
 *   smtp_host, smtp_port, smtp_user, smtp_password, smtp_use_tls, from_address
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final StorageProvider storageProvider;

    public EmailSender(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    /**
     * Send an HTML reply email for the given ticket.
     *
     * @param channel     email Channel with SMTP credentials
     * @param ticket      the Ticket being replied to
     * @param htmlBody    HTML-formatted reply body
     * @param attachments outbound attachments (may be empty)
     * @return SendResult containing the SMTP Message-ID
     */
    public TicketPlatformPlugin.SendResult send(Channel channel, Ticket ticket,
                                                String htmlBody, List<Attachment> attachments) {
        Map<String, String> attrs = channel.credential().attributes();

        String smtpHost = attrs.getOrDefault("smtp_host", "localhost");
        int smtpPort = parseInt(attrs.getOrDefault("smtp_port", "25"), 25);
        String smtpUser = attrs.get("smtp_user");
        String smtpPassword = attrs.get("smtp_password");
        boolean useTls = "true".equalsIgnoreCase(attrs.getOrDefault("smtp_use_tls", "false"));
        String fromAddress = attrs.getOrDefault("from_address", smtpUser);

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", (smtpUser != null && smtpPassword != null) ? "true" : "false");
        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Authenticator auth = null;
        if (smtpUser != null && smtpPassword != null) {
            final String user = smtpUser;
            final String pass = smtpPassword;
            auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass);
                }
            };
        }

        Session session = Session.getInstance(props, auth);

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromAddress));
            msg.setRecipient(MimeMessage.RecipientType.TO,
                    new InternetAddress(ticket.customerIdentifier()));
            msg.setSubject(buildSubject(ticket.subject()), "UTF-8");
            msg.setSentDate(new Date());

            // Threading headers — use last INBOUND message's externalMessageId if available
            String inReplyTo = ticket.messages().stream()
                    .filter(m -> m.direction() == MessageDirection.INBOUND)
                    .filter(m -> m.externalMessageId() != null)
                    .reduce((first, second) -> second)  // last inbound
                    .map(Message::externalMessageId)
                    .orElse(null);

            if (inReplyTo != null) {
                msg.setHeader("In-Reply-To", "<" + inReplyTo + ">");
                msg.setHeader("References", "<" + inReplyTo + ">");
            }

            if (attachments == null || attachments.isEmpty()) {
                // Simple HTML message
                msg.setContent(htmlBody, "text/html; charset=utf-8");
            } else {
                // Multipart/mixed: HTML + attachments
                MimeMultipart multipart = new MimeMultipart("mixed");

                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(htmlBody, "text/html; charset=utf-8");
                multipart.addBodyPart(htmlPart);

                for (Attachment att : attachments) {
                    try {
                        InputStream is = storageProvider.download(att.key());
                        MimeBodyPart attPart = new MimeBodyPart();
                        attPart.setFileName(att.fileName());
                        attPart.setContent(readAllBytes(is),
                                att.contentType() != null ? att.contentType() : "application/octet-stream");
                        attPart.setDisposition(MimeBodyPart.ATTACHMENT);
                        multipart.addBodyPart(attPart);
                    } catch (Exception e) {
                        log.warn("[EMAIL] Failed to attach file {}: {}", att.fileName(), e.getMessage());
                    }
                }
                msg.setContent(multipart);
            }

            // saveChanges() assigns Message-ID and computes content
            msg.saveChanges();

            Transport.send(msg);

            String messageId = EmailWebhookParser.stripAngleBrackets(msg.getMessageID());
            log.debug("[EMAIL] sent reply for ticket={} messageId={}", ticket.id(), messageId);
            return new TicketPlatformPlugin.SendResult(messageId);

        } catch (MessagingException e) {
            throw new AutotixException.IntegrationException("email",
                    "SMTP send failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String buildSubject(String originalSubject) {
        if (originalSubject == null) {
            return "Re:";
        }
        if (originalSubject.toLowerCase().startsWith("re:")) {
            return originalSubject;
        }
        return "Re: " + originalSubject;
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static byte[] readAllBytes(InputStream is) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }
}
