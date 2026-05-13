package dev.autotix.infrastructure.persistence.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * TODO: MyBatis Plus entity for Channel.
 *  Credentials encrypted at rest — use Jasypt or a TypeHandler that wraps encrypt/decrypt.
 *  Schema indexes:
 *    - UNIQUE (platform, webhook_token)
 */
@Data
@TableName("channel")
public class ChannelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** PlatformType.name() */
    private String platform;

    /** ChannelType.name(): EMAIL / CHAT / VOICE */
    @TableField("channel_type")
    private String channelType;

    @TableField("display_name")
    private String displayName;

    @TableField("webhook_token")
    private String webhookToken;

    /** TODO: encrypt at rest via TypeHandler */
    @TableField("access_token")
    private String accessToken;

    /** TODO: encrypt at rest */
    @TableField("refresh_token")
    private String refreshToken;

    @TableField("expires_at")
    private Instant expiresAt;

    /** Jackson-serialized Map&lt;String,String&gt; for platform-specific extras */
    @TableField("attributes_json")
    private String attributesJson;

    private Boolean enabled;

    @TableField("auto_reply_enabled")
    private Boolean autoReplyEnabled;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
