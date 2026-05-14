package dev.autotix.application.tag;

import dev.autotix.domain.tag.TagDefinition;
import dev.autotix.domain.tag.TagDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Use case: list all tag definitions.
 */
@Service
public class ListTagsUseCase {

    private final TagDefinitionRepository tagDefinitionRepository;

    public ListTagsUseCase(TagDefinitionRepository tagDefinitionRepository) {
        this.tagDefinitionRepository = tagDefinitionRepository;
    }

    public List<TagDefinition> list() {
        return tagDefinitionRepository.findAll();
    }
}
