package dev.autotix.infrastructure.platform.email;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.application.ticket.ProcessWebhookUseCase;
import dev.autotix.infrastructure.infra.lock.LockProvider;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * E2E-B: Polls IMAP inboxes for all enabled EMAIL channels and processes new messages
 * as TicketEvents via ProcessWebhookUseCase.
 *
 * Threading: In-Reply-To header is matched against known email_message_id values to
 * route replies to the correct existing ticket.
 */
@Component
public class EmailInboxPoller {

    private static final Logger log = LoggerFactory.getLogger(EmailInboxPoller.class);

    private final ChannelRepository channelRepository;
    private final EmailWebhookParser emailWebhookParser;
    private final ProcessWebhookUseCase processWebhookUseCase;
    private final LockProvider lockProvider;
    private final TicketRepository ticketRepository;
    private final StorageProvider storageProvider;

    public EmailInboxPoller(ChannelRepository channelRepository,
                            EmailWebhookParser emailWebhookParser,
                            ProcessWebhookUseCase processWebhookUseCase,
                            LockProvider lockProvider,
                            TicketRepository ticketRepository,
                            StorageProvider storageProvider) {
        this.channelRepository = channelRepository;
        this.emailWebhookParser = emailWebhookParser;
        this.processWebhookUseCase = processWebhookUseCase;
        this.lockProvider = lockProvider;
        this.ticketRepository = ticketRepository;
        this.storageProvider = storageProvider;
    }

    @Scheduled(fixedDelayString = "${autotix.email.poll-interval-ms:60000}")
    public void scheduledPoll() {
        pollAll();
    }

    /**
     * Package-private so tests can call directly without the scheduler.
     */
    void pollAll() {
        List<Channel> channels = channelRepository.findAll();
        for (Channel channel : channels) {
            if (channel.platform() != PlatformType.EMAIL || !channel.isEnabled()) {
                continue;
            }
            String lockKey = "email-poll:" + channel.id().value();
            LockProvider.LockHandle lock = lockProvider.tryAcquire(lockKey, Duration.ofMinutes(5));
            if (lock == null) {
                log.debug("[EMAIL] Skipping poll for channel={} — lock not acquired", channel.id().value());
                continue;
            }
            try {
                pollOne(channel);
            } catch (Exception e) {
                log.error("[EMAIL] Error polling channel={}: {}", channel.id().value(), e.getMessage(), e);
            } finally {
                lock.close();
            }
        }
    }

    private void pollOne(Channel channel) {
        Map<String, String> attrs = channel.credential().attributes();
        String host = attrs.getOrDefault("imap_host", "localhost");
        int port = parseIntAttr(attrs, "imap_port", 143);
        String user = attrs.get("imap_user");
        String password = attrs.get("imap_password");
        boolean useSsl = "true".equalsIgnoreCase(attrs.getOrDefault("imap_use_ssl", "false"));

        String protocol = useSsl ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail." + protocol + ".host", host);
        props.put("mail." + protocol + ".port", String.valueOf(port));
        props.put("mail." + protocol + ".connectiontimeout", "15000");
        props.put("mail." + protocol + ".timeout", "15000");

        Store store = null;
        Folder folder = null;
        try {
            Session session = Session.getInstance(props);
            store = session.getStore(protocol);
            store.connect(host, port, user, password);

            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);

            Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            log.debug("[EMAIL] channel={} found {} unseen messages", channel.id().value(), messages.length);

            for (Message message : messages) {
                try {
                    processMessage(channel, (MimeMessage) message);
                } catch (Exception e) {
                    log.error("[EMAIL] Failed to process message in channel={}: {}",
                            channel.id().value(), e.getMessage(), e);
                }
            }

        } catch (MessagingException e) {
            log.error("[EMAIL] IMAP connection error for channel={}: {}", channel.id().value(), e.getMessage(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private void processMessage(Channel channel, MimeMessage mimeMessage) throws Exception {
        // Determine externalTicketId via threading (In-Reply-To header)
        String externalTicketId = resolveExternalTicketId(channel, mimeMessage);

        TicketEvent event = emailWebhookParser.parse(channel, mimeMessage, externalTicketId);

        processWebhookUseCase.handle(channel, event);

        mimeMessage.setFlag(Flags.Flag.SEEN, true);
        log.debug("[EMAIL] processed message externalTicketId={} subject={}",
                externalTicketId, mimeMessage.getSubject());
    }

    /**
     * If the message has an In-Reply-To header matching a known email Message-ID,
     * return the externalNativeId of that ticket (for threading).
     * Otherwise use the message's own Message-ID as the externalTicketId (new thread).
     */
    private String resolveExternalTicketId(Channel channel, MimeMessage mimeMessage)
            throws MessagingException {
        String[] inReplyToHeaders = mimeMessage.getHeader("In-Reply-To");
        if (inReplyToHeaders != null && inReplyToHeaders.length > 0) {
            String inReplyTo = EmailWebhookParser.stripAngleBrackets(inReplyToHeaders[0].trim());
            if (inReplyTo != null && !inReplyTo.isEmpty()) {
                TicketId existingTicketId = ticketRepository.findTicketIdByEmailMessageId(inReplyTo);
                if (existingTicketId != null) {
                    // Look up the ticket to get its externalNativeId
                    Optional<Ticket> ticketOpt = ticketRepository.findById(existingTicketId);
                    if (ticketOpt.isPresent()) {
                        String externalNativeId = ticketOpt.get().externalNativeId();
                        log.debug("[EMAIL] threading reply to existing ticket={} via inReplyTo={}",
                                existingTicketId.value(), inReplyTo);
                        return externalNativeId;
                    }
                }
            }
        }

        // New thread — use this message's own Message-ID
        String ownMessageId = EmailWebhookParser.stripAngleBrackets(mimeMessage.getMessageID());
        if (ownMessageId == null || ownMessageId.isEmpty()) {
            ownMessageId = "email-" + System.currentTimeMillis() + "-" + channel.id().value();
        }
        return ownMessageId;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int parseIntAttr(Map<String, String> attrs, String key, int defaultValue) {
        try {
            return Integer.parseInt(attrs.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (Exception ignore) {
            }
        }
    }

    private static void closeQuietly(Store store) {
        if (store != null) {
            try {
                store.close();
            } catch (Exception ignore) {
            }
        }
    }
}
