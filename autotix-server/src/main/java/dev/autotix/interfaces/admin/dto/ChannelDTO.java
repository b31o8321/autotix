package dev.autotix.interfaces.admin.dto;

import java.time.Instant;

/**
 * TODO: REST DTO for Channel (admin listing).
 */
public class ChannelDTO {
    public String id;
    public String platform;
    public String channelType;       // EMAIL / CHAT
    public String displayName;
    public String webhookToken;      // shown in UI for user to paste into platform's webhook config
    public boolean enabled;
    public boolean autoReplyEnabled;
    public Instant connectedAt;
}
