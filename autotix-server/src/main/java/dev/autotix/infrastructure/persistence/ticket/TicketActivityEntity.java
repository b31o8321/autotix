package dev.autotix.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MyBatis Plus entity for ticket_activity.
 * Ordered by (ticket_id, occurred_at DESC).
 */
@Data
@TableName("ticket_activity")
public class TicketActivityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ticket_id")
    private Long ticketId;

    /** "customer" / "ai" / "agent:{userId}" / "system" */
    private String actor;

    /** TicketActivityAction name */
    private String action;

    /** Free-form JSON string, may be null */
    private String details;

    @TableField("occurred_at")
    private Instant occurredAt;
}
