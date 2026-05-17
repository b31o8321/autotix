package dev.autotix.interfaces.desk.dto;

import java.util.List;
import java.util.Map;

/**
 * Request body for POST /api/desk/tickets/bulk.
 *
 * action must be one of the BulkActionType enum values (as string).
 * payload shape depends on action:
 *   STATUS_CHANGE  → { "status": "OPEN" | "WAITING_ON_CUSTOMER" | ... }
 *   ASSIGN         → { "assigneeId": "u-7" }
 *   ADD_TAG        → { "tag": "billing" }
 *   REMOVE_TAG     → { "tag": "billing" }
 *   SOLVE, MARK_SPAM, UNASSIGN → {} (empty payload)
 */
public class BulkTicketActionRequest {

    public List<String> ticketIds;
    public String action;
    public Map<String, Object> payload;
}
