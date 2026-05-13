package dev.autotix.domain.ai;

import dev.autotix.domain.channel.ChannelType;

import java.util.List;

/**
 * TODO: Value object passed into AIReplyPort.
 *  Constructed by application layer from a Ticket's conversation.
 */
public final class AIRequest {

    private final ChannelType channelType;       // gives AI the context, NOT format
    private final String customerName;
    private final String latestMessage;
    private final List<HistoryTurn> history;
    private final String systemPromptOverride;   // optional per-channel override

    public AIRequest(ChannelType channelType, String customerName, String latestMessage,
                     List<HistoryTurn> history, String systemPromptOverride) {
        // TODO: validate non-null channelType + latestMessage
        this.channelType = channelType;
        this.customerName = customerName;
        this.latestMessage = latestMessage;
        this.history = history;
        this.systemPromptOverride = systemPromptOverride;
    }

    public ChannelType channelType() { return channelType; }
    public String customerName() { return customerName; }
    public String latestMessage() { return latestMessage; }
    public List<HistoryTurn> history() { return history; }
    public String systemPromptOverride() { return systemPromptOverride; }

    /**
     * TODO: a single past message — role is "user" or "assistant" (OpenAI semantics).
     */
    public static final class HistoryTurn {
        public final String role;
        public final String content;
        public HistoryTurn(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
