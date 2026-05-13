package dev.autotix.infrastructure.persistence.automation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.domain.automation.AutomationRule.Condition;
import dev.autotix.domain.automation.AutomationRule.RuleAction;
import dev.autotix.domain.automation.AutomationRuleRepository;
import dev.autotix.infrastructure.persistence.automation.mapper.AutomationRuleMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements AutomationRuleRepository port using MyBatis Plus.
 * conditions_json and actions_json are serialized/deserialized via Fastjson.
 */
@Repository
public class AutomationRuleRepositoryImpl implements AutomationRuleRepository {

    private final AutomationRuleMapper mapper;

    public AutomationRuleRepositoryImpl(AutomationRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(AutomationRule rule) {
        AutomationRuleEntity entity = toEntity(rule);
        if (rule.id() == null) {
            entity.setId(null);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            mapper.insert(entity);
            rule.setId(String.valueOf(entity.getId()));
        } else {
            entity.setUpdatedAt(Instant.now());
            mapper.updateById(entity);
        }
        return rule.id();
    }

    @Override
    public Optional<AutomationRule> findById(String id) {
        AutomationRuleEntity entity = mapper.selectById(Long.parseLong(id));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<AutomationRule> findAllEnabled() {
        QueryWrapper<AutomationRuleEntity> qw = new QueryWrapper<>();
        qw.eq("enabled", true).orderByAsc("priority");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<AutomationRule> findAll() {
        QueryWrapper<AutomationRuleEntity> qw = new QueryWrapper<>();
        qw.orderByAsc("priority");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(Long.parseLong(id));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private AutomationRuleEntity toEntity(AutomationRule rule) {
        AutomationRuleEntity e = new AutomationRuleEntity();
        if (rule.id() != null) {
            e.setId(Long.parseLong(rule.id()));
        }
        e.setName(rule.name());
        e.setPriority(rule.priority());
        e.setEnabled(rule.isEnabled());
        e.setConditionsJson(JSON.toJSONString(rule.conditions() != null ? rule.conditions() : Collections.emptyList()));
        e.setActionsJson(JSON.toJSONString(rule.actions() != null ? rule.actions() : Collections.emptyList()));
        return e;
    }

    private AutomationRule toDomain(AutomationRuleEntity e) {
        List<Condition> conditions = JSON.parseObject(
                e.getConditionsJson() != null ? e.getConditionsJson() : "[]",
                new TypeReference<List<Condition>>() {});
        List<RuleAction> actions = JSON.parseObject(
                e.getActionsJson() != null ? e.getActionsJson() : "[]",
                new TypeReference<List<RuleAction>>() {});

        AutomationRule rule = AutomationRule.create(
                e.getName(),
                e.getPriority() != null ? e.getPriority() : 100,
                e.getEnabled() != null && e.getEnabled(),
                conditions,
                actions);
        rule.setId(String.valueOf(e.getId()));
        return rule;
    }
}
