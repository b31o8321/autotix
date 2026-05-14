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
 *   4. Call AIReplyPort.generate
 *   5. Pass result to ReplyTicketUseCase
 *   6. Apply optional AI action (solve, tag) — AI CLOSE → solve (not permanent close)
 *   7. Release lock (try-with-resources)
 *   8. Publish InboxEvent (AI_REPLIED on success, ASSIGNED on fallback)
 *
 * Failure handling: on AI error, assign ticket to "ai-fallback-queue" for human review.
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

    public DispatchAIReplyUseCase(TicketRepository ticketRepository,
                                  ChannelRepository channelRepository,
                                  AIReplyPort aiReplyPort,
                                  ReplyTicketUseCase replyTicketUseCase,
                                  SolveTicketUseCase solveTicketUseCase,
                                  LockProvider lockProvider,
                                  InboxEventPublisher inboxEventPublisher) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.aiReplyPort = aiReplyPort;
        this.replyTicketUseCase = replyTicketUseCase;
        this.solveTicketUseCase = solveTicketUseCase;
        this.lockProvider = lockProvider;
        this.inboxEventPublisher = inboxEventPublisher;
    }

    public void dispatch(TicketId ticketId) {
        LockProvider.LockHandle lock = lockProvider.tryAcquire(
                "ai-dispatch:" + ticketId.value(), Duration.ofMinutes(5));
        if (lock == null) {
            log.info("Concurrent AI dispatch skipped for ticket={}", ticketId.value());
            return;
        }

        try (LockProvider.LockHandle lockHandle = lock) {
            // Load ticket
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new AutotixException.NotFoundException(
                            "Ticket not found: " + ticketId.value()));

            // Load channel
            Channel channel = channelRepository.findById(ticket.channelId())
                    .orElseThrow(() -> new AutotixException.NotFoundException(
                            "Channel not found: " + ticket.channelId().value()));

            // Build history (all messages except the last inbound)
            List<Message> messages = ticket.messages();
            List<AIRequest.HistoryTurn> history = new ArrayList<>();
            String latestMessage = "";

            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (i == messages.size() - 1 && msg.direction() == MessageDirection.INBOUND) {
                    latestMessage = msg.content();
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
                return;
            }

            // Send reply via platform
            replyTicketUseCase.reply(ticketId, response.reply(), "ai");

            // Publish AI_REPLIED event
            inboxEventPublisher.publish(new InboxEvent(
                    InboxEvent.Kind.AI_REPLIED,
                    ticketId.value(),
                    channel.id().value(),
                    "AI replied",
                    Instant.now()));

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
