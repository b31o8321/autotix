package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.interfaces.desk.dto.BulkTicketActionRequest;
import dev.autotix.interfaces.desk.dto.BulkTicketActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Executes a single bulk action over a list of ticket IDs.
 *
 * Each ticket is processed independently — a failure on one does not abort the rest.
 * The underlying single-ticket use cases handle all business logic and activity logging.
 */
@Service
public class BulkTicketActionUseCase {

    private static final Logger log = LoggerFactory.getLogger(BulkTicketActionUseCase.class);

    private final SolveTicketUseCase solveTicket;
    private final AssignTicketUseCase assignTicket;
    private final MarkSpamUseCase markSpam;
    private final UpdateTagsUseCase updateTags;
    private final ChangeTicketStatusUseCase changeStatus;

    public BulkTicketActionUseCase(SolveTicketUseCase solveTicket,
                                   AssignTicketUseCase assignTicket,
                                   MarkSpamUseCase markSpam,
                                   UpdateTagsUseCase updateTags,
                                   ChangeTicketStatusUseCase changeStatus) {
        this.solveTicket = solveTicket;
        this.assignTicket = assignTicket;
        this.markSpam = markSpam;
        this.updateTags = updateTags;
        this.changeStatus = changeStatus;
    }

    public BulkTicketActionResponse execute(BulkTicketActionRequest req, String actorId) {
        if (req.ticketIds == null || req.ticketIds.isEmpty()) {
            return new BulkTicketActionResponse(0, Collections.emptyList());
        }

        BulkActionType actionType;
        try {
            actionType = BulkActionType.valueOf(req.action);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AutotixException.ValidationException(
                    "Unknown bulk action type: " + req.action);
        }

        Map<String, Object> payload = req.payload != null ? req.payload : Collections.emptyMap();

        int successCount = 0;
        List<BulkTicketActionResponse.Failure> failures = new ArrayList<>();

        for (String rawId : req.ticketIds) {
            try {
                TicketId ticketId = new TicketId(rawId);
                dispatch(actionType, ticketId, payload, actorId);
                successCount++;
            } catch (AutotixException e) {
                failures.add(new BulkTicketActionResponse.Failure(rawId, e.getMessage()));
                log.debug("Bulk action {} failed for ticket {}: {}", actionType, rawId, e.getMessage());
            } catch (Exception e) {
                failures.add(new BulkTicketActionResponse.Failure(rawId, "Unexpected error: " + e.getMessage()));
                log.warn("Unexpected error in bulk action {} for ticket {}", actionType, rawId, e);
            }
        }

        return new BulkTicketActionResponse(successCount, failures);
    }

    private void dispatch(BulkActionType action, TicketId ticketId,
                          Map<String, Object> payload, String actorId) {
        switch (action) {
            case SOLVE:
                solveTicket.solve(ticketId, actorId);
                break;

            case MARK_SPAM:
                markSpam.mark(ticketId, actorId);
                break;

            case ASSIGN: {
                String assigneeId = requireString(payload, "assigneeId");
                assignTicket.assign(ticketId, assigneeId);
                break;
            }

            case UNASSIGN:
                assignTicket.unassign(ticketId);
                break;

            case ADD_TAG: {
                String tag = requireString(payload, "tag");
                updateTags.update(ticketId,
                        new HashSet<>(Collections.singletonList(tag)),
                        Collections.emptySet());
                break;
            }

            case REMOVE_TAG: {
                String tag = requireString(payload, "tag");
                updateTags.update(ticketId,
                        Collections.emptySet(),
                        new HashSet<>(Collections.singletonList(tag)));
                break;
            }

            case STATUS_CHANGE: {
                String statusStr = requireString(payload, "status");
                TicketStatus newStatus;
                try {
                    newStatus = TicketStatus.valueOf(statusStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new AutotixException.ValidationException(
                            "Unknown status: " + statusStr);
                }
                changeStatus.change(ticketId, newStatus, actorId);
                break;
            }

            default:
                throw new AutotixException.ValidationException(
                        "Unsupported bulk action: " + action);
        }
    }

    private String requireString(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null || val.toString().trim().isEmpty()) {
            throw new AutotixException.ValidationException(
                    "payload." + key + " is required for this action");
        }
        return val.toString().trim();
    }
}
