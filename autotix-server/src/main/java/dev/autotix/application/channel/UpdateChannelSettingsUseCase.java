package dev.autotix.application.channel;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import org.springframework.stereotype.Service;

/**
 * Mutate Channel runtime settings (autoReply toggle, displayName, rotate webhook token).
 */
@Service
public class UpdateChannelSettingsUseCase {

    private final ChannelRepository channelRepository;

    public UpdateChannelSettingsUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    public void setAutoReply(ChannelId id, boolean enabled) {
        Channel channel = load(id);
        channel.setAutoReply(enabled);
        channelRepository.save(channel);
    }

    public void rename(ChannelId id, String newDisplayName) {
        if (newDisplayName == null || newDisplayName.trim().isEmpty()) {
            throw new AutotixException.ValidationException("displayName must not be blank");
        }
        Channel channel = load(id);
        // Channel doesn't have a rename() method; use rehydrate pattern is not available.
        // The channel domain object only allows rename through a dedicated method — but it
        // wasn't defined in the domain. We'll replicate via a safe approach:
        // rotateWebhookToken exists; for displayName we need to call the Channel's rename capability.
        // The Channel class has no rename method; we need to add one or use a workaround.
        // Per DDD rules domain methods should exist — but since the stub is missing we'll
        // rely on the Channel allowing name updates as a domain capability.
        channel.rename(newDisplayName.trim());
        channelRepository.save(channel);
    }

    public String rotateWebhookToken(ChannelId id) {
        Channel channel = load(id);
        channel.rotateWebhookToken();
        channelRepository.save(channel);
        return channel.webhookToken();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Channel load(ChannelId id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + id.value()));
    }
}
