package dev.autotix.application.channel;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import org.springframework.stereotype.Service;

/**
 * Disconnect a channel.
 * - Clears credential, sets enabled=false (soft disable; preserves history)
 * - hardDelete: currently same as soft (deferred to v2 — deleting a channel
 *   requires cascading ticket cleanup decisions)
 */
@Service
public class DisconnectChannelUseCase {

    private final ChannelRepository channelRepository;

    public DisconnectChannelUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    public void disconnect(ChannelId channelId, boolean hardDelete) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + channelId.value()));

        channel.disconnect(); // clears credential, sets enabled=false
        channelRepository.save(channel);

        // hardDelete: deferred to v2 — cascade delete of tickets is a significant operation
        // For now, soft-disable is equivalent to hard delete from the user's perspective
    }
}
