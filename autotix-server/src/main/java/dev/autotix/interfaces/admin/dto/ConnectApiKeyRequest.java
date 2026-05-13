package dev.autotix.interfaces.admin.dto;

import java.util.Map;

/**
 * TODO: Payload to connect a channel via API key / shared secret (non-OAuth platforms).
 *  credentials is a free-form map (apiKey / corpId / corpSecret / etc.).
 */
public class ConnectApiKeyRequest {
    public String platform;
    public String channelType;
    public String displayName;
    public Map<String, String> credentials;
}
