package dev.autotix.infrastructure.persistence.customfield;

import dev.autotix.domain.customfield.CustomFieldDefinition;
import dev.autotix.domain.customfield.CustomFieldDefinition.AppliesTo;
import dev.autotix.domain.customfield.CustomFieldDefinitionRepository;
import dev.autotix.domain.customfield.CustomFieldType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CustomFieldDefinitionRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class CustomFieldDefinitionRepositoryImplTest {

    @Autowired
    private CustomFieldDefinitionRepository fieldRepository;

    private String uniqueKey() {
        return "field_" + System.nanoTime();
    }

    @Test
    void save_and_findByKey_roundTrip() {
        String key = uniqueKey();
        CustomFieldDefinition field = CustomFieldDefinition.create(
                "Order Number", key, CustomFieldType.TEXT, AppliesTo.TICKET, false, 10);

        Long id = fieldRepository.save(field);
        assertNotNull(id);

        Optional<CustomFieldDefinition> found = fieldRepository.findByKey(key);
        assertTrue(found.isPresent());
        assertEquals("Order Number", found.get().name());
        assertEquals(key, found.get().key());
        assertEquals(CustomFieldType.TEXT, found.get().type());
        assertEquals(AppliesTo.TICKET, found.get().appliesTo());
        assertFalse(found.get().required());
        assertEquals(10, found.get().displayOrder());
    }

    @Test
    void findAllByAppliesTo_filterAndOrderByDisplayOrder() {
        // Create multiple TICKET fields with different display orders
        String key1 = uniqueKey();
        String key2 = uniqueKey();
        String key3 = uniqueKey();

        fieldRepository.save(CustomFieldDefinition.create(
                "Field Z", key1, CustomFieldType.NUMBER, AppliesTo.TICKET, false, 30));
        fieldRepository.save(CustomFieldDefinition.create(
                "Field A", key2, CustomFieldType.TEXT, AppliesTo.TICKET, true, 5));
        fieldRepository.save(CustomFieldDefinition.create(
                "Customer Field", key3, CustomFieldType.DATE, AppliesTo.CUSTOMER, false, 1));

        List<CustomFieldDefinition> ticketFields =
                fieldRepository.findAllByAppliesTo(AppliesTo.TICKET);

        // All returned should be TICKET type
        for (CustomFieldDefinition f : ticketFields) {
            assertEquals(AppliesTo.TICKET, f.appliesTo());
        }

        // Verify our two TICKET fields are in the result and ordered by displayOrder
        List<CustomFieldDefinition> ours = ticketFields.stream()
                .filter(f -> f.key().equals(key1) || f.key().equals(key2))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(2, ours.size());
        // key2 has displayOrder=5, key1 has displayOrder=30 — key2 should come first
        assertEquals(key2, ours.get(0).key());
        assertEquals(key1, ours.get(1).key());
    }
}
