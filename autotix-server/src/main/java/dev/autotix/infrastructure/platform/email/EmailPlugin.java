package dev.autotix.infrastructure.platform.email;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Attachment;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * E2E-B: TicketPlatformPlugin implementation for the EMAIL platform.
 * Inbound is handled by IMAP polling (EmailInboxPoller), not webhooks.
 * Outbound sends via SMTP through EmailSender.
 */
@Component
public class EmailPlugin implements TicketPlatformPlugin {

    private static final Logger log = LoggerFactory.getLogger(EmailPlugin.class);

    private final EmailSender emailSender;

    public EmailPlugin(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public PlatformType platform() {
        return PlatformType.EMAIL;
    }

    @Override
    public ChannelType defaultChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        throw new UnsupportedOperationException("Email uses IMAP poll, not webhook");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        sendReplyDetailed(channel, ticket, formattedReply);
    }

    @Override
    public SendResult sendReplyDetailed(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: outbound attachments via SMTP multipart — not yet wired
        List<Attachment> noAttachments = Collections.emptyList();
        return emailSender.send(channel, ticket, formattedReply, noAttachments);
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        log.info("[EMAIL] close() called for ticket={} — no-op (email channel has no server-side close)",
                ticket.id() != null ? ticket.id().value() : "null");
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        Map<String, String> attrs = credential.attributes();
        boolean smtpOk = checkSmtp(attrs);
        boolean imapOk = checkImap(attrs);
        return smtpOk && imapOk;
    }

    // -----------------------------------------------------------------------
    // Health check helpers
    // -----------------------------------------------------------------------

    private boolean checkSmtp(Map<String, String> attrs) {
        try {
            String host = attrs.getOrDefault("smtp_host", "localhost");
            int port = parseIntAttr(attrs, "smtp_port", 25);
            String user = attrs.get("smtp_user");
            String pass = attrs.get("smtp_password");
            boolean useTls = "true".equalsIgnoreCase(attrs.getOrDefault("smtp_use_tls", "false"));

            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            if (useTls) {
                props.put("mail.smtp.starttls.enable", "true");
            }

            javax.mail.Authenticator auth = null;
            if (user != null && pass != null) {
                final String u = user;
                final String p = pass;
                props.put("mail.smtp.auth", "true");
                auth = new javax.mail.Authenticator() {
                    @Override
                    protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                        return new javax.mail.PasswordAuthentication(u, p);
                    }
                };
            }

            Session session = Session.getInstance(props, auth);
            Transport transport = session.getTransport("smtp");
            if (user != null && pass != null) {
                transport.connect(host, port, user, pass);
            } else {
                transport.connect();
            }
            transport.close();
            log.debug("[EMAIL] SMTP health check OK for {}", host);
            return true;
        } catch (Exception e) {
            log.warn("[EMAIL] SMTP health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkImap(Map<String, String> attrs) {
        try {
            String host = attrs.getOrDefault("imap_host", "localhost");
            int port = parseIntAttr(attrs, "imap_port", 143);
            String user = attrs.get("imap_user");
            String pass = attrs.get("imap_password");
            boolean useSsl = "true".equalsIgnoreCase(attrs.getOrDefault("imap_use_ssl", "false"));

            Properties props = new Properties();
            String protocol = useSsl ? "imaps" : "imap";
            props.put("mail." + protocol + ".host", host);
            props.put("mail." + protocol + ".port", String.valueOf(port));
            props.put("mail." + protocol + ".connectiontimeout", "5000");
            props.put("mail." + protocol + ".timeout", "5000");

            Session session = Session.getInstance(props);
            Store store = session.getStore(protocol);
            store.connect(host, port, user, pass);
            store.close();
            log.debug("[EMAIL] IMAP health check OK for {}", host);
            return true;
        } catch (Exception e) {
            log.warn("[EMAIL] IMAP health check failed: {}", e.getMessage());
            return false;
        }
    }

    private static int parseIntAttr(Map<String, String> attrs, String key, int defaultValue) {
        try {
            return Integer.parseInt(attrs.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
