package dev.autotix.infrastructure.sla;

import dev.autotix.domain.sla.SlaPolicy;
import dev.autotix.domain.sla.SlaPolicyRepository;
import dev.autotix.domain.ticket.TicketPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup: ensure one enabled SLA policy exists per TicketPriority.
 * If no row exists for a priority, inserts the default values.
 *
 * Defaults:
 *   LOW    — 480 min first response / 2880 min resolution (8h / 48h)
 *   NORMAL — 240 min / 1440 min (4h / 24h)
 *   HIGH   — 60 min  / 480 min  (1h / 8h)
 *   URGENT — 30 min  / 240 min  (30m / 4h)
 */
@Component
public class SlaPolicyBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SlaPolicyBootstrap.class);

    private final SlaPolicyRepository slaPolicyRepository;

    public SlaPolicyBootstrap(SlaPolicyRepository slaPolicyRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensurePolicy(TicketPriority.LOW, "Low Priority SLA", 480, 2880);
        ensurePolicy(TicketPriority.NORMAL, "Normal Priority SLA", 240, 1440);
        ensurePolicy(TicketPriority.HIGH, "High Priority SLA", 60, 480);
        ensurePolicy(TicketPriority.URGENT, "Urgent Priority SLA", 30, 240);
    }

    private void ensurePolicy(TicketPriority priority, String defaultName,
                               int defaultFirstResponseMin, int defaultResolutionMin) {
        if (!slaPolicyRepository.findByPriority(priority).isPresent()) {
            SlaPolicy policy = SlaPolicy.create(defaultName, priority,
                    defaultFirstResponseMin, defaultResolutionMin, true);
            slaPolicyRepository.save(policy);
            log.info("Seeded SLA policy for priority={} (firstResponse={}m, resolution={}m)",
                    priority.name(), defaultFirstResponseMin, defaultResolutionMin);
        }
    }
}
