package dev.autotix.interfaces.desk;

import dev.autotix.application.attachment.UploadAttachmentUseCase;
import dev.autotix.application.ticket.*;
import dev.autotix.infrastructure.auth.CurrentUser;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Attachment;
import dev.autotix.domain.ticket.AttachmentRepository;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketPriority;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketSearchQuery;
import dev.autotix.domain.ticket.SlaState;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.domain.ticket.TicketType;
import dev.autotix.interfaces.desk.dto.AttachmentDTO;
import dev.autotix.interfaces.desk.dto.MessageDTO;
import dev.autotix.interfaces.desk.dto.ReplyRequest;
import dev.autotix.interfaces.desk.dto.TicketActivityDTO;
import dev.autotix.interfaces.desk.dto.TicketDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
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
    private final AttachmentRepository attachmentRepository;
    private final UploadAttachmentUseCase uploadAttachmentUseCase;
    private final EscalateToHumanUseCase escalateToHuman;
    private final ResumeAiUseCase resumeAi;
    private final CurrentUser currentUser;

    public DeskController(ListTicketsUseCase listTickets,
                          ReplyTicketUseCase replyTicket,
                          AssignTicketUseCase assignTicket,
                          SolveTicketUseCase solveTicket,
                          CloseTicketUseCase closeTicket,
                          TicketRepository ticketRepository,
                          ChangeTicketPriorityUseCase changePriority,
                          ChangeTicketTypeUseCase changeType,
                          ListTicketActivityUseCase listActivity,
                          AttachmentRepository attachmentRepository,
                          UploadAttachmentUseCase uploadAttachmentUseCase,
                          EscalateToHumanUseCase escalateToHuman,
                          ResumeAiUseCase resumeAi,
                          CurrentUser currentUser) {
        this.listTickets = listTickets;
        this.replyTicket = replyTicket;
        this.assignTicket = assignTicket;
        this.solveTicket = solveTicket;
        this.closeTicket = closeTicket;
        this.ticketRepository = ticketRepository;
        this.changePriority = changePriority;
        this.changeType = changeType;
        this.listActivity = listActivity;
        this.attachmentRepository = attachmentRepository;
        this.uploadAttachmentUseCase = uploadAttachmentUseCase;
        this.escalateToHuman = escalateToHuman;
        this.resumeAi = resumeAi;
        this.currentUser = currentUser;
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
        TicketId tid = new TicketId(ticketId);
        Ticket ticket = ticketRepository.findById(tid)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId));
        TicketDTO dto = toDTO(ticket);
        dto.messages = buildMessageDTOs(tid, ticket);
        return dto;
    }

    @GetMapping("/{ticketId}/messages")
    public List<MessageDTO> messages(@PathVariable String ticketId) {
        TicketId tid = new TicketId(ticketId);
        Ticket ticket = ticketRepository.findById(tid)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId));
        return buildMessageDTOs(tid, ticket);
    }

    @PostMapping("/{ticketId}/reply")
    public void reply(@PathVariable String ticketId, @RequestBody ReplyRequest req) {
        replyTicket.reply(new TicketId(ticketId), req.content, "agent", req.internal, req.attachmentIds);
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

    /**
     * POST /api/desk/tickets/{id}/escalate — suspend AI and escalate to human.
     * Any AGENT or ADMIN can call this.
     */
    @PostMapping("/{ticketId}/escalate")
    public void escalate(@PathVariable String ticketId,
                         @RequestBody(required = false) EscalateRequest req) {
        String actorId = "agent:" + currentUser.id().value();
        String reason = req != null ? req.reason : null;
        escalateToHuman.escalate(new TicketId(ticketId), actorId, reason);
    }

    /**
     * POST /api/desk/tickets/{id}/resume-ai — re-enable AI for a previously escalated ticket.
     * Admin-only.
     */
    @PostMapping("/{ticketId}/resume-ai")
    @PreAuthorize("hasRole('ADMIN')")
    public void resumeAi(@PathVariable String ticketId) {
        String actorId = "agent:" + currentUser.id().value();
        resumeAi.resume(new TicketId(ticketId), actorId);
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
    // Inner DTOs
    // -----------------------------------------------------------------------

    public static class EscalateRequest {
        public String reason;
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
        // Slice 10: SLA fields
        dto.firstResponseAt = t.firstResponseAt();
        dto.firstHumanResponseAt = t.firstHumanResponseAt();
        dto.firstResponseDueAt = t.firstResponseDueAt();
        dto.resolutionDueAt = t.resolutionDueAt();
        dto.slaBreached = t.slaBreached();
        if (t.firstResponseDueAt() != null || t.resolutionDueAt() != null) {
            SlaState state = t.currentSlaState(java.time.Instant.now());
            dto.firstResponseRemainingMs = t.firstResponseDueAt() != null
                    ? state.firstResponseRemainingMs() : null;
            dto.resolutionRemainingMs = t.resolutionDueAt() != null
                    ? state.resolutionRemainingMs() : null;
        }
        // Slice 13
        dto.aiSuspended = t.aiSuspended();
        dto.escalatedAt = t.escalatedAt();
        return dto;
    }

    /**
     * Build message DTOs with attachments enriched.
     * Message IDs are fetched from DB in order; attachments matched by index.
     */
    private List<MessageDTO> buildMessageDTOs(TicketId ticketId, Ticket ticket) {
        List<MessageDTO> dtos = ticket.messages().stream()
                .map(this::toMessageDTO)
                .collect(Collectors.toList());

        try {
            List<Long> msgIds = ticketRepository.findMessageIdsByTicketIdOrdered(ticketId);
            for (int i = 0; i < Math.min(msgIds.size(), dtos.size()); i++) {
                List<Attachment> atts = attachmentRepository.findByMessageId(msgIds.get(i));
                if (!atts.isEmpty()) {
                    dtos.get(i).attachments = atts.stream()
                            .map(a -> uploadAttachmentUseCase.toDTO(a))
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            // Non-fatal: return messages without attachment enrichment rather than failing
        }
        return dtos;
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
