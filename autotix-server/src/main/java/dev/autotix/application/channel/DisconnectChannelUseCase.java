package dev.autotix.application.channel;

import dev.autotix.domain.channel.ChannelId;
import org.springframework.stereotype.Service;

/**
 * TODO: Disconnect a channel.
 *  - Revoke OAuth token if Plugin supports
 *  - Set local Channel.enabled = false (soft disable; preserves history)
 *  - Optionally hard delete (admin choice)
 */
@Service
public class DisconnectChannelUseCase {

    public DisconnectChannelUseCase() {}

    public void disconnect(ChannelId channelId, boolean hardDelete) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }
}
