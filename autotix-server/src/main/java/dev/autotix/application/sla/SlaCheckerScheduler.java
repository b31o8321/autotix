package dev.autotix.application.sla;

import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Background job that detects SLA breaches and marks affected tickets.
 *
 * Runs every {@code autotix.sla.check-interval-ms} milliseconds (default 60s).
 * Checks for:
 *   1. firstResponseDueAt < now AND firstResponseAt IS NULL (no outbound yet) AND status != SOLVED/CLOSED/SPAM
 *   2. resolutionDueAt < now AND solvedAt IS NULL AND status != SOLVED/CLOSED/SPAM
 * For any match: marks breached, saves, logs SLA_BREACHED activity, publishes STATUS_CHANGED event.
 *
 * Already-breached tickets are skipped (sla_breached = true in query).
 */
@Component
public class SlaCheckerScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaCheckerScheduler.class);

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;
    private final InboxEventPublisher inboxEventPublisher;

    public SlaCheckerScheduler(TicketRepository ticketRepository,
                                TicketActivityRepository activityRepository,
                                InboxEventPublisher inboxEventPublisher) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
        this.inboxEventPublisher = inboxEventPublisher;
    }

    @Scheduled(fixedDelayString = "${autotix.sla.check-interval-ms:60000}")
    public void checkOverdue() {
        Instant now = Instant.now();
        List<Ticket> overdueTickets = ticketRepository.findOverdue(now);

        if (overdueTickets.isEmpty()) {
            log.debug("SlaChecker: no overdue tickets found");
            return;
        }

        log.info("SlaChecker: {} ticket(s) have breached SLA", overdueTickets.size());

        for (Ticket ticket : overdueTickets) {
            try {
                ticket.markSlaBreached();
                ticketRepository.save(ticket);

                activityRepository.save(new TicketActivity(
                        ticket.id(),
                        "system",
                        TicketActivityAction.SLA_BREACHED,
                        "{\"externalNativeId\":\"" + ticket.externalNativeId() + "\"}",
                        now));

                inboxEventPublisher.publish(new InboxEvent(
                        InboxEvent.Kind.STATUS_CHANGED,
                        ticket.id().value(),
                        ticket.channelId().value(),
                        "SLA breached on #" + ticket.externalNativeId(),
                        now));

                log.debug("SlaChecker: marked ticket {} as SLA_BREACHED", ticket.id().value());
            } catch (Exception e) {
                log.error("SlaChecker: failed to mark ticket {} as breached: {}",
                        ticket.id() != null ? ticket.id().value() : "?", e.getMessage(), e);
            }
        }
    }
}
