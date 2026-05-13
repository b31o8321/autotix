package dev.autotix.application.channel;

import dev.autotix.domain.channel.ChannelId;
import org.springframework.stereotype.Service;

/**
 * TODO: Mutate Channel runtime settings (autoReply toggle, displayName, rotate webhook token).
 */
@Service
public class UpdateChannelSettingsUseCase {

    public UpdateChannelSettingsUseCase() {}

    public void setAutoReply(ChannelId id, boolean enabled) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }

    public void rename(ChannelId id, String newDisplayName) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }

    public String rotateWebhookToken(ChannelId id) {
        // TODO: implement; return new token
        throw new UnsupportedOperationException("TODO");
    }
}
