package dev.autotix.application.ticket;

import dev.autotix.application.automation.EvaluateRulesUseCase;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketDomainService;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import dev.autotix.infrastructure.infra.idempotency.IdempotencyStore;
import dev.autotix.infrastructure.infra.queue.QueueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 *   2. Load most-recent matching Ticket (if any)
 *   3. Decide: create / append / reopen / spawn based on existing ticket state
 *   4. Evaluate automation rules; apply outcome (tags, assign, solve, skipAi)
 *   5. If autoReply enabled && shouldAutoReply && !skipAi && !immediateSolved -> enqueue AI
 *   6. Persist Ticket
 *   7. Publish InboxEvent
 *
 * Spawn logic (Zendesk two-stage close):
 *   - CLOSED or SPAM ticket         → spawn new ticket with parentTicketId
 *   - SOLVED, within reopen window  → reopen then append
 *   - SOLVED, past reopen window    → spawn new ticket with parentTicketId
 *   - Any other active state        → append inbound
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
    private final TicketActivityRepository activityRepository;

    @Value("${autotix.ticket.reopen-window-days:7}")
    private int reopenWindowDays;

    public ProcessWebhookUseCase(TicketRepository ticketRepository,
                                 IdempotencyStore idempotencyStore,
                                 QueueProvider queueProvider,
                                 TicketDomainService ticketDomainService,
                                 EvaluateRulesUseCase evaluateRulesUseCase,
                                 InboxEventPublisher inboxEventPublisher,
                                 TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.idempotencyStore = idempotencyStore;
        this.queueProvider = queueProvider;
        this.ticketDomainService = ticketDomainService;
        this.evaluateRulesUseCase = evaluateRulesUseCase;
        this.inboxEventPublisher = inboxEventPublisher;
        this.activityRepository = activityRepository;
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

        // 2. Load most-recent ticket matching (channel, externalTicketId)
        Optional<Ticket> existing = ticketRepository.findByChannelAndExternalId(
                channel.id(), event.externalTicketId());

        Message inbound = new Message(
                MessageDirection.INBOUND,
                event.customerIdentifier() != null && !event.customerIdentifier().isEmpty()
                        ? event.customerIdentifier() : "customer",
                event.messageBody() != null && !event.messageBody().isEmpty()
                        ? event.messageBody() : "(no content)",
                event.occurredAt());

        Instant now = Instant.now();
        Duration reopenWindow = Duration.ofDays(reopenWindowDays);

        boolean isNewTicket;
        Ticket ticket;

        if (!existing.isPresent()) {
            // 3a. Brand new externalTicketId — create fresh ticket
            ticket = Ticket.openFromInbound(
                    channel.id(),
                    event.externalTicketId(),
                    event.subject(),
                    event.customerIdentifier(),
                    inbound);
            isNewTicket = true;

        } else {
            Ticket found = existing.get();
            TicketStatus foundStatus = found.status();

            if (foundStatus == TicketStatus.CLOSED || foundStatus == TicketStatus.SPAM) {
                // 3b. Terminal ticket — spawn a new child ticket
                log.info("Spawning new ticket from {} ticket id={}", foundStatus, found.id().value());
                ticket = Ticket.spawnFromClosed(
                        channel.id(),
                        event.externalTicketId(),
                        event.subject(),
                        event.customerIdentifier(),
                        inbound,
                        found.id());
                isNewTicket = true;

            } else if (foundStatus == TicketStatus.SOLVED) {
                if (found.isReopenable(now, reopenWindow)) {
                    // 3c. Within reopen window — reopen then append
                    log.info("Reopening ticket id={} (within {}d window)", found.id().value(), reopenWindowDays);
                    found.reopen(now);
                    found.appendInbound(inbound);
                    ticket = found;
                    isNewTicket = false;
                } else {
                    // 3d. Reopen window expired — spawn new child ticket
                    log.info("Spawning new ticket from expired SOLVED ticket id={}", found.id().value());
                    ticket = Ticket.spawnFromClosed(
                            channel.id(),
                            event.externalTicketId(),
                            event.subject(),
                            event.customerIdentifier(),
                            inbound,
                            found.id());
                    isNewTicket = true;
                }

            } else {
                // 3e. Active ticket — append inbound
                found.appendInbound(inbound);
                ticket = found;
                isNewTicket = false;
            }
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

        boolean immediatelySolved = false;
        // Apply close action from rules (mapped to solve)
        if (outcome.finalAction == AIAction.CLOSE) {
            ticket.solve(now);
            immediatelySolved = true;
        }

        // 5. Persist
        ticketRepository.save(ticket);

        // 5b. Log activity (must happen after save so ticket.id() is set)
        TicketId savedId = ticket.id();
        if (isNewTicket) {
            boolean isSpawned = ticket.parentTicketId() != null;
            if (isSpawned) {
                String details = "{\"parentTicketId\":\"" + ticket.parentTicketId().value() + "\"}";
                activityRepository.save(new TicketActivity(
                        savedId, "system", TicketActivityAction.SPAWNED, details, now));
            } else {
                activityRepository.save(new TicketActivity(
                        savedId, "customer", TicketActivityAction.CREATED, now));
            }
        } else if (!existing.isPresent() || existing.get().status() != ticket.status()) {
            // Detect reopen (status went from SOLVED to OPEN)
            if (existing.isPresent() && existing.get().status() == TicketStatus.SOLVED) {
                activityRepository.save(new TicketActivity(
                        savedId, "customer", TicketActivityAction.REOPENED, now));
            }
        }

        // 6. Enqueue AI dispatch if conditions met
        if (!immediatelySolved && !outcome.skipAi
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
                    now));
        } else {
            inboxEventPublisher.publish(new InboxEvent(
                    InboxEvent.Kind.NEW_MESSAGE,
                    ticket.id().value(),
                    channel.id().value(),
                    "new inbound on #" + event.externalTicketId(),
                    now));
        }
    }
}
