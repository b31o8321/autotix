package dev.autotix.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * TODO: MyBatis Plus entity for ticket.
 *  Schema indexes (declared in V1__init.sql / Flyway):
 *    - UNIQUE (channel_id, external_native_id) — idempotency
 *    - (status, updated_at)                    — desk listing
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

    /** OPEN / PENDING / ASSIGNED / CLOSED */
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
}
