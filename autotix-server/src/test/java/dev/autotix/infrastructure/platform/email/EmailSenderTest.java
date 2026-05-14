package dev.autotix.infrastructure.platform.email;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.MessageVisibility;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-B: EmailSender tests using GreenMail to intercept sent emails.
 */
@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("agent@autotix.local", "secret"))
            .withPerMethodLifecycle(true);

    @Mock
    private StorageProvider storageProvider;

    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        emailSender = new EmailSender(storageProvider);
    }

    private Channel buildSmtpChannel() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("smtp_host", "localhost");
        attrs.put("smtp_port", String.valueOf(ServerSetupTest.SMTP.getPort()));
        attrs.put("smtp_user", "agent@autotix.local");
        attrs.put("smtp_password", "secret");
        attrs.put("smtp_use_tls", "false");
        attrs.put("from_address", "agent@autotix.local");
        return Channel.rehydrate(
                new ChannelId("ch-email-1"),
                PlatformType.EMAIL,
                ChannelType.EMAIL,
                "Test Email",
                "token",
                new ChannelCredential(null, null, null, attrs),
                true, false,
                Instant.now(), Instant.now());
    }

    private Ticket buildTicket(String customerEmail, String subject) {
        Message firstMsg = new Message(
                MessageDirection.INBOUND, "customer", "Initial message", Instant.now());
        return Ticket.openFromInbound(
                new ChannelId("ch-email-1"),
                "ext-thread-1",
                subject,
                customerEmail,
                firstMsg);
    }

    @Test
    void sendsEmailAndReturnsNonNullMessageId() throws Exception {
        Channel channel = buildSmtpChannel();
        Ticket ticket = buildTicket("customer@example.com", "Support Request");

        TicketPlatformPlugin.SendResult result = emailSender.send(
                channel, ticket, "<p>Hello, we received your request.</p>", Collections.emptyList());

        // Assert send result
        assertNotNull(result, "SendResult must not be null");

        // Assert GreenMail received the message
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length, "Expected exactly 1 sent message");

        MimeMessage sent = received[0];
        assertEquals("Re: Support Request", sent.getSubject());
        assertTrue(sent.getAllRecipients()[0].toString().contains("customer@example.com"));
    }

    @Test
    void sendsWithReSubjectPrefixAlreadyPresent() throws Exception {
        Channel channel = buildSmtpChannel();
        Ticket ticket = buildTicket("customer@example.com", "Re: Existing Thread");

        emailSender.send(channel, ticket, "<p>Yes.</p>", Collections.emptyList());

        MimeMessage[] received = greenMail.getReceivedMessages();
        // Filter to messages for this test — greenMail accumulates across tests when withPerMethodLifecycle(false)
        MimeMessage last = received[received.length - 1];
        assertEquals("Re: Existing Thread", last.getSubject(),
                "Should not double-prefix 'Re:'");
    }

    @Test
    void setsInReplyToHeaderWhenInboundMessageExists() throws Exception {
        Channel channel = buildSmtpChannel();

        // Build a ticket that already has an inbound message with a known Message-ID
        Ticket ticket = buildTicket("customer@example.com", "Thread Subject");
        ticket.appendInbound(new Message(
                MessageDirection.INBOUND,
                "customer",
                "Original question",
                Instant.now().minusSeconds(60),
                MessageVisibility.PUBLIC,
                "original-msg-id@mail.example.com"));

        emailSender.send(channel, ticket, "<p>Reply.</p>", Collections.emptyList());

        MimeMessage[] received = greenMail.getReceivedMessages();
        MimeMessage last = received[received.length - 1];
        String[] inReplyToHeaders = last.getHeader("In-Reply-To");
        assertNotNull(inReplyToHeaders, "In-Reply-To header should be set");
        assertTrue(inReplyToHeaders[0].contains("original-msg-id@mail.example.com"),
                "In-Reply-To should reference the original message ID");
    }

    @Test
    void returnsMessageId() throws Exception {
        Channel channel = buildSmtpChannel();
        Ticket ticket = buildTicket("customer@example.com", "MessageId Test");

        TicketPlatformPlugin.SendResult result = emailSender.send(
                channel, ticket, "<p>Test</p>", Collections.emptyList());

        assertNotNull(result.externalMessageId, "externalMessageId should be non-null after send");
        assertFalse(result.externalMessageId.isEmpty(), "externalMessageId should not be empty");
    }
}
