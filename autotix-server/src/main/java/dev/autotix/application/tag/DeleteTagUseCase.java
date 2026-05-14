package dev.autotix.application.tag;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.tag.TagDefinitionRepository;
import org.springframework.stereotype.Service;

/**
 * Use case: delete a tag definition by id.
 * Deleting a tag definition does NOT strip the tag from existing tickets (tag_csv is free text).
 */
@Service
public class DeleteTagUseCase {

    private final TagDefinitionRepository tagDefinitionRepository;

    public DeleteTagUseCase(TagDefinitionRepository tagDefinitionRepository) {
        this.tagDefinitionRepository = tagDefinitionRepository;
    }

    public void delete(Long id) {
        tagDefinitionRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Tag not found: " + id));
        tagDefinitionRepository.delete(id);
    }
}
