package dev.autotix.domain.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregate root for a customer support ticket.
 *
 * Status machine — see {@link TicketStatus} for the full transition table.
 *
 * Key invariants:
 *  - CLOSED and SPAM are terminal; any mutation attempt throws ValidationException.
 *  - SOLVED tickets cannot receive messages; callers must call reopen() first.
 *  - assignTo() only sets assigneeId — it does NOT change status.
 */
public class Ticket {

    private TicketId id;
    private ChannelId channelId;
    private String externalNativeId;       // platform's ticket id
    private String subject;
    private String customerIdentifier;     // email / phone / user-id on the source platform
    private String customerName;
    private String assigneeId;
    private TicketStatus status;
    private List<Message> messages = new ArrayList<>();
    private Set<String> tags;
    private Instant createdAt;
    private Instant updatedAt;

    // New fields — Zendesk two-stage close
    private Instant solvedAt;              // stamped when entering SOLVED
    private Instant closedAt;             // stamped when entering CLOSED
    private TicketId parentTicketId;      // set when this ticket spawned from a prior one
    private int reopenCount;              // increments on each reopen()

    // Slice 9: priority + type
    private TicketPriority priority = TicketPriority.NORMAL;
    private TicketType type = TicketType.QUESTION;

    /** Private constructor — use factory methods or rehydration. */
    private Ticket() {}

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Create a new Ticket in NEW status from the first inbound message.
     * Status is NEW (no one has touched it yet); transitions to WAITING_ON_CUSTOMER
     * on first outbound reply.
     */
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
        t.status = TicketStatus.NEW;
        t.messages = new ArrayList<>();
        t.messages.add(firstMessage);
        t.tags = new HashSet<>();
        t.createdAt = now;
        t.updatedAt = now;
        t.reopenCount = 0;
        t.priority = TicketPriority.NORMAL;
        t.type = TicketType.QUESTION;
        return t;
    }

    /**
     * Spawn a new ticket for a customer message that arrived on a CLOSED/SPAM/
     * expired-SOLVED ticket. Links back via parentTicketId.
     */
    public static Ticket spawnFromClosed(ChannelId channelId, String externalNativeId,
                                         String subject, String customerIdentifier,
                                         Message firstMessage, TicketId parentTicketId) {
        Ticket t = openFromInbound(channelId, externalNativeId, subject, customerIdentifier, firstMessage);
        t.parentTicketId = parentTicketId;
        return t;
    }

    // -----------------------------------------------------------------------
    // Rehydration (called by repository impl)
    // -----------------------------------------------------------------------

    public static Ticket rehydrate(TicketId id, ChannelId channelId, String externalNativeId,
                                   String subject, String customerIdentifier, String customerName,
                                   String assigneeId, TicketStatus status, List<Message> messages,
                                   Set<String> tags, Instant createdAt, Instant updatedAt,
                                   Instant solvedAt, Instant closedAt,
                                   TicketId parentTicketId, int reopenCount) {
        return rehydrate(id, channelId, externalNativeId, subject, customerIdentifier, customerName,
                assigneeId, status, messages, tags, createdAt, updatedAt,
                solvedAt, closedAt, parentTicketId, reopenCount,
                TicketPriority.NORMAL, TicketType.QUESTION);
    }

    public static Ticket rehydrate(TicketId id, ChannelId channelId, String externalNativeId,
                                   String subject, String customerIdentifier, String customerName,
                                   String assigneeId, TicketStatus status, List<Message> messages,
                                   Set<String> tags, Instant createdAt, Instant updatedAt,
                                   Instant solvedAt, Instant closedAt,
                                   TicketId parentTicketId, int reopenCount,
                                   TicketPriority priority, TicketType type) {
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
        t.solvedAt = solvedAt;
        t.closedAt = closedAt;
        t.parentTicketId = parentTicketId;
        t.reopenCount = reopenCount;
        t.priority = priority != null ? priority : TicketPriority.NORMAL;
        t.type = type != null ? type : TicketType.QUESTION;
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

    /**
     * Append inbound message from the customer.
     * Transitions:
     *   WAITING_ON_CUSTOMER → OPEN
     *   NEW / OPEN remain unchanged
     * Throws ValidationException if status is CLOSED, SPAM, or SOLVED
     * (callers must reopen() before appending to a SOLVED ticket).
     */
    public void appendInbound(Message message) {
        if (message == null) {
            throw new AutotixException.ValidationException("message must not be null");
        }
        requireNotTerminal();
        if (status == TicketStatus.SOLVED) {
            throw new AutotixException.ValidationException(
                    "Cannot append message to a SOLVED ticket; call reopen() first");
        }
        messages.add(message);
        if (status == TicketStatus.WAITING_ON_CUSTOMER) {
            status = TicketStatus.OPEN;
        }
        updatedAt = Instant.now();
    }

    /**
     * Append outbound (agent or AI) reply.
     * Transitions:
     *   NEW / OPEN → WAITING_ON_CUSTOMER
     *   WAITING_ON_INTERNAL → WAITING_ON_CUSTOMER
     * Throws ValidationException if status is CLOSED, SPAM, or SOLVED.
     */
    public void appendOutbound(Message message) {
        if (message == null) {
            throw new AutotixException.ValidationException("message must not be null");
        }
        requireNotTerminal();
        if (status == TicketStatus.SOLVED) {
            throw new AutotixException.ValidationException(
                    "Cannot append message to a SOLVED ticket; call reopen() first");
        }
        messages.add(message);
        if (status == TicketStatus.NEW || status == TicketStatus.OPEN
                || status == TicketStatus.WAITING_ON_INTERNAL) {
            status = TicketStatus.WAITING_ON_CUSTOMER;
        }
        updatedAt = Instant.now();
    }

    /**
     * Transition to SOLVED (agent/AI resolved).
     * Valid from OPEN, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL, NEW.
     */
    public void solve(Instant now) {
        requireNotTerminal();
        status = TicketStatus.SOLVED;
        solvedAt = now;
        updatedAt = now;
    }

    /**
     * Reopen a SOLVED ticket (customer re-engaged within window).
     * Increments reopenCount, clears solvedAt.
     * Only valid from SOLVED.
     */
    public void reopen(Instant now) {
        if (status != TicketStatus.SOLVED) {
            throw new AutotixException.ValidationException(
                    "Can only reopen a SOLVED ticket; current status: " + status);
        }
        status = TicketStatus.OPEN;
        reopenCount++;
        solvedAt = null;
        updatedAt = now;
    }

    /**
     * Permanently close the ticket. Terminal — no further transitions.
     * Valid from any non-SPAM state (including SOLVED, OPEN, NEW, WAITING_*).
     * SPAM tickets cannot be permanently closed (already terminal).
     */
    public void permanentClose(Instant now) {
        if (status == TicketStatus.SPAM) {
            throw new AutotixException.ValidationException(
                    "Cannot permanently close a SPAM ticket");
        }
        if (status == TicketStatus.CLOSED) {
            throw new AutotixException.ValidationException("Ticket is already CLOSED");
        }
        status = TicketStatus.CLOSED;
        closedAt = now;
        updatedAt = now;
    }

    /**
     * Mark as spam. Terminal — no further transitions.
     * Valid from any state (including CLOSED, which can be re-spammed if somehow reached).
     */
    public void markSpam(Instant now) {
        status = TicketStatus.SPAM;
        updatedAt = now;
    }

    /**
     * Returns true if this ticket can still be reopened within the configured window.
     * Only meaningful when status == SOLVED; always false otherwise.
     */
    public boolean isReopenable(Instant now, Duration window) {
        if (status != TicketStatus.SOLVED || solvedAt == null) {
            return false;
        }
        return Duration.between(solvedAt, now).compareTo(window) < 0;
    }

    /**
     * Convenience backward-compat method kept for callers that used the old close().
     * Delegates to permanentClose(Instant.now()).
     *
     * @deprecated Use permanentClose(Instant) for explicit control of timestamp,
     *             or solve(Instant) for the normal agent "close" action.
     */
    @Deprecated
    public void close() {
        permanentClose(Instant.now());
    }

    /**
     * Escalate to internal team (WAITING_ON_INTERNAL).
     * Valid from OPEN or WAITING_ON_CUSTOMER.
     */
    public void escalateToInternal(Instant now) {
        if (status != TicketStatus.OPEN && status != TicketStatus.WAITING_ON_CUSTOMER
                && status != TicketStatus.NEW) {
            throw new AutotixException.ValidationException(
                    "Cannot escalate from status: " + status);
        }
        requireNotTerminal();
        status = TicketStatus.WAITING_ON_INTERNAL;
        updatedAt = now;
    }

    /**
     * Assign to a human agent (sets assigneeId only; does NOT change status).
     * Callers that want to set WAITING_ON_INTERNAL should call escalateToInternal() separately.
     */
    public void assignTo(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new AutotixException.ValidationException("agentId must not be blank");
        }
        this.assigneeId = agentId;
        updatedAt = Instant.now();
    }

    /**
     * Generic status setter — kept for backward compatibility with automation rules.
     * Enforces: cannot change status of CLOSED or SPAM ticket.
     */
    public void changeStatus(TicketStatus next) {
        if (next == null) {
            throw new AutotixException.ValidationException("next status must not be null");
        }
        requireNotTerminal();
        status = next;
        updatedAt = Instant.now();
    }

    /**
     * Append an internal note (OUTBOUND, INTERNAL visibility).
     * Does NOT change ticket status (internal notes don't move the conversation).
     * Throws ValidationException if ticket is CLOSED or SPAM.
     */
    public void appendInternalNote(Message message) {
        if (message == null) {
            throw new AutotixException.ValidationException("message must not be null");
        }
        if (message.visibility() != MessageVisibility.INTERNAL) {
            throw new AutotixException.ValidationException(
                    "appendInternalNote requires INTERNAL visibility");
        }
        requireNotTerminal();
        messages.add(message);
        updatedAt = Instant.now();
    }

    /**
     * Change ticket priority. Stamps updatedAt.
     */
    public void changePriority(TicketPriority newPriority) {
        if (newPriority == null) {
            throw new AutotixException.ValidationException("priority must not be null");
        }
        this.priority = newPriority;
        updatedAt = Instant.now();
    }

    /**
     * Change ticket type. Stamps updatedAt.
     */
    public void changeType(TicketType newType) {
        if (newType == null) {
            throw new AutotixException.ValidationException("type must not be null");
        }
        this.type = newType;
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

    // -----------------------------------------------------------------------
    // Internal guards
    // -----------------------------------------------------------------------

    private void requireNotTerminal() {
        if (status == TicketStatus.CLOSED) {
            throw new AutotixException.ValidationException(
                    "Cannot mutate a CLOSED ticket");
        }
        if (status == TicketStatus.SPAM) {
            throw new AutotixException.ValidationException(
                    "Cannot mutate a SPAM ticket");
        }
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
    public Instant solvedAt() { return solvedAt; }
    public Instant closedAt() { return closedAt; }
    public TicketId parentTicketId() { return parentTicketId; }
    public int reopenCount() { return reopenCount; }
    public TicketPriority priority() { return priority; }
    public TicketType type() { return type; }
}
