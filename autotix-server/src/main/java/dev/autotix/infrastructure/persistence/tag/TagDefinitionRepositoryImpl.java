package dev.autotix.infrastructure.persistence.tag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.tag.TagDefinition;
import dev.autotix.domain.tag.TagDefinitionRepository;
import dev.autotix.infrastructure.persistence.tag.mapper.TagDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements TagDefinitionRepository using MyBatis Plus.
 */
@Repository
public class TagDefinitionRepositoryImpl implements TagDefinitionRepository {

    private final TagDefinitionMapper mapper;

    public TagDefinitionRepositoryImpl(TagDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long save(TagDefinition tag) {
        TagDefinitionEntity entity = toEntity(tag);
        if (tag.id() == null) {
            entity.setId(null);
            entity.setCreatedAt(Instant.now());
            mapper.insert(entity);
            tag.assignPersistedId(entity.getId());
        } else {
            mapper.updateById(entity);
        }
        return tag.id();
    }

    @Override
    public Optional<TagDefinition> findByName(String name) {
        QueryWrapper<TagDefinitionEntity> qw = new QueryWrapper<>();
        qw.eq("name", name).last("LIMIT 1");
        TagDefinitionEntity entity = mapper.selectOne(qw);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<TagDefinition> findAll() {
        return mapper.selectList(null).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public TagDefinition upsertByName(String name, String color) {
        Optional<TagDefinition> existing = findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        TagDefinition newTag = TagDefinition.create(name, color, null);
        save(newTag);
        return newTag;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private TagDefinitionEntity toEntity(TagDefinition t) {
        TagDefinitionEntity e = new TagDefinitionEntity();
        if (t.id() != null) {
            e.setId(t.id());
        }
        e.setName(t.name());
        e.setColor(t.color());
        e.setCategory(t.category());
        e.setCreatedAt(t.createdAt());
        return e;
    }

    private TagDefinition toDomain(TagDefinitionEntity e) {
        return TagDefinition.rehydrate(e.getId(), e.getName(), e.getColor(),
                e.getCategory(), e.getCreatedAt());
    }
}
