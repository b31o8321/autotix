package dev.autotix.domain.ticket;

import java.util.List;

/**
 * Port for persisting and querying ticket activity log entries.
 * Write-only from the domain perspective (no delete / update).
 */
public interface TicketActivityRepository {

    /**
     * Persist a new activity record.
     * Sets the persisted id on the activity object after insert.
     */
    void save(TicketActivity activity);

    /**
     * Return activity entries for a ticket, ordered by occurredAt DESC.
     *
     * @param ticketId the ticket to query
     * @param offset   0-based offset
     * @param limit    max records to return
     */
    List<TicketActivity> findByTicketId(TicketId ticketId, int offset, int limit);
}
