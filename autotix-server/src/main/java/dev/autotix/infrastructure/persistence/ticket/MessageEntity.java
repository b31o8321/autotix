package dev.autotix.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * TODO: MyBatis Plus entity for one ticket message.
 *  Index: (ticket_id, occurred_at).
 */
@Data
@TableName("ticket_message")
public class MessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ticket_id")
    private Long ticketId;

    /** INBOUND / OUTBOUND */
    private String direction;

    private String author;

    private String content;

    @TableField("occurred_at")
    private Instant occurredAt;

    /** Slice 9: PUBLIC (default) or INTERNAL */
    private String visibility;

    /** E2E-B: RFC 2822 Message-ID header for email threading (null for non-email channels). */
    @TableField("email_message_id")
    private String emailMessageId;
}
