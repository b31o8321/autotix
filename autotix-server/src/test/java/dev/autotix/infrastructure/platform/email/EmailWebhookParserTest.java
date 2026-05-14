package dev.autotix.infrastructure.platform.email;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailWebhookParser — builds MimeMessages in-memory (no GreenMail needed).
 */
@ExtendWith(MockitoExtension.class)
class EmailWebhookParserTest {

    @Mock
    private StorageProvider storageProvider;

    private EmailWebhookParser parser;
    private Channel channel;
    private Session mailSession;

    @BeforeEach
    void setUp() {
        parser = new EmailWebhookParser(storageProvider);
        channel = Channel.rehydrate(
                new ChannelId("ch-1"),
                PlatformType.EMAIL,
                ChannelType.EMAIL,
                "Test Email Channel",
                "token-abc",
                new ChannelCredential(null, null, null, Collections.emptyMap()),
                true, false,
                Instant.now(), Instant.now());
        mailSession = Session.getInstance(new Properties());
    }

    @Test
    void parsesSimplePlainTextEmail() throws Exception {
        MimeMessage msg = new MimeMessage(mailSession);
        msg.setFrom(new InternetAddress("customer@example.com", "John Doe"));
        msg.setSubject("Test Subject");
        msg.setText("Hello, this is a test email.");
        msg.saveChanges();

        TicketEvent event = parser.parse(channel, msg, "ext-ticket-1");

        assertEquals("ext-ticket-1", event.externalTicketId());
        assertEquals("customer@example.com", event.customerIdentifier());
        assertEquals("John Doe", event.customerName());
        assertEquals("Test Subject", event.subject());
        assertTrue(event.messageBody().contains("Hello, this is a test email."));
        assertTrue(event.attachments().isEmpty());
    }

    @Test
    void parsesEmailWithNoSubject() throws Exception {
        MimeMessage msg = new MimeMessage(mailSession);
        msg.setFrom(new InternetAddress("anon@example.com"));
        msg.setText("Some body text");
        msg.saveChanges();

        TicketEvent event = parser.parse(channel, msg, "ext-ticket-2");

        assertEquals("(no subject)", event.subject());
    }

    @Test
    void parsesEmailWithNoFromAddress() throws Exception {
        MimeMessage msg = new MimeMessage(mailSession);
        msg.setSubject("No From");
        msg.setText("Body");
        msg.saveChanges();

        TicketEvent event = parser.parse(channel, msg, "ext-ticket-3");

        assertEquals("unknown@unknown.invalid", event.customerIdentifier());
    }

    @Test
    void stripAngleBracketsRemovesAngleBrackets() {
        assertEquals("abc@example.com", EmailWebhookParser.stripAngleBrackets("<abc@example.com>"));
        assertEquals("abc@example.com", EmailWebhookParser.stripAngleBrackets("abc@example.com"));
        assertNull(EmailWebhookParser.stripAngleBrackets(null));
    }

    @Test
    void rawMapContainsEmailMetadata() throws Exception {
        MimeMessage msg = new MimeMessage(mailSession);
        msg.setFrom(new InternetAddress("sender@example.com"));
        msg.setSubject("Metadata test");
        msg.setText("Body");
        msg.saveChanges();

        TicketEvent event = parser.parse(channel, msg, "ext-ticket-4");

        assertTrue(event.raw().containsKey("subject"));
        assertTrue(event.raw().containsKey("from"));
        assertTrue(event.raw().containsKey("messageId"));
    }
}
