package dev.autotix.domain.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregate root for a customer support ticket.
 *  Responsibilities:
 *    - Holds conversation messages and status
 *    - Enforces status transitions (see {@link TicketStatus})
 *    - Tracks origin channel (ChannelId) and external native id (from platform)
 *    - Tag management
 *  Invariants:
 *    - Cannot reply to a CLOSED ticket without reopening
 *    - Inbound message on PENDING ticket moves back to OPEN
 */
public class Ticket {

    private TicketId id;
    private ChannelId channelId;
    private String externalNativeId;       // platform's ticket id (e.g. zendesk numeric)
    private String subject;
    private String customerIdentifier;     // email / phone / user-id on the source platform
    private String customerName;
    private String assigneeId;
    private TicketStatus status;
    private List<Message> messages = new ArrayList<>();
    private Set<String> tags;
    private Instant createdAt;
    private Instant updatedAt;

    /** Private constructor — use factory methods or rehydration. */
    private Ticket() {}

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /** Create a new Ticket in OPEN status from the first inbound message. */
    public static Ticket openFromInbound(ChannelId channelId, String externalNativeId,
                                         String subject, String customerIdentifier,
                                         Message firstMessage) {
        if (channelId == null) {
            throw new AutotixException.ValidationException("channelId must not be null");
        }
        if (externalNativeId == null || externalNativeId.trim().isEmpty()) {
            throw new AutotixException.ValidationException("externalNativeId must not be blank");
        }
        if (firstMessage == null) {
            throw new AutotixException.ValidationException("firstMessage must not be null");
        }
        Instant now = Instant.now();
        Ticket t = new Ticket();
        t.channelId = channelId;
        t.externalNativeId = externalNativeId;
        t.subject = subject;
        t.customerIdentifier = customerIdentifier;
        t.status = TicketStatus.OPEN;
        t.messages = new ArrayList<>();
        t.messages.add(firstMessage);
        t.tags = new HashSet<>();
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    // -----------------------------------------------------------------------
    // Rehydration (called by repository impl)
    // -----------------------------------------------------------------------

    public static Ticket rehydrate(TicketId id, ChannelId channelId, String externalNativeId,
                                   String subject, String customerIdentifier, String customerName,
                                   String assigneeId, TicketStatus status, List<Message> messages,
                                   Set<String> tags, Instant createdAt, Instant updatedAt) {
        Ticket t = new Ticket();
        t.id = id;
        t.channelId = channelId;
        t.externalNativeId = externalNativeId;
        t.subject = subject;
        t.customerIdentifier = customerIdentifier;
        t.customerName = customerName;
        t.assigneeId = assigneeId;
        t.status = status;
        t.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        t.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
        t.createdAt = createdAt;
        t.updatedAt = updatedAt;
        return t;
    }

    // -----------------------------------------------------------------------
    // Package-private id injection (called by repository after INSERT)
    // -----------------------------------------------------------------------

    public void assignPersistedId(TicketId id) {
        this.id = id;
    }

    // -----------------------------------------------------------------------
    // Domain behaviors
    // -----------------------------------------------------------------------

    /** Append inbound message; transition PENDING -> OPEN if applicable. */
    public void appendInbound(Message message) {
        if (message == null) {
            throw new AutotixException.ValidationException("message must not be null");
        }
        if (status == TicketStatus.CLOSED) {
            throw new AutotixException.ValidationException("Cannot append message to a CLOSED ticket");
        }
        messages.add(message);
        if (status == TicketStatus.PENDING) {
            status = TicketStatus.OPEN;
        }
        updatedAt = Instant.now();
    }

    /** Append outbound (AI or agent) reply; transition OPEN -> PENDING. */
    public void appendOutbound(Message message) {
        if (message == null) {
            throw new AutotixException.ValidationException("message must not be null");
        }
        if (status == TicketStatus.CLOSED) {
            throw new AutotixException.ValidationException("Cannot append message to a CLOSED ticket");
        }
        messages.add(message);
        if (status == TicketStatus.OPEN) {
            status = TicketStatus.PENDING;
        }
        updatedAt = Instant.now();
    }

    /**
     * Validate transition and set status.
     * Rule: from CLOSED you can't move to anything.
     *       from OPEN/PENDING/ASSIGNED you can move to any other state.
     */
    public void changeStatus(TicketStatus next) {
        if (next == null) {
            throw new AutotixException.ValidationException("next status must not be null");
        }
        if (status == TicketStatus.CLOSED) {
            throw new AutotixException.ValidationException(
                    "Cannot change status of a CLOSED ticket; reopen first");
        }
        status = next;
        updatedAt = Instant.now();
    }

    /** Assign to human agent — sets status = ASSIGNED, records agent id. */
    public void assignTo(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new AutotixException.ValidationException("agentId must not be blank");
        }
        if (status == TicketStatus.CLOSED) {
            throw new AutotixException.ValidationException("Cannot assign a CLOSED ticket");
        }
        this.assigneeId = agentId;
        this.status = TicketStatus.ASSIGNED;
        updatedAt = Instant.now();
    }

    /** Add tags (idempotent). */
    public void addTags(Set<String> newTags) {
        if (newTags == null) {
            return;
        }
        if (tags == null) {
            tags = new HashSet<>();
        }
        tags.addAll(newTags);
        updatedAt = Instant.now();
    }

    /** Close ticket; enforce not already closed. */
    public void close() {
        if (status == TicketStatus.CLOSED) {
            throw new AutotixException.ValidationException("Ticket is already CLOSED");
        }
        status = TicketStatus.CLOSED;
        updatedAt = Instant.now();
    }

    // -----------------------------------------------------------------------
    // Accessors (no setters — mutation through behavior methods above)
    // -----------------------------------------------------------------------

    public TicketId id() { return id; }
    public ChannelId channelId() { return channelId; }
    public String externalNativeId() { return externalNativeId; }
    public String subject() { return subject; }
    public String customerIdentifier() { return customerIdentifier; }
    public String customerName() { return customerName; }
    public String assigneeId() { return assigneeId; }
    public TicketStatus status() { return status; }
    public List<Message> messages() { return Collections.unmodifiableList(messages); }
    public Set<String> tags() { return tags == null ? Collections.emptySet() : Collections.unmodifiableSet(tags); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
