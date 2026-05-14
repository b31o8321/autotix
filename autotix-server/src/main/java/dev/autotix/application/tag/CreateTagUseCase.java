package dev.autotix.application.tag;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.tag.TagDefinition;
import dev.autotix.domain.tag.TagDefinitionRepository;
import org.springframework.stereotype.Service;

/**
 * Use case: create a new tag definition. Throws ConflictException if name already exists.
 */
@Service
public class CreateTagUseCase {

    private final TagDefinitionRepository tagDefinitionRepository;

    public CreateTagUseCase(TagDefinitionRepository tagDefinitionRepository) {
        this.tagDefinitionRepository = tagDefinitionRepository;
    }

    public TagDefinition create(String name, String color, String category) {
        if (name == null || name.trim().isEmpty()) {
            throw new AutotixException.ValidationException("Tag name must not be blank");
        }
        if (tagDefinitionRepository.findByName(name.trim()).isPresent()) {
            throw new AutotixException.ConflictException("Tag with name '" + name.trim() + "' already exists");
        }
        TagDefinition tag = TagDefinition.create(name.trim(), color, category);
        tagDefinitionRepository.save(tag);
        return tag;
    }
}
