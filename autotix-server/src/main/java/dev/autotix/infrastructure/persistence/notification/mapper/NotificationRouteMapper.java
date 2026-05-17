package dev.autotix.infrastructure.persistence.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.autotix.infrastructure.persistence.notification.NotificationRouteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis Plus mapper for the notification_route table.
 */
@Mapper
public interface NotificationRouteMapper extends BaseMapper<NotificationRouteEntity> {
}
