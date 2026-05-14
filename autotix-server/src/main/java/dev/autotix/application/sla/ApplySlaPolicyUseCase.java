package dev.autotix.application.sla;

import dev.autotix.domain.sla.SlaPolicy;
import dev.autotix.domain.sla.SlaPolicyRepository;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Applies SLA deadlines to a Ticket based on the configured SlaPolicy for its priority.
 * Falls back to hardcoded defaults when no policy row exists.
 *
 * Due timestamps are anchored at ticket.createdAt() so changing priority doesn't reset
 * the clock start — only the due-by window changes.
 */
@Service
public class ApplySlaPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApplySlaPolicyUseCase.class);

    /** Default minutes: [firstResponseMinutes, resolutionMinutes] */
    private static final Map<TicketPriority, int[]> DEFAULTS = new EnumMap<>(TicketPriority.class);

    static {
        DEFAULTS.put(TicketPriority.LOW,    new int[]{480,  2880});
        DEFAULTS.put(TicketPriority.NORMAL, new int[]{240,  1440});
        DEFAULTS.put(TicketPriority.HIGH,   new int[]{60,    480});
        DEFAULTS.put(TicketPriority.URGENT, new int[]{30,    240});
    }

    private final SlaPolicyRepository slaPolicyRepository;

    public ApplySlaPolicyUseCase(SlaPolicyRepository slaPolicyRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
    }

    /**
     * Compute and apply SLA due timestamps to the given ticket.
     * Anchored at ticket.createdAt() — re-applying on priority change
     * shifts the due windows without resetting the clock.
     *
     * @param ticket the ticket to apply SLA to (mutated in place)
     * @param now    current time (unused; kept for API clarity)
     */
    public void apply(Ticket ticket, Instant now) {
        TicketPriority priority = ticket.priority() != null ? ticket.priority() : TicketPriority.NORMAL;
        int firstResponseMinutes;
        int resolutionMinutes;

        Optional<SlaPolicy> policyOpt = slaPolicyRepository.findByPriority(priority);
        if (policyOpt.isPresent()) {
            SlaPolicy policy = policyOpt.get();
            firstResponseMinutes = policy.firstResponseMinutes();
            resolutionMinutes = policy.resolutionMinutes();
            log.debug("Applied SLA policy '{}' to ticket (priority={}): firstResponse={}m, resolution={}m",
                    policy.name(), priority.name(), firstResponseMinutes, resolutionMinutes);
        } else {
            int[] defaults = DEFAULTS.getOrDefault(priority, DEFAULTS.get(TicketPriority.NORMAL));
            firstResponseMinutes = defaults[0];
            resolutionMinutes = defaults[1];
            log.debug("No SLA policy for priority={}; using defaults: firstResponse={}m, resolution={}m",
                    priority.name(), firstResponseMinutes, resolutionMinutes);
        }

        Instant anchor = ticket.createdAt() != null ? ticket.createdAt() : now;
        Instant firstResponseDue = anchor.plusSeconds(firstResponseMinutes * 60L);
        Instant resolutionDue = anchor.plusSeconds(resolutionMinutes * 60L);

        ticket.applySlaDeadlines(firstResponseDue, resolutionDue);
    }
}
