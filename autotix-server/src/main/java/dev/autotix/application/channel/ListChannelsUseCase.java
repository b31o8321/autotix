package dev.autotix.application.channel;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
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
}
