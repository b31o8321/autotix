package dev.autotix.infrastructure.persistence.customfield;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.customfield.CustomFieldDefinition;
import dev.autotix.domain.customfield.CustomFieldDefinition.AppliesTo;
import dev.autotix.domain.customfield.CustomFieldDefinitionRepository;
import dev.autotix.domain.customfield.CustomFieldType;
import dev.autotix.infrastructure.persistence.customfield.mapper.CustomFieldDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements CustomFieldDefinitionRepository using MyBatis Plus.
 */
@Repository
public class CustomFieldDefinitionRepositoryImpl implements CustomFieldDefinitionRepository {

    private final CustomFieldDefinitionMapper mapper;

    public CustomFieldDefinitionRepositoryImpl(CustomFieldDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long save(CustomFieldDefinition field) {
        CustomFieldDefinitionEntity entity = toEntity(field);
        if (field.id() == null) {
            entity.setId(null);
            entity.setCreatedAt(Instant.now());
            mapper.insert(entity);
            field.assignPersistedId(entity.getId());
        } else {
            mapper.updateById(entity);
        }
        return field.id();
    }

    @Override
    public Optional<CustomFieldDefinition> findByKey(String key) {
        QueryWrapper<CustomFieldDefinitionEntity> qw = new QueryWrapper<>();
        qw.eq("field_key", key).last("LIMIT 1");
        CustomFieldDefinitionEntity entity = mapper.selectOne(qw);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<CustomFieldDefinition> findAllByAppliesTo(AppliesTo appliesTo) {
        QueryWrapper<CustomFieldDefinitionEntity> qw = new QueryWrapper<>();
        qw.eq("applies_to", appliesTo.name()).orderByAsc("display_order");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private CustomFieldDefinitionEntity toEntity(CustomFieldDefinition f) {
        CustomFieldDefinitionEntity e = new CustomFieldDefinitionEntity();
        if (f.id() != null) {
            e.setId(f.id());
        }
        e.setName(f.name());
        e.setFieldKey(f.key());
        e.setFieldType(f.type().name());
        e.setAppliesTo(f.appliesTo().name());
        e.setRequired(f.required());
        e.setDisplayOrder(f.displayOrder());
        e.setCreatedAt(f.createdAt());
        return e;
    }

    private CustomFieldDefinition toDomain(CustomFieldDefinitionEntity e) {
        return CustomFieldDefinition.rehydrate(
                e.getId(),
                e.getName(),
                e.getFieldKey(),
                CustomFieldType.valueOf(e.getFieldType()),
                AppliesTo.valueOf(e.getAppliesTo()),
                Boolean.TRUE.equals(e.getRequired()),
                e.getDisplayOrder() != null ? e.getDisplayOrder() : 100,
                e.getCreatedAt());
    }
}
