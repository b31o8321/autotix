package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.ai.AIReplyPort;
import dev.autotix.domain.ai.AIRequest;
import dev.autotix.domain.ai.AIResponse;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import dev.autotix.infrastructure.infra.lock.LockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Triggered async after a webhook produced a ticket needing AI reply.
 *
 * Flow:
 *   1. Acquire distributed lock on ticketId
 *   2. Load Ticket
 *   3. Build AIRequest from conversation history
 *      - INTERNAL notes → role="system", content="[Internal note from {author}]: {content}"
 *      - PUBLIC outbound → role="assistant"
 *      - INBOUND → role="user"
 *   4. Call AIReplyPort.generate
 *   5. Pass result to ReplyTicketUseCase (PUBLIC reply)
 *   6. Apply optional AI action (solve, tag)
 *   7. Release lock
 *   8. Publish InboxEvent; log activity
 */
@Service
public class DispatchAIReplyUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchAIReplyUseCase.class);

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final AIReplyPort aiReplyPort;
    private final ReplyTicketUseCase replyTicketUseCase;
    private final SolveTicketUseCase solveTicketUseCase;
    private final LockProvider lockProvider;
    private final InboxEventPublisher inboxEventPublisher;
    private final TicketActivityRepository activityRepository;

    public DispatchAIReplyUseCase(TicketRepository ticketRepository,
                                  ChannelRepository channelRepository,
                                  AIReplyPort aiReplyPort,
                                  ReplyTicketUseCase replyTicketUseCase,
                                  SolveTicketUseCase solveTicketUseCase,
                                  LockProvider lockProvider,
                                  InboxEventPublisher inboxEventPublisher,
                                  TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.aiReplyPort = aiReplyPort;
        this.replyTicketUseCase = replyTicketUseCase;
        this.solveTicketUseCase = solveTicketUseCase;
        this.lockProvider = lockProvider;
        this.inboxEventPublisher = inboxEventPublisher;
        this.activityRepository = activityRepository;
    }

    public void dispatch(TicketId ticketId) {
        LockProvider.LockHandle lock = lockProvider.tryAcquire(
                "ai-dispatch:" + ticketId.value(), Duration.ofMinutes(5));
        if (lock == null) {
            log.info("Concurrent AI dispatch skipped for ticket={}", ticketId.value());
            return;
        }

        try (LockProvider.LockHandle lockHandle = lock) {
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new AutotixException.NotFoundException(
                            "Ticket not found: " + ticketId.value()));

            // Slice 12: skip AI if ticket was escalated to human
            if (ticket.aiSuspended()) {
                log.info("Skipping AI dispatch for ticket={} — aiSuspended=true", ticketId.value());
                return;
            }

            Channel channel = channelRepository.findById(ticket.channelId())
                    .orElseThrow(() -> new AutotixException.NotFoundException(
                            "Channel not found: " + ticket.channelId().value()));

            // Build history — INTERNAL notes as role=system, PUBLIC outbound as assistant
            List<Message> messages = ticket.messages();
            List<AIRequest.HistoryTurn> history = new ArrayList<>();
            String latestMessage = "";

            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (i == messages.size() - 1 && msg.direction() == MessageDirection.INBOUND) {
                    latestMessage = msg.content();
                } else if (msg.isInternal()) {
                    // Internal notes give AI context without being customer/agent turns
                    String content = "[Internal note from " + msg.author() + "]: " + msg.content();
                    history.add(new AIRequest.HistoryTurn("system", content));
                } else {
                    String role = msg.direction() == MessageDirection.INBOUND ? "user" : "assistant";
                    history.add(new AIRequest.HistoryTurn(role, msg.content()));
                }
            }
            if (latestMessage.isEmpty() && !messages.isEmpty()) {
                latestMessage = messages.get(messages.size() - 1).content();
            }

            AIRequest request = new AIRequest(
                    channel.type(),
                    ticket.customerName() != null ? ticket.customerName() : ticket.customerIdentifier(),
                    latestMessage,
                    history,
                    null);

            // Call AI — on failure, escalate to human
            AIResponse response;
            try {
                response = aiReplyPort.generate(request);
            } catch (Exception e) {
                log.error("AI generation failed for ticket={}, escalating to human: {}",
                        ticketId.value(), e.getMessage(), e);
                ticket.assignTo("ai-fallback-queue");
                ticketRepository.save(ticket);
                inboxEventPublisher.publish(new InboxEvent(
                        InboxEvent.Kind.ASSIGNED,
                        ticketId.value(),
                        channel.id().value(),
                        "AI failed — assigned to fallback queue",
                        Instant.now()));
                activityRepository.save(new TicketActivity(
                        ticketId, "system",
                        TicketActivityAction.ASSIGNED,
                        "{\"assignee\":\"ai-fallback-queue\",\"reason\":\"ai-error\"}",
                        Instant.now()));
                return;
            }

            // Send reply via platform (PUBLIC)
            replyTicketUseCase.reply(ticketId, response.reply(), "ai");

            // Publish AI_REPLIED event
            inboxEventPublisher.publish(new InboxEvent(
                    InboxEvent.Kind.AI_REPLIED,
                    ticketId.value(),
                    channel.id().value(),
                    "AI replied",
                    Instant.now()));

            // Activity for AI reply is already logged inside ReplyTicketUseCase.reply()

            // Apply optional action: CLOSE → solve (AI should not permanently close)
            if (response.action() == AIAction.CLOSE) {
                solveTicketUseCase.solve(ticketId);
            }

            // Apply tags if any
            if (response.tags() != null && !response.tags().isEmpty()) {
                Ticket reloaded = ticketRepository.findById(ticketId).orElse(ticket);
                reloaded.addTags(new HashSet<>(response.tags()));
                ticketRepository.save(reloaded);
            }

        } // lock released here
    }
}
