package dev.autotix.domain.event;

import dev.autotix.domain.channel.ChannelId;

import java.time.Instant;
import java.util.Map;

/**
 * TODO: Standardized inbound event produced by a Platform Plugin's webhook parser.
 *  This is the lingua franca that decouples platform-specific webhook payloads
 *  from the Ticket Engine.
 *
 *  Each Plugin must produce TicketEvent from raw webhook input.
 */
public final class TicketEvent {

    private final ChannelId channelId;
    private final EventType type;
    private final String externalTicketId;
    private final String customerIdentifier;
    private final String customerName;
    private final String subject;
    private final String messageBody;            // raw text/html as received
    private final Instant occurredAt;
    private final Map<String, Object> raw;       // original payload for audit/debug

    public TicketEvent(ChannelId channelId, EventType type, String externalTicketId,
                       String customerIdentifier, String customerName, String subject,
                       String messageBody, Instant occurredAt, Map<String, Object> raw) {
        this.channelId = channelId;
        this.type = type;
        this.externalTicketId = externalTicketId;
        this.customerIdentifier = customerIdentifier;
        this.customerName = customerName;
        this.subject = subject;
        this.messageBody = messageBody;
        this.occurredAt = occurredAt;
        this.raw = raw;
    }

    public ChannelId channelId() { return channelId; }
    public EventType type() { return type; }
    public String externalTicketId() { return externalTicketId; }
    public String customerIdentifier() { return customerIdentifier; }
    public String customerName() { return customerName; }
    public String subject() { return subject; }
    public String messageBody() { return messageBody; }
    public Instant occurredAt() { return occurredAt; }
    public Map<String, Object> raw() { return raw; }
}
