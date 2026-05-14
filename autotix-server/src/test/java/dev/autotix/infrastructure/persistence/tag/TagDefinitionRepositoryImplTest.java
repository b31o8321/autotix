package dev.autotix.infrastructure.persistence.tag;

import dev.autotix.domain.tag.TagDefinition;
import dev.autotix.domain.tag.TagDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TagDefinitionRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class TagDefinitionRepositoryImplTest {

    @Autowired
    private TagDefinitionRepository tagRepository;

    @Test
    void upsertByName_createsOnFirstCall_returnsSameIdOnSecondCall() {
        String name = "billing-" + System.nanoTime();

        TagDefinition first = tagRepository.upsertByName(name, "#FF0000");
        assertNotNull(first.id());

        TagDefinition second = tagRepository.upsertByName(name, "#00FF00"); // different color
        assertNotNull(second.id());

        // Same id must be returned
        assertEquals(first.id(), second.id(),
                "upsertByName should return same tag on second call");
    }

    @Test
    void findAll_returnsAllRows() {
        String name1 = "tag-all-1-" + System.nanoTime();
        String name2 = "tag-all-2-" + System.nanoTime();
        tagRepository.upsertByName(name1, "#111111");
        tagRepository.upsertByName(name2, "#222222");

        List<TagDefinition> all = tagRepository.findAll();
        assertTrue(all.size() >= 2, "findAll should return at least the 2 tags we created");

        boolean foundFirst = all.stream().anyMatch(t -> t.name().equals(name1));
        boolean foundSecond = all.stream().anyMatch(t -> t.name().equals(name2));
        assertTrue(foundFirst, "Should find first tag");
        assertTrue(foundSecond, "Should find second tag");
    }

    @Test
    void save_and_findByName_roundTrip() {
        String name = "save-test-" + System.nanoTime();
        TagDefinition tag = TagDefinition.create(name, "#AABBCC", "test-category");
        Long id = tagRepository.save(tag);
        assertNotNull(id);

        Optional<TagDefinition> found = tagRepository.findByName(name);
        assertTrue(found.isPresent());
        assertEquals("#AABBCC", found.get().color());
        assertEquals("test-category", found.get().category());
    }
}
