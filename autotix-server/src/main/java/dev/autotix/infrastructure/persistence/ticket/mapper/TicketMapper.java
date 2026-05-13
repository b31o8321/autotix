package dev.autotix.infrastructure.persistence.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.autotix.infrastructure.persistence.ticket.TicketEntity;

/**
 * TODO: MyBatis Plus mapper for TicketEntity.
 *  Custom queries (search by status/channel/assignee with pagination) via QueryWrapper
 *  in TicketRepositoryImpl, OR via @Select XML in this file.
 */
public interface TicketMapper extends BaseMapper<TicketEntity> {
    // TODO: add custom paginated search query here if QueryWrapper is insufficient
}
