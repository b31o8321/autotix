package dev.autotix.domain.channel;

import java.util.List;
import java.util.Optional;

/**
 * TODO: Repository port for Channel aggregate.
 */
public interface ChannelRepository {

    /** TODO: persist (insert or update) */
    ChannelId save(Channel channel);

    /** TODO: find by internal id */
    Optional<Channel> findById(ChannelId id);

    /** TODO: webhook entry uses this — find by platform + webhookToken; must be enabled */
    Optional<Channel> findByWebhookToken(PlatformType platform, String webhookToken);

    /** TODO: list all (for Settings page); only return non-deleted */
    List<Channel> findAll();

    /** Find all channels for a specific platform (non-deleted). */
    List<Channel> findByPlatform(PlatformType platform);

    /** TODO: soft delete */
    void delete(ChannelId id);
}
