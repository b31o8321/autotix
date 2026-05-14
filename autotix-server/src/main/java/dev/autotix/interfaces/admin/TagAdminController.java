package dev.autotix.interfaces.admin;

import dev.autotix.application.tag.CreateTagUseCase;
import dev.autotix.application.tag.DeleteTagUseCase;
import dev.autotix.application.tag.ListTagsUseCase;
import dev.autotix.application.tag.UpdateTagUseCase;
import dev.autotix.domain.tag.TagDefinition;
import dev.autotix.interfaces.admin.dto.TagDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin REST endpoints for tag definition management.
 *
 * GET    /api/admin/tags         — list all tags
 * POST   /api/admin/tags         — create tag (409 if name conflict)
 * PUT    /api/admin/tags/{id}    — update color/category
 * DELETE /api/admin/tags/{id}    — delete
 */
@RestController
@RequestMapping("/api/admin/tags")
@PreAuthorize("hasRole('ADMIN')")
public class TagAdminController {

    private final ListTagsUseCase listTags;
    private final CreateTagUseCase createTag;
    private final UpdateTagUseCase updateTag;
    private final DeleteTagUseCase deleteTag;

    public TagAdminController(ListTagsUseCase listTags,
                              CreateTagUseCase createTag,
                              UpdateTagUseCase updateTag,
                              DeleteTagUseCase deleteTag) {
        this.listTags = listTags;
        this.createTag = createTag;
        this.updateTag = updateTag;
        this.deleteTag = deleteTag;
    }

    @GetMapping
    public List<TagDTO> list() {
        return listTags.list().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public TagDTO create(@RequestBody TagDTO dto) {
        TagDefinition tag = createTag.create(dto.name, dto.color, dto.category);
        return toDTO(tag);
    }

    @PutMapping("/{id}")
    public TagDTO update(@PathVariable Long id, @RequestBody TagDTO dto) {
        TagDefinition tag = updateTag.update(id, dto.color, dto.category);
        return toDTO(tag);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteTag.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

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
