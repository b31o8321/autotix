package dev.autotix.infrastructure.persistence.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.autotix.infrastructure.persistence.ticket.MessageEntity;

/**
 * TODO: MyBatis Plus mapper for MessageEntity.
 */
public interface MessageMapper extends BaseMapper<MessageEntity> {
    // TODO: findByTicketIdOrderByOccurredAtAsc via QueryWrapper in repository impl
}
