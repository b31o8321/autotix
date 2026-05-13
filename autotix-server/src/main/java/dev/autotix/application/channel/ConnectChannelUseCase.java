package dev.autotix.application.channel;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * TODO: Connect a new third-party platform integration.
 *
 *  Two-step OAuth flow:
 *    - startOAuth(): build authorize URL, return for redirect
 *    - completeOAuth(code, state): exchange code, persist Channel with credentials
 *
 *  For non-OAuth (API key): use connectWithApiKey().
 */
@Service
public class ConnectChannelUseCase {

    // TODO: inject ChannelRepository, PluginRegistry
    public ConnectChannelUseCase() {}

    public String startOAuth(PlatformType platform, ChannelType type, String displayName) {
        // TODO: ask Plugin for authorize URL; persist a pending Channel with a state token
        throw new UnsupportedOperationException("TODO");
    }

    public ChannelId completeOAuth(String state, String code) {
        // TODO: lookup pending Channel by state, exchange code for token via Plugin, persist credentials
        throw new UnsupportedOperationException("TODO");
    }

    public ChannelId connectWithApiKey(PlatformType platform, ChannelType type,
                                       String displayName, Map<String, String> credentials) {
        // TODO: validate credentials by calling Plugin.healthCheck; persist Channel
        throw new UnsupportedOperationException("TODO");
    }
}
