package dev.autotix.application.ticket;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.tag.TagDefinitionRepository;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateTagsUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TagDefinitionRepository tagDefinitionRepository;
    @Mock private TicketActivityRepository activityRepository;

    private UpdateTagsUseCase useCase;

    private Ticket ticket;
    private final TicketId ticketId = new TicketId("10");

    @BeforeEach
    void setUp() {
        useCase = new UpdateTagsUseCase(ticketRepository, tagDefinitionRepository, activityRepository);

        Message inbound = new Message(MessageDirection.INBOUND, "bob@test.com",
                "Hello", Instant.now());
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-1", "Subject",
                "bob@test.com", inbound);
        ticket.assignPersistedId(ticketId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticketId);
    }

    @Test
    void addTags_appliedAndLibraryUpserted() {
        Set<String> add = new HashSet<>();
        add.add("billing");
        add.add("refund");

        useCase.update(ticketId, add, null);

        // Tags should now be on the ticket
        assertTrue(ticket.tags().contains("billing"));
        assertTrue(ticket.tags().contains("refund"));

        // TagDefinitionRepository.upsertByName called for each added tag
        verify(tagDefinitionRepository).upsertByName(eq("billing"), anyString());
        verify(tagDefinitionRepository).upsertByName(eq("refund"), anyString());
    }

    @Test
    void removeTags_appliedToTicket() {
        // First add a tag
        Set<String> existing = new HashSet<>();
        existing.add("existing-tag");
        ticket.addTags(existing);

        Set<String> remove = new HashSet<>();
        remove.add("existing-tag");

        useCase.update(ticketId, null, remove);

        assertFalse(ticket.tags().contains("existing-tag"));
        verifyNoInteractions(tagDefinitionRepository);
    }

    @Test
    void tagsChangedActivity_logged() {
        Set<String> add = new HashSet<>();
        add.add("new-tag");
        Set<String> remove = new HashSet<>();
        remove.add("old-tag");

        useCase.update(ticketId, add, remove);

        ArgumentCaptor<TicketActivity> captor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activityRepository).save(captor.capture());

        TicketActivity activity = captor.getValue();
        assertEquals(ticketId, activity.ticketId());
        assertEquals(TicketActivityAction.TAGS_CHANGED, activity.action());
        assertNotNull(activity.details());
        assertTrue(activity.details().contains("new-tag"));
        assertTrue(activity.details().contains("old-tag"));
    }
}
