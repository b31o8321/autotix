package dev.autotix.infrastructure.persistence.ai;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MyBatis Plus entity for the singleton ai_config row (id always = 1).
 */
@Data
@TableName("ai_config")
public class AIConfigEntity {

    /** Fixed at 1 — singleton row. */
    @TableId
    private Long id;

    private String endpoint;

    @TableField("api_key")
    private String apiKey;

    private String model;

    @TableField("system_prompt")
    private String systemPrompt;

    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    @TableField("max_retries")
    private Integer maxRetries;

    @TableField("global_auto_reply_enabled")
    private Boolean globalAutoReplyEnabled;

    @TableField("updated_at")
    private Instant updatedAt;
}
