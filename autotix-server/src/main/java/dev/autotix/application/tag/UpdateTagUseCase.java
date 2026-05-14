package dev.autotix.application.tag;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.tag.TagDefinition;
import dev.autotix.domain.tag.TagDefinitionRepository;
import org.springframework.stereotype.Service;

/**
 * Use case: update color and category of a tag definition. Name is immutable.
 */
@Service
public class UpdateTagUseCase {

    private final TagDefinitionRepository tagDefinitionRepository;

    public UpdateTagUseCase(TagDefinitionRepository tagDefinitionRepository) {
        this.tagDefinitionRepository = tagDefinitionRepository;
    }

    public TagDefinition update(Long id, String color, String category) {
        TagDefinition tag = tagDefinitionRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Tag not found: " + id));
        tag.updateColorAndCategory(color, category);
        tagDefinitionRepository.save(tag);
        return tag;
    }
}
