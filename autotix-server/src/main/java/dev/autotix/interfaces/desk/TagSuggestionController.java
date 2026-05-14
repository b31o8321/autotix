package dev.autotix.interfaces.desk;

import dev.autotix.application.tag.ListTagsUseCase;
import dev.autotix.domain.tag.TagDefinition;
import dev.autotix.interfaces.admin.dto.TagDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public (any authenticated agent) endpoint for tag type-ahead suggestions.
 *
 * GET /api/desk/tags/suggestions — returns all tag definitions for autocomplete
 */
@RestController
@RequestMapping("/api/desk/tags")
public class TagSuggestionController {

    private final ListTagsUseCase listTags;

    public TagSuggestionController(ListTagsUseCase listTags) {
        this.listTags = listTags;
    }

    @GetMapping("/suggestions")
    public List<TagDTO> suggestions() {
        return listTags.list().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private TagDTO toDTO(TagDefinition t) {
        TagDTO dto = new TagDTO();
        dto.id = t.id();
        dto.name = t.name();
        dto.color = t.color();
        dto.category = t.category();
        dto.createdAt = t.createdAt();
        return dto;
    }
}
