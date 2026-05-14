package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.tag.TagDefinitionRepository;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds and removes tags on a ticket.
 * New tags are automatically upserted into the TagDefinition library.
 */
@Service
public class UpdateTagsUseCase {

    private static final String DEFAULT_TAG_COLOR = "#9BAAB8";

    private final TicketRepository ticketRepository;
    private final TagDefinitionRepository tagDefinitionRepository;
    private final TicketActivityRepository activityRepository;

    public UpdateTagsUseCase(TicketRepository ticketRepository,
                              TagDefinitionRepository tagDefinitionRepository,
                              TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.tagDefinitionRepository = tagDefinitionRepository;
        this.activityRepository = activityRepository;
    }

    public void update(TicketId ticketId, Set<String> add, Set<String> remove) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        if (add != null && !add.isEmpty()) {
            ticket.addTags(new HashSet<>(add));
            // Upsert each new tag into the library
            for (String tag : add) {
                tagDefinitionRepository.upsertByName(tag, DEFAULT_TAG_COLOR);
            }
        }

        if (remove != null && !remove.isEmpty()) {
            ticket.removeTags(new HashSet<>(remove));
        }

        ticketRepository.save(ticket);

        // Log activity
        String addedJson = add != null
                ? add.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","))
                : "";
        String removedJson = remove != null
                ? remove.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","))
                : "";
        String details = "{\"added\":[" + addedJson + "],\"removed\":[" + removedJson + "]}";

        activityRepository.save(new TicketActivity(
                ticketId, "agent",
                TicketActivityAction.TAGS_CHANGED,
                details,
                Instant.now()));
    }
}
