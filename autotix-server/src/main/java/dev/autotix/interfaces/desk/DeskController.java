package dev.autotix.interfaces.desk;

import dev.autotix.application.ticket.*;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketSearchQuery;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.interfaces.desk.dto.MessageDTO;
import dev.autotix.interfaces.desk.dto.ReplyRequest;
import dev.autotix.interfaces.desk.dto.TicketDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for the human agent desk UI.
 * Auth: JWT (enforced by SecurityConfig — ROLE_AGENT or ROLE_ADMIN).
 *
 * Endpoints:
 *  GET    /api/desk/tickets                   — list / search
 *  GET    /api/desk/tickets/{id}              — detail
 *  GET    /api/desk/tickets/{id}/messages     — messages
 *  POST   /api/desk/tickets/{id}/reply        — send reply
 *  POST   /api/desk/tickets/{id}/assign       — assign to agent
 *  POST   /api/desk/tickets/{id}/solve        — solve (primary agent action; status → SOLVED)
 *  POST   /api/desk/tickets/{id}/close        — permanently close (admin action; status → CLOSED)
 */
@RestController
@RequestMapping("/api/desk/tickets")
public class DeskController {

    private final ListTicketsUseCase listTickets;
    private final ReplyTicketUseCase replyTicket;
    private final AssignTicketUseCase assignTicket;
    private final SolveTicketUseCase solveTicket;
    private final CloseTicketUseCase closeTicket;
    private final TicketRepository ticketRepository;

    public DeskController(ListTicketsUseCase listTickets,
                          ReplyTicketUseCase replyTicket,
                          AssignTicketUseCase assignTicket,
                          SolveTicketUseCase solveTicket,
                          CloseTicketUseCase closeTicket,
                          TicketRepository ticketRepository) {
        this.listTickets = listTickets;
        this.replyTicket = replyTicket;
        this.assignTicket = assignTicket;
        this.solveTicket = solveTicket;
        this.closeTicket = closeTicket;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping
    public List<TicketDTO> list(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String channelId,
                                @RequestParam(required = false) String assignee,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "50") int limit) {
        TicketSearchQuery query = new TicketSearchQuery();
        if (status != null && !status.isEmpty()) {
            try {
                query.status = TicketStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Invalid status — ignore filter
            }
        }
        if (channelId != null && !channelId.isEmpty()) {
            query.channelId = new ChannelId(channelId);
        }
        query.assigneeId = assignee;
        query.text = q;
        query.offset = offset;
        query.limit = limit;

        return listTickets.list(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{ticketId}")
    public TicketDTO get(@PathVariable String ticketId) {
        Ticket ticket = ticketRepository.findById(new TicketId(ticketId))
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId));
        TicketDTO dto = toDTO(ticket);
        dto.messages = ticket.messages().stream()
                .map(this::toMessageDTO)
                .collect(Collectors.toList());
        return dto;
    }

    @GetMapping("/{ticketId}/messages")
    public List<MessageDTO> messages(@PathVariable String ticketId) {
        Ticket ticket = ticketRepository.findById(new TicketId(ticketId))
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId));
        return ticket.messages().stream()
                .map(this::toMessageDTO)
                .collect(Collectors.toList());
    }

    @PostMapping("/{ticketId}/reply")
    public void reply(@PathVariable String ticketId, @RequestBody ReplyRequest req) {
        replyTicket.reply(new TicketId(ticketId), req.content, "agent");
        if (req.closeAfter) {
            // "reply and close" in UI means solve
            solveTicket.solve(new TicketId(ticketId));
        }
    }

    @PostMapping("/{ticketId}/assign")
    public void assign(@PathVariable String ticketId, @RequestParam String agentId) {
        assignTicket.assign(new TicketId(ticketId), agentId);
    }

    /**
     * Primary agent "close" action: transitions to SOLVED.
     * Customer can still reopen within the configured reopen window.
     */
    @PostMapping("/{ticketId}/solve")
    public void solve(@PathVariable String ticketId) {
        solveTicket.solve(new TicketId(ticketId));
    }

    /**
     * Permanent close (admin action): transitions to CLOSED (terminal).
     * Any subsequent inbound from the customer spawns a new ticket.
     */
    @PostMapping("/{ticketId}/close")
    public void close(@PathVariable String ticketId) {
        closeTicket.close(new TicketId(ticketId));
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private TicketDTO toDTO(Ticket t) {
        TicketDTO dto = new TicketDTO();
        dto.id = t.id() != null ? t.id().value() : null;
        dto.channelId = t.channelId() != null ? t.channelId().value() : null;
        dto.externalNativeId = t.externalNativeId();
        dto.subject = t.subject();
        dto.customerIdentifier = t.customerIdentifier();
        dto.customerName = t.customerName();
        dto.status = t.status() != null ? t.status().name() : null;
        dto.assigneeId = t.assigneeId();
        dto.tags = t.tags();
        dto.createdAt = t.createdAt();
        dto.updatedAt = t.updatedAt();
        dto.solvedAt = t.solvedAt();
        dto.closedAt = t.closedAt();
        dto.parentTicketId = t.parentTicketId() != null ? t.parentTicketId().value() : null;
        dto.reopenCount = t.reopenCount();
        return dto;
    }

    private MessageDTO toMessageDTO(dev.autotix.domain.ticket.Message m) {
        MessageDTO dto = new MessageDTO();
        dto.direction = m.direction().name();
        dto.author = m.author();
        dto.content = m.content();
        dto.occurredAt = m.occurredAt();
        return dto;
    }
}
