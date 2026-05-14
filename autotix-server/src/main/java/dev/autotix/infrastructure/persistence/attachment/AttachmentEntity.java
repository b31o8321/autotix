package dev.autotix.infrastructure.persistence.attachment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MyBatis Plus entity for the attachment table.
 */
@Data
@TableName("attachment")
public class AttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("ticket_id")
    private Long ticketId;

    @TableField("storage_key")
    private String storageKey;

    @TableField("file_name")
    private String fileName;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("uploaded_by")
    private String uploadedBy;

    @TableField("uploaded_at")
    private Instant uploadedAt;
}
