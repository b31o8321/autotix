package dev.autotix.application.ticket;

import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background job that auto-closes SOLVED tickets whose reopen window has expired.
 *
 * Runs every {@code autotix.ticket.auto-close-check-interval-ms} milliseconds (default 1 hour).
 * Finds all tickets where status = SOLVED AND solvedAt < now - reopenWindowDays,
 * transitions them to CLOSED, persists, and publishes InboxEvent.STATUS_CHANGED.
 *
 * Requires {@code @EnableScheduling} on a configuration class (already on DomainBeansConfig).
 */
@Component
public class AutoCloseSolvedTicketsScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseSolvedTicketsScheduler.class);

    private final TicketRepository ticketRepository;
    private final InboxEventPublisher inboxEventPublisher;
    private final TicketActivityRepository activityRepository;

    @Value("${autotix.ticket.reopen-window-days:7}")
    private int reopenWindowDays;

    public AutoCloseSolvedTicketsScheduler(TicketRepository ticketRepository,
                                           InboxEventPublisher inboxEventPublisher,
                                           TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.inboxEventPublisher = inboxEventPublisher;
        this.activityRepository = activityRepository;
    }

    @Scheduled(fixedDelayString = "${autotix.ticket.auto-close-check-interval-ms:3600000}")
    public void autoCloseExpiredSolved() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(reopenWindowDays));
        List<Ticket> expired = ticketRepository.findSolvedBefore(cutoff);

        if (expired.isEmpty()) {
            log.debug("AutoClose: no expired SOLVED tickets found");
            return;
        }

        log.info("AutoClose: closing {} expired SOLVED ticket(s) (window={}d)", expired.size(), reopenWindowDays);

        for (Ticket ticket : expired) {
            try {
                ticket.permanentClose(now);
                ticketRepository.save(ticket);

                inboxEventPublisher.publish(new InboxEvent(
                        InboxEvent.Kind.STATUS_CHANGED,
                        ticket.id().value(),
                        ticket.channelId().value(),
                        "auto-closed after reopen window expired",
                        now));

                activityRepository.save(new TicketActivity(
                        ticket.id(), "system",
                        TicketActivityAction.PERMANENTLY_CLOSED,
                        "{\"reason\":\"auto-close-after-window\"}",
                        now));

                log.debug("AutoClose: ticket {} permanently closed", ticket.id().value());
            } catch (Exception e) {
                log.error("AutoClose: failed to close ticket {}: {}",
                        ticket.id() != null ? ticket.id().value() : "?", e.getMessage(), e);
            }
        }
    }
}
