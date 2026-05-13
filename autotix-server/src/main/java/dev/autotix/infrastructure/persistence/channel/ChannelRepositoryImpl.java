package dev.autotix.infrastructure.persistence.channel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.infrastructure.persistence.channel.mapper.ChannelMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements ChannelRepository port using MyBatis Plus.
 * Credentials are stored as plain-text in this slice; encryption is a TODO for prod.
 */
@Repository
public class ChannelRepositoryImpl implements ChannelRepository {

    private final ChannelMapper channelMapper;

    public ChannelRepositoryImpl(ChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    @Override
    public ChannelId save(Channel channel) {
        ChannelEntity entity = toEntity(channel);
        if (channel.id() == null) {
            entity.setId(null);
            channelMapper.insert(entity);
            ChannelId newId = new ChannelId(String.valueOf(entity.getId()));
            channel.assignPersistedId(newId);
            return newId;
        } else {
            channelMapper.updateById(entity);
            return channel.id();
        }
    }

    @Override
    public Optional<Channel> findById(ChannelId id) {
        ChannelEntity entity = channelMapper.selectById(Long.parseLong(id.value()));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<Channel> findByWebhookToken(PlatformType platform, String webhookToken) {
        QueryWrapper<ChannelEntity> qw = new QueryWrapper<>();
        qw.eq("platform", platform.name())
          .eq("webhook_token", webhookToken)
          .eq("enabled", true);
        ChannelEntity entity = channelMapper.selectOne(qw);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<Channel> findAll() {
        // Include all channels (enabled and disabled) — admin needs to see them all
        return channelMapper.selectList(null)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(ChannelId id) {
        // Soft delete: set enabled=false and stamp updatedAt
        ChannelEntity entity = new ChannelEntity();
        entity.setId(Long.parseLong(id.value()));
        entity.setEnabled(false);
        entity.setUpdatedAt(Instant.now());
        channelMapper.updateById(entity);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private ChannelEntity toEntity(Channel c) {
        ChannelEntity e = new ChannelEntity();
        if (c.id() != null) {
            e.setId(Long.parseLong(c.id().value()));
        }
        e.setPlatform(c.platform().name());
        e.setChannelType(c.type().name());
        e.setDisplayName(c.displayName());
        e.setWebhookToken(c.webhookToken());
        e.setEnabled(c.isEnabled());
        e.setAutoReplyEnabled(c.isAutoReplyEnabled());
        e.setCreatedAt(c.createdAt());
        e.setUpdatedAt(c.updatedAt());

        ChannelCredential cred = c.credential();
        if (cred != null) {
            e.setAccessToken(cred.accessToken());
            e.setRefreshToken(cred.refreshToken());
            e.setExpiresAt(cred.expiresAt());
            if (cred.attributes() != null && !cred.attributes().isEmpty()) {
                e.setAttributesJson(JSON.toJSONString(cred.attributes()));
            } else {
                e.setAttributesJson(null);
            }
        } else {
            e.setAccessToken(null);
            e.setRefreshToken(null);
            e.setExpiresAt(null);
            e.setAttributesJson(null);
        }

        return e;
    }

    private Channel toDomain(ChannelEntity e) {
        ChannelCredential credential = null;
        if (e.getAccessToken() != null || e.getAttributesJson() != null) {
            Map<String, String> attributes = null;
            if (e.getAttributesJson() != null && !e.getAttributesJson().trim().isEmpty()) {
                attributes = JSON.parseObject(e.getAttributesJson(),
                        new TypeReference<Map<String, String>>() {});
            }
            credential = new ChannelCredential(
                    e.getAccessToken(),
                    e.getRefreshToken(),
                    e.getExpiresAt(),
                    attributes
            );
        }

        return Channel.rehydrate(
                new ChannelId(String.valueOf(e.getId())),
                PlatformType.valueOf(e.getPlatform()),
                ChannelType.valueOf(e.getChannelType()),
                e.getDisplayName(),
                e.getWebhookToken(),
                credential,
                Boolean.TRUE.equals(e.getEnabled()),
                Boolean.TRUE.equals(e.getAutoReplyEnabled()),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
