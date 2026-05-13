package dev.autotix.application.ticket;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketDomainService;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.infra.idempotency.IdempotencyStore;
import dev.autotix.infrastructure.infra.queue.QueueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Entry point invoked by webhook controller after the platform plugin parses the
 * raw payload into a standardized TicketEvent.
 *
 * Flow:
 *   1. Idempotency check by (channelId, externalTicketId, occurredAt)
 *   2. Load or create Ticket
 *   3. Append inbound message
 *   4. If autoReply enabled && shouldAutoReply -> enqueue DispatchAIReplyUseCase
 *   5. Persist Ticket
 */
@Service
public class ProcessWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessWebhookUseCase.class);

    private final TicketRepository ticketRepository;
    private final IdempotencyStore idempotencyStore;
    private final QueueProvider queueProvider;
    private final TicketDomainService ticketDomainService;

    public ProcessWebhookUseCase(TicketRepository ticketRepository,
                                 IdempotencyStore idempotencyStore,
                                 QueueProvider queueProvider,
                                 TicketDomainService ticketDomainService) {
        this.ticketRepository = ticketRepository;
        this.idempotencyStore = idempotencyStore;
        this.queueProvider = queueProvider;
        this.ticketDomainService = ticketDomainService;
    }

    public void handle(Channel channel, TicketEvent event) {
        // 1. Idempotency check
        String idempotencyKey = event.channelId().value()
                + ":" + event.externalTicketId()
                + ":" + event.occurredAt().toEpochMilli();

        boolean firstTime = idempotencyStore.tryMark(idempotencyKey, Duration.ofHours(24));
        if (!firstTime) {
            log.info("Duplicate event ignored (idempotency key={})", idempotencyKey);
            return;
        }

        // 2. Load or create Ticket
        Optional<Ticket> existing = ticketRepository.findByChannelAndExternalId(
                channel.id(), event.externalTicketId());

        Message inbound = new Message(
                MessageDirection.INBOUND,
                event.customerIdentifier() != null && !event.customerIdentifier().isEmpty()
                        ? event.customerIdentifier() : "customer",
                event.messageBody() != null && !event.messageBody().isEmpty()
                        ? event.messageBody() : "(no content)",
                event.occurredAt());

        Ticket ticket;
        if (existing.isPresent()) {
            // 3a. Append inbound message to existing ticket
            ticket = existing.get();
            ticket.appendInbound(inbound);
        } else {
            // 3b. Create new ticket
            ticket = Ticket.openFromInbound(
                    channel.id(),
                    event.externalTicketId(),
                    event.subject(),
                    event.customerIdentifier(),
                    inbound);
        }

        // 4. Persist first so we have an id for the queue message
        ticketRepository.save(ticket);

        // 5. Enqueue AI dispatch if conditions met
        if (channel.isAutoReplyEnabled() && ticketDomainService.shouldAutoReply(ticket)) {
            queueProvider.publish("ai.dispatch", ticket.id().value());
            log.debug("Enqueued AI dispatch for ticket={}", ticket.id().value());
        }
    }
}
