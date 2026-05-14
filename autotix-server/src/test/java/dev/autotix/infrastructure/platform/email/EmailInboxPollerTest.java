package dev.autotix.infrastructure.platform.email;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import dev.autotix.application.ticket.ProcessWebhookUseCase;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.MessageVisibility;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.infra.lock.LockProvider;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2E-B: EmailInboxPoller tests using GreenMail to simulate an IMAP inbox.
 */
@ExtendWith(MockitoExtension.class)
class EmailInboxPollerTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("agent@autotix.local", "secret")
                    .withUser("customer@example.com", "secret"))
            .withPerMethodLifecycle(true);

    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ProcessWebhookUseCase processWebhookUseCase;
    @Mock
    private LockProvider lockProvider;
    @Mock
    private LockProvider.LockHandle lockHandle;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private StorageProvider storageProvider;

    private EmailInboxPoller poller;
    private Channel emailChannel;

    @BeforeEach
    void setUp() {
        EmailWebhookParser parser = new EmailWebhookParser(storageProvider);
        poller = new EmailInboxPoller(
                channelRepository, parser, processWebhookUseCase,
                lockProvider, ticketRepository, storageProvider);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("imap_host", "localhost");
        attrs.put("imap_port", String.valueOf(ServerSetupTest.IMAP.getPort()));
        attrs.put("imap_user", "agent@autotix.local");
        attrs.put("imap_password", "secret");
        attrs.put("imap_use_ssl", "false");

        emailChannel = Channel.rehydrate(
                new ChannelId("ch-imap-1"),
                PlatformType.EMAIL,
                ChannelType.EMAIL,
                "Test IMAP",
                "token",
                new ChannelCredential(null, null, null, attrs),
                true, false,
                Instant.now(), Instant.now());

        when(channelRepository.findAll()).thenReturn(Collections.singletonList(emailChannel));
        when(lockProvider.tryAcquire(anyString(), any())).thenReturn(lockHandle);
    }

    /**
     * Delivers a message to the agent's IMAP inbox via SMTP.
     */
    private MimeMessage deliverToAgentInbox(String from, String subject, String body) throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new java.util.Properties()));
        msg.setFrom(new InternetAddress(from));
        msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("agent@autotix.local"));
        msg.setSubject(subject);
        msg.setText(body);
        msg.saveChanges();

        GreenMailUser agentUser = greenMail.getUserManager().getUser("agent@autotix.local");
        agentUser.deliver(msg);
        return msg;
    }

    @Test
    void newThreadCreatesTicketEventWithCorrectFields() throws Exception {
        deliverToAgentInbox("customer@example.com", "New Support Request", "I need help.");

        poller.pollAll();

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(processWebhookUseCase, times(1)).handle(eq(emailChannel), eventCaptor.capture());

        TicketEvent event = eventCaptor.getValue();
        assertEquals("customer@example.com", event.customerIdentifier());
        assertEquals("New Support Request", event.subject());
        assertTrue(event.messageBody().contains("I need help."),
                "Body should contain message text. Got: " + event.messageBody());
    }

    @Test
    void threadedReplySetsExternalTicketIdToExistingTicket() throws Exception {
        // Pre-seed: there's already a ticket with a known email message ID
        String existingMessageId = "original-12345@mail.example.com";
        TicketId existingTicketId = new TicketId("42");

        Message seedMsg = new Message(
                MessageDirection.INBOUND, "customer", "Original question",
                Instant.now().minusSeconds(120));
        Ticket existingTicket = Ticket.openFromInbound(
                new ChannelId("ch-imap-1"),
                "original-thread-id",
                "Original Subject",
                "customer@example.com",
                seedMsg);
        // Assign a persisted ID so externalNativeId is accessible
        existingTicket.assignPersistedId(existingTicketId);

        when(ticketRepository.findTicketIdByEmailMessageId(existingMessageId))
                .thenReturn(existingTicketId);
        when(ticketRepository.findById(existingTicketId))
                .thenReturn(Optional.of(existingTicket));

        // Deliver a reply with In-Reply-To header
        MimeMessage reply = new MimeMessage(Session.getInstance(new java.util.Properties()));
        reply.setFrom(new InternetAddress("customer@example.com"));
        reply.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("agent@autotix.local"));
        reply.setSubject("Re: Original Subject");
        reply.setText("This is a follow-up.");
        reply.setHeader("In-Reply-To", "<" + existingMessageId + ">");
        reply.saveChanges();

        GreenMailUser agentUser = greenMail.getUserManager().getUser("agent@autotix.local");
        agentUser.deliver(reply);

        poller.pollAll();

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(processWebhookUseCase, times(1)).handle(eq(emailChannel), eventCaptor.capture());

        TicketEvent event = eventCaptor.getValue();
        // externalTicketId should match the existing ticket's externalNativeId
        assertEquals("original-thread-id", event.externalTicketId(),
                "Threaded reply should route to existing ticket's externalNativeId");
    }

    @Test
    void skipsChannelIfLockNotAcquired() throws Exception {
        when(lockProvider.tryAcquire(anyString(), any())).thenReturn(null);

        deliverToAgentInbox("customer@example.com", "Skipped", "Lock not available.");

        poller.pollAll();

        verify(processWebhookUseCase, never()).handle(any(), any());
    }
}
