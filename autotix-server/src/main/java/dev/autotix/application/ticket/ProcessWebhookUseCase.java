package dev.autotix.application.ticket;

import dev.autotix.application.automation.EvaluateRulesUseCase;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketDomainService;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import dev.autotix.infrastructure.infra.idempotency.IdempotencyStore;
import dev.autotix.infrastructure.infra.queue.QueueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

/**
 * Entry point invoked by webhook controller after the platform plugin parses the
 * raw payload into a standardized TicketEvent.
 *
 * Flow:
 *   1. Idempotency check by (channelId, externalTicketId, occurredAt)
 *   2. Load or create Ticket
 *   3. Append inbound message
 *   4. Evaluate automation rules; apply outcome (tags, assign, close, skipAi)
 *   5. If autoReply enabled && shouldAutoReply && !skipAi && !immediateClosed -> enqueue DispatchAIReplyUseCase
 *   6. Persist Ticket
 *   7. Publish InboxEvent
 */
@Service
public class ProcessWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessWebhookUseCase.class);

    private final TicketRepository ticketRepository;
    private final IdempotencyStore idempotencyStore;
    private final QueueProvider queueProvider;
    private final TicketDomainService ticketDomainService;
    private final EvaluateRulesUseCase evaluateRulesUseCase;
    private final InboxEventPublisher inboxEventPublisher;

    public ProcessWebhookUseCase(TicketRepository ticketRepository,
                                 IdempotencyStore idempotencyStore,
                                 QueueProvider queueProvider,
                                 TicketDomainService ticketDomainService,
                                 EvaluateRulesUseCase evaluateRulesUseCase,
                                 InboxEventPublisher inboxEventPublisher) {
        this.ticketRepository = ticketRepository;
        this.idempotencyStore = idempotencyStore;
        this.queueProvider = queueProvider;
        this.ticketDomainService = ticketDomainService;
        this.evaluateRulesUseCase = evaluateRulesUseCase;
        this.inboxEventPublisher = inboxEventPublisher;
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

        boolean isNewTicket;
        Ticket ticket;
        if (existing.isPresent()) {
            // 3a. Append inbound message to existing ticket
            ticket = existing.get();
            ticket.appendInbound(inbound);
            isNewTicket = false;
        } else {
            // 3b. Create new ticket
            ticket = Ticket.openFromInbound(
                    channel.id(),
                    event.externalTicketId(),
                    event.subject(),
                    event.customerIdentifier(),
                    inbound);
            isNewTicket = true;
        }

        // 4. Evaluate automation rules
        EvaluateRulesUseCase.RuleOutcome outcome = evaluateRulesUseCase.evaluate(event, channel);

        // Apply tags
        if (!outcome.tags.isEmpty()) {
            ticket.addTags(new HashSet<>(outcome.tags));
        }
        // Apply assignee
        if (outcome.assignee != null) {
            ticket.assignTo(outcome.assignee);
        }

        boolean immediatelyClosed = false;
        // Apply close action
        if (outcome.finalAction == AIAction.CLOSE) {
            ticket.close();
            immediatelyClosed = true;
        }

        // 5. Persist
        ticketRepository.save(ticket);

        // 6. Enqueue AI dispatch if conditions met (and not suppressed by rules)
        if (!immediatelyClosed && !outcome.skipAi
                && channel.isAutoReplyEnabled()
                && ticketDomainService.shouldAutoReply(ticket)) {
            queueProvider.publish("ai.dispatch", ticket.id().value());
            log.debug("Enqueued AI dispatch for ticket={}", ticket.id().value());
        }

        // 7. Publish inbox event
        if (isNewTicket) {
            inboxEventPublisher.publish(new InboxEvent(
                    InboxEvent.Kind.TICKET_CREATED,
                    ticket.id().value(),
                    channel.id().value(),
                    event.subject(),
                    Instant.now()));
        } else {
            inboxEventPublisher.publish(new InboxEvent(
                    InboxEvent.Kind.NEW_MESSAGE,
                    ticket.id().value(),
                    channel.id().value(),
                    "new inbound on #" + event.externalTicketId(),
                    Instant.now()));
        }
    }
}
