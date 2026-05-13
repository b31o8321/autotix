package dev.autotix.application.channel;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.infrastructure.platform.PluginRegistry;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Connect a new third-party platform integration.
 *
 * OAuth flow (startOAuth / completeOAuth): deferred to v2 — each platform needs
 * different redirect URIs and client credentials that require additional setup.
 *
 * For non-OAuth (API key / shared secret): use connectWithApiKey().
 */
@Service
public class ConnectChannelUseCase {

    private final ChannelRepository channelRepository;
    private final PluginRegistry pluginRegistry;

    public ConnectChannelUseCase(ChannelRepository channelRepository,
                                 PluginRegistry pluginRegistry) {
        this.channelRepository = channelRepository;
        this.pluginRegistry = pluginRegistry;
    }

    public String startOAuth(PlatformType platform, ChannelType type, String displayName) {
        // OAuth flow deferred to v2 — requires per-platform redirect URI & client credentials
        throw new UnsupportedOperationException("OAuth flow deferred to v2");
    }

    public ChannelId completeOAuth(String state, String code) {
        // OAuth flow deferred to v2
        throw new UnsupportedOperationException("OAuth flow deferred to v2");
    }

    public ChannelId connectWithApiKey(PlatformType platform, ChannelType type,
                                       String displayName, Map<String, String> credentials) {
        // Build channel with generated webhookToken
        Channel channel = Channel.newInstance(platform, type, displayName);

        // Build credential from map
        String accessToken = credentials.get("access_token");
        Map<String, String> attrs = new HashMap<>(credentials);
        if (accessToken != null) {
            attrs.remove("access_token");
        }
        ChannelCredential credential = new ChannelCredential(accessToken, null, null, attrs);

        // Validate credentials via plugin health check
        boolean healthy = pluginRegistry.get(platform).healthCheck(credential);
        if (!healthy) {
            throw new AutotixException.ValidationException(
                    "Invalid credentials for platform: " + platform);
        }

        // Connect and persist
        channel.connect(credential);
        ChannelId id = channelRepository.save(channel);
        return id;
    }
}
