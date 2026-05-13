package dev.autotix.interfaces.admin.dto;

/**
 * TODO: Payload to start OAuth — frontend sends platform + desired channelType + displayName.
 */
public class OAuthStartRequest {
    public String platform;          // PlatformType.name()
    public String channelType;       // EMAIL / CHAT (some platforms have multiple flavors)
    public String displayName;
}
