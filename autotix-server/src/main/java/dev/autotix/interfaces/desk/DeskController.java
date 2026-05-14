package dev.autotix.interfaces.desk;

import dev.autotix.application.ticket.*;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketPriority;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketSearchQuery;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.domain.ticket.TicketType;
import dev.autotix.interfaces.desk.dto.MessageDTO;
import dev.autotix.interfaces.desk.dto.ReplyRequest;
import dev.autotix.interfaces.desk.dto.TicketActivityDTO;
import dev.autotix.interfaces.desk.dto.TicketDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for the human agent desk UI.
 * Auth: JWT (enforced by SecurityConfig — ROLE_AGENT or ROLE_ADMIN).
 *
 * Endpoints:
 *  GET    /api/desk/tickets                              — list / search
 *  GET    /api/desk/tickets/{id}                        — detail
 *  GET    /api/desk/tickets/{id}/messages               — messages
 *  POST   /api/desk/tickets/{id}/reply                  — send reply (or internal note)
 *  POST   /api/desk/tickets/{id}/assign                 — assign to agent
 *  POST   /api/desk/tickets/{id}/solve                  — solve
 *  POST   /api/desk/tickets/{id}/close                  — permanently close (admin)
 *  PUT    /api/desk/tickets/{id}/priority?value=HIGH    — change priority
 *  PUT    /api/desk/tickets/{id}/type?value=INCIDENT    — change type
 *  GET    /api/desk/tickets/{id}/activity               — audit log (paginated)
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
    private final ChangeTicketPriorityUseCase changePriority;
    private final ChangeTicketTypeUseCase changeType;
    private final ListTicketActivityUseCase listActivity;

    public DeskController(ListTicketsUseCase listTickets,
                          ReplyTicketUseCase replyTicket,
                          AssignTicketUseCase assignTicket,
                          SolveTicketUseCase solveTicket,
                          CloseTicketUseCase closeTicket,
                          TicketRepository ticketRepository,
                          ChangeTicketPriorityUseCase changePriority,
                          ChangeTicketTypeUseCase changeType,
                          ListTicketActivityUseCase listActivity) {
        this.listTickets = listTickets;
        this.replyTicket = replyTicket;
        this.assignTicket = assignTicket;
        this.solveTicket = solveTicket;
        this.closeTicket = closeTicket;
        this.ticketRepository = ticketRepository;
        this.changePriority = changePriority;
        this.changeType = changeType;
        this.listActivity = listActivity;
    }

    @GetMapping
    public List<TicketDTO> list(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String channelId,
                                @RequestParam(required = false) String assignee,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String priority,
                                @RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "50") int limit) {
        TicketSearchQuery query = new TicketSearchQuery();
        if (status != null && !status.isEmpty()) {
            try {
                query.status = TicketStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (channelId != null && !channelId.isEmpty()) {
            query.channelId = new ChannelId(channelId);
        }
        query.assigneeId = assignee;
        query.text = q;
        query.offset = offset;
        query.limit = limit;

        // Note: priority filter applied in-memory for now (list is already small)
        List<Ticket> results = listTickets.list(query);
        if (priority != null && !priority.isEmpty()) {
            try {
                TicketPriority p = TicketPriority.valueOf(priority.toUpperCase());
                results = results.stream()
                        .filter(t -> t.priority() == p)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return results.stream()
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
        replyTicket.reply(new TicketId(ticketId), req.content, "agent", req.internal);
        if (!req.internal && req.closeAfter) {
            solveTicket.solve(new TicketId(ticketId));
        }
    }

    @PostMapping("/{ticketId}/assign")
    public void assign(@PathVariable String ticketId, @RequestParam String agentId) {
        assignTicket.assign(new TicketId(ticketId), agentId);
    }

    @PostMapping("/{ticketId}/solve")
    public void solve(@PathVariable String ticketId) {
        solveTicket.solve(new TicketId(ticketId));
    }

    @PostMapping("/{ticketId}/close")
    public void close(@PathVariable String ticketId) {
        closeTicket.close(new TicketId(ticketId));
    }

    @PutMapping("/{ticketId}/priority")
    public void changePriority(@PathVariable String ticketId,
                               @RequestParam String value) {
        TicketPriority p;
        try {
            p = TicketPriority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AutotixException.ValidationException("Invalid priority: " + value);
        }
        changePriority.change(new TicketId(ticketId), p, "agent");
    }

    @PutMapping("/{ticketId}/type")
    public void changeType(@PathVariable String ticketId,
                           @RequestParam String value) {
        TicketType t;
        try {
            t = TicketType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AutotixException.ValidationException("Invalid type: " + value);
        }
        changeType.change(new TicketId(ticketId), t, "agent");
    }

    @GetMapping("/{ticketId}/activity")
    public List<TicketActivityDTO> activity(@PathVariable String ticketId,
                                            @RequestParam(defaultValue = "0") int offset,
                                            @RequestParam(defaultValue = "100") int limit) {
        List<TicketActivity> entries = listActivity.list(new TicketId(ticketId), offset, limit);
        return entries.stream()
                .map(this::toActivityDTO)
                .collect(Collectors.toList());
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
        dto.priority = t.priority() != null ? t.priority().name() : null;
        dto.type = t.type() != null ? t.type().name() : null;
        return dto;
    }

    private MessageDTO toMessageDTO(dev.autotix.domain.ticket.Message m) {
        MessageDTO dto = new MessageDTO();
        dto.direction = m.direction().name();
        dto.author = m.author();
        dto.content = m.content();
        dto.occurredAt = m.occurredAt();
        dto.visibility = m.visibility() != null ? m.visibility().name() : "PUBLIC";
        return dto;
    }

    private TicketActivityDTO toActivityDTO(TicketActivity a) {
        TicketActivityDTO dto = new TicketActivityDTO();
        dto.id = a.id();
        dto.ticketId = a.ticketId().value();
        dto.actor = a.actor();
        dto.action = a.action().name();
        dto.details = a.details();
        dto.occurredAt = a.occurredAt();
        return dto;
    }
}
