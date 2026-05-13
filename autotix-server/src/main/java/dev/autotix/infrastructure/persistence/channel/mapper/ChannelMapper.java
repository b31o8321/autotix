package dev.autotix.infrastructure.persistence.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.autotix.infrastructure.persistence.channel.ChannelEntity;

public interface ChannelMapper extends BaseMapper<ChannelEntity> {
    // TODO: findByPlatformAndWebhookToken via QueryWrapper in repository impl
}
