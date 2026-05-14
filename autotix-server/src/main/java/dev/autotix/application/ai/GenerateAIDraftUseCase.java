package dev.autotix.application.ai;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ai.AIReplyPort;
import dev.autotix.domain.ai.AIRequest;
import dev.autotix.domain.ai.AIResponse;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.ai.AIConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates an AI draft reply for a given ticket, used by the agent desk AI Draft panel.
 *
 * Unlike DispatchAIReplyUseCase (which auto-sends), this only returns the draft.
 * If ticket.aiSuspended == true, throws ValidationException (caller shows note).
 */
@Service
public class GenerateAIDraftUseCase {

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final AIReplyPort aiReplyPort;
    private final AIConfig aiConfig;

    public GenerateAIDraftUseCase(TicketRepository ticketRepository,
                                   ChannelRepository channelRepository,
                                   AIReplyPort aiReplyPort,
                                   AIConfig aiConfig) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.aiReplyPort = aiReplyPort;
        this.aiConfig = aiConfig;
    }

    public Draft generate(TicketId ticketId, GenerationOptions options) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        if (ticket.aiSuspended()) {
            throw new AutotixException.ValidationException(
                    "AI is suspended for this ticket; resume first");
        }

        Channel channel = channelRepository.findById(ticket.channelId())
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + ticket.channelId().value()));

        // Build history from last 5 INBOUND + last 5 OUTBOUND messages
        List<Message> messages = ticket.messages();
        List<AIRequest.HistoryTurn> history = new ArrayList<>();
        String latestMessage = "";

        // Collect up to 5 INBOUND and 5 OUTBOUND (excluding very last message if INBOUND)
        int inboundCount = 0;
        int outboundCount = 0;

        // First pass: find the last INBOUND to treat as latest message
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).direction() == MessageDirection.INBOUND) {
                latestMessage = messages.get(i).content();
                break;
            }
        }

        // Second pass: build history (all except the latest inbound used above)
        boolean foundLatestInbound = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (!foundLatestInbound && msg.direction() == MessageDirection.INBOUND) {
                foundLatestInbound = true;
                continue; // skip — used as latestMessage
            }
            if (msg.direction() == MessageDirection.INBOUND) {
                if (inboundCount < 5) {
                    history.add(0, new AIRequest.HistoryTurn("user", msg.content()));
                    inboundCount++;
                }
            } else {
                if (outboundCount < 5) {
                    if (msg.isInternal()) {
                        history.add(0, new AIRequest.HistoryTurn("system",
                                "[Internal note from " + msg.author() + "]: " + msg.content()));
                    } else {
                        history.add(0, new AIRequest.HistoryTurn("assistant", msg.content()));
                    }
                    outboundCount++;
                }
            }
        }

        if (latestMessage.isEmpty() && !messages.isEmpty()) {
            latestMessage = messages.get(messages.size() - 1).content();
        }

        // Apply style hint to system prompt
        String styleOverride = null;
        if (options != null && options.styleHint != null) {
            switch (options.styleHint) {
                case FRIENDLIER:
                    styleOverride = "Make your reply warm and reassuring.";
                    break;
                case FORMAL:
                    styleOverride = "Use a formal professional tone.";
                    break;
                case SHORTER:
                    styleOverride = "Keep your reply under 60 words.";
                    break;
                default:
                    break;
            }
        }

        AIRequest request = new AIRequest(
                channel.type(),
                ticket.customerName() != null ? ticket.customerName() : ticket.customerIdentifier(),
                latestMessage,
                history,
                styleOverride);

        long start = System.currentTimeMillis();
        AIResponse response = aiReplyPort.generate(request);
        long latencyMs = System.currentTimeMillis() - start;

        List<String> suggestedTags = response.tags() != null ? response.tags() : new ArrayList<>();

        Draft draft = new Draft();
        draft.reply = response.reply();
        draft.action = response.action() != null ? response.action().name() : "NONE";
        draft.suggestedTags = suggestedTags;
        draft.latencyMs = latencyMs;
        draft.modelName = aiConfig.getModel();
        return draft;
    }

    // -----------------------------------------------------------------------
    // Nested types
    // -----------------------------------------------------------------------

    public enum StyleHint {
        DEFAULT, FRIENDLIER, FORMAL, SHORTER
    }

    public static class GenerationOptions {
        public StyleHint styleHint = StyleHint.DEFAULT;
    }

    public static class Draft {
        public String reply;
        public String action;
        public List<String> suggestedTags;
        public long latencyMs;
        public String modelName;
    }
}
