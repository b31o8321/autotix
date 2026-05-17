package dev.autotix.interfaces.desk.dto;

import java.util.List;

/**
 * Response for POST /api/desk/tickets/bulk.
 * Partial success is the contract — failures list is populated for each ticket that errored.
 */
public class BulkTicketActionResponse {

    public int successCount;
    public List<Failure> failures;

    public BulkTicketActionResponse(int successCount, List<Failure> failures) {
        this.successCount = successCount;
        this.failures = failures;
    }

    public static class Failure {
        public String ticketId;
        public String reason;

        public Failure(String ticketId, String reason) {
            this.ticketId = ticketId;
            this.reason = reason;
        }
    }
}
