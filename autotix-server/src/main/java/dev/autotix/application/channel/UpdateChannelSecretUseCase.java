package dev.autotix.application.channel;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Update the per-channel webhook signing secret stored in credential attributes.
 *
 * <p>This is intentionally a thin use case: load → mutate credential → reconnect → save.
 * No health-check is performed because the secret is outbound-direction only.
 */
@Service
public class UpdateChannelSecretUseCase {

    private final ChannelRepository channelRepository;

    public UpdateChannelSecretUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /**
     * Set or replace the {@code webhook_secret} attribute on the channel credential.
     *
     * @param id     the channel to update
     * @param secret the new signing secret; null or blank clears it (disables verification)
     */
    public void updateSecret(ChannelId id, String secret) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + id.value()));

        ChannelCredential old = channel.credential();

        // Build a new attributes map; preserve existing entries, add/replace webhook_secret
        Map<String, String> attrs = new HashMap<String, String>();
        if (old != null && old.attributes() != null) {
            attrs.putAll(old.attributes());
        }
        if (secret == null || secret.trim().isEmpty()) {
            attrs.remove("webhook_secret");
        } else {
            attrs.put("webhook_secret", secret.trim());
        }

        String accessToken   = old != null ? old.accessToken()   : null;
        String refreshToken  = old != null ? old.refreshToken()  : null;
        java.time.Instant expiresAt = old != null ? old.expiresAt() : null;

        ChannelCredential updated = new ChannelCredential(accessToken, refreshToken, expiresAt, attrs);
        channel.connect(updated);
        channelRepository.save(channel);
    }
}
