package dev.autotix.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MyBatis Plus entity for ticket.
 * Schema indexes (declared in schema-*.sql):
 *   - (channel_id, external_native_id, created_at) — most-recent lookup
 *   - (status, updated_at)                         — desk listing
 *   - (status, solved_at)                          — auto-close scheduler
 *
 * NOTE: The old UNIQUE(channel_id, external_native_id) constraint has been dropped.
 * Duplicate externalNativeId rows are intentional when a closed ticket spawns a new one.
 * Old status values: PENDING → WAITING_ON_CUSTOMER, ASSIGNED → OPEN (dev DBs wiped on restart).
 */
@Data
@TableName("ticket")
public class TicketEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("channel_id")
    private String channelId;

    @TableField("external_native_id")
    private String externalNativeId;

    private String subject;

    @TableField("customer_identifier")
    private String customerIdentifier;

    @TableField("customer_name")
    private String customerName;

    /** NEW / OPEN / WAITING_ON_CUSTOMER / WAITING_ON_INTERNAL / SOLVED / CLOSED / SPAM */
    private String status;

    @TableField("assignee_id")
    private String assigneeId;

    /** comma-separated; TODO: split table when tag count grows */
    @TableField("tags_csv")
    private String tagsCsv;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    /** Timestamp when the ticket entered SOLVED status. Null if never solved. */
    @TableField("solved_at")
    private Instant solvedAt;

    /** Timestamp when the ticket was permanently closed. Null if not closed. */
    @TableField("closed_at")
    private Instant closedAt;

    /**
     * FK (soft) to ticket.id — set when this ticket was spawned from a prior
     * CLOSED/SPAM/expired-SOLVED ticket on the same externalNativeId.
     */
    @TableField("parent_ticket_id")
    private Long parentTicketId;

    /** Number of times this ticket has been reopened from SOLVED. */
    @TableField("reopen_count")
    private Integer reopenCount;
}
