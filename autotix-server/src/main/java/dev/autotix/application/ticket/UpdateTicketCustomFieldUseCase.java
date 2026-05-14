package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Sets or clears a custom field on a ticket.
 * Logs CUSTOM_FIELD_CHANGED activity.
 */
@Service
public class UpdateTicketCustomFieldUseCase {

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;

    public UpdateTicketCustomFieldUseCase(TicketRepository ticketRepository,
                                           TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
    }

    public void update(TicketId ticketId, String key, String value) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        if (value == null || value.trim().isEmpty()) {
            ticket.clearCustomField(key);
        } else {
            ticket.setCustomField(key, value);
        }

        ticketRepository.save(ticket);

        String escapedKey = key.replace("\"", "\\\"");
        String escapedValue = value != null ? value.replace("\"", "\\\"") : "";
        String details = "{\"key\":\"" + escapedKey + "\",\"value\":\"" + escapedValue + "\"}";

        activityRepository.save(new TicketActivity(
                ticketId, "agent",
                TicketActivityAction.CUSTOM_FIELD_CHANGED,
                details,
                Instant.now()));
    }
}
