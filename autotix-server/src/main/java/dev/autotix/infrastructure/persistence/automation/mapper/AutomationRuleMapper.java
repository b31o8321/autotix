package dev.autotix.infrastructure.persistence.automation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.autotix.infrastructure.persistence.automation.AutomationRuleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis Plus mapper for the automation_rule table.
 */
@Mapper
public interface AutomationRuleMapper extends BaseMapper<AutomationRuleEntity> {
}
