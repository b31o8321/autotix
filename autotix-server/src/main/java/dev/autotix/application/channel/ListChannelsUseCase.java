package dev.autotix.application.channel;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * List all channels for Settings page.
 */
@Service
public class ListChannelsUseCase {

    private final ChannelRepository channelRepository;

    public ListChannelsUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    public List<Channel> list() {
        return channelRepository.findAll();
    }

    /** Returns channels filtered to a specific platform. */
    public List<Channel> listByPlatform(PlatformType platform) {
        return channelRepository.findByPlatform(platform);
    }
}
