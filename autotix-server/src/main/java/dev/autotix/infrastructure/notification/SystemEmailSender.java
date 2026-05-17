package dev.autotix.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Sends plain-text notification emails via the system SMTP config
 * (autotix.notification.email.*), independent of any Channel credential.
 *
 * If host is blank, the send is skipped with a warning — no exception thrown.
 */
@Component
public class SystemEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SystemEmailSender.class);

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String from;
    private final boolean useTls;

    public SystemEmailSender(
            @Value("${autotix.notification.email.host:}") String host,
            @Value("${autotix.notification.email.port:25}") int port,
            @Value("${autotix.notification.email.user:}") String user,
            @Value("${autotix.notification.email.password:}") String password,
            @Value("${autotix.notification.email.from:alerts@autotix.local}") String from,
            @Value("${autotix.notification.email.use-tls:false}") boolean useTls) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.from = from;
        this.useTls = useTls;
    }

    /**
     * Send a notification email.
     *
     * @param to      recipient addresses
     * @param subject email subject
     * @param body    plain-text body
     * @return true if sent, false if skipped (no SMTP host configured)
     */
    public boolean send(List<String> to, String subject, String body) {
        if (host == null || host.isEmpty()) {
            log.warn("[SystemEmail] SMTP host not configured (autotix.notification.email.host); skipping notification email");
            return false;
        }
        if (to == null || to.isEmpty()) {
            log.warn("[SystemEmail] No recipients specified; skipping notification email");
            return false;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        boolean hasAuth = user != null && !user.isEmpty() && password != null && !password.isEmpty();
        props.put("mail.smtp.auth", hasAuth ? "true" : "false");
        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Authenticator auth = null;
        if (hasAuth) {
            final String u = user;
            final String p = password;
            auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(u, p);
                }
            };
        }

        Session session = Session.getInstance(props, auth);

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from));
            for (String recipient : to) {
                msg.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient.trim()));
            }
            msg.setSubject(subject, "UTF-8");
            msg.setSentDate(new Date());
            msg.setContent(body, "text/plain; charset=utf-8");
            msg.saveChanges();
            Transport.send(msg);
            log.info("[SystemEmail] Notification sent to {} recipients, subject: {}", to.size(), subject);
            return true;
        } catch (MessagingException e) {
            log.error("[SystemEmail] Failed to send notification email: {}", e.getMessage(), e);
            return false;
        }
    }
}
