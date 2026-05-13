package dev.autotix.infrastructure.persistence.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.autotix.infrastructure.persistence.user.UserEntity;

public interface UserMapper extends BaseMapper<UserEntity> {
    // TODO: countByRole via QueryWrapper in repository impl
}
