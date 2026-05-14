package dev.autotix.domain.sla;

import dev.autotix.domain.ticket.TicketPriority;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for SLA policies.
 * Implemented in infrastructure/persistence/sla.
 */
public interface SlaPolicyRepository {

    /** Persist a new or existing policy; returns the policy id. */
    String save(SlaPolicy policy);

    Optional<SlaPolicy> findById(String id);

    /** Find the policy for the given priority (at most one per priority). */
    Optional<SlaPolicy> findByPriority(TicketPriority priority);

    /** All enabled policies, ordered by priority ordinal. */
    List<SlaPolicy> findAllEnabled();

    /** All policies (enabled and disabled). */
    List<SlaPolicy> findAll();
}
