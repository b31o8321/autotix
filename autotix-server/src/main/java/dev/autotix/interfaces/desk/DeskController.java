package dev.autotix.interfaces.desk;

import dev.autotix.application.ticket.*;
import dev.autotix.interfaces.desk.dto.MessageDTO;
import dev.autotix.interfaces.desk.dto.ReplyRequest;
import dev.autotix.interfaces.desk.dto.TicketDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TODO: REST API for the human agent desk UI.
 *  Auth: TBD (basic auth / JWT; not required in dev).
 */
@RestController
@RequestMapping("/api/desk/tickets")
public class DeskController {

    private final ListTicketsUseCase listTickets;
    private final ReplyTicketUseCase replyTicket;
    private final AssignTicketUseCase assignTicket;
    private final CloseTicketUseCase closeTicket;

    public DeskController(ListTicketsUseCase listTickets, ReplyTicketUseCase replyTicket,
                          AssignTicketUseCase assignTicket, CloseTicketUseCase closeTicket) {
        this.listTickets = listTickets;
        this.replyTicket = replyTicket;
        this.assignTicket = assignTicket;
        this.closeTicket = closeTicket;
    }

    @GetMapping
    public List<TicketDTO> list(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String channelId,
                                @RequestParam(required = false) String assignee,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "50") int limit) {
        // TODO: build TicketSearchQuery; call listTickets; map to DTO
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/{ticketId}")
    public TicketDTO get(@PathVariable String ticketId) {
        // TODO: fetch ticket + messages, map to DTO with full thread
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/{ticketId}/messages")
    public List<MessageDTO> messages(@PathVariable String ticketId) {
        // TODO: load full thread
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{ticketId}/reply")
    public void reply(@PathVariable String ticketId, @RequestBody ReplyRequest req) {
        // TODO: replyTicket.reply(...)
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{ticketId}/assign")
    public void assign(@PathVariable String ticketId, @RequestParam String agentId) {
        // TODO: assignTicket.assign(...)
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{ticketId}/close")
    public void close(@PathVariable String ticketId) {
        // TODO: closeTicket.close(...)
        throw new UnsupportedOperationException("TODO");
    }
}
