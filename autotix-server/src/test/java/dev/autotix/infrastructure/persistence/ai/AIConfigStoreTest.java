package dev.autotix.infrastructure.persistence.ai;

import dev.autotix.infrastructure.ai.AIConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AIConfigStore using the H2 in-memory test profile.
 */
@SpringBootTest
@ActiveProfiles("test")
class AIConfigStoreTest {

    @Autowired
    AIConfigStore aiConfigStore;

    @Autowired
    AIConfig aiConfig;

    @Test
    void saveAndLoad_roundTrip() {
        // Arrange: build a snapshot with known values
        AIConfig snapshot = new AIConfig();
        snapshot.setEndpoint("https://api.example.com/v1");
        snapshot.setApiKey("sk-roundtrip-key-1234");
        snapshot.setModel("gpt-4o");
        snapshot.setSystemPrompt("You are helpful.");
        snapshot.setTimeoutSeconds(45);
        snapshot.setMaxRetries(3);

        // Act
        aiConfigStore.save(snapshot);
        Optional<AIConfigEntity> loaded = aiConfigStore.load();

        // Assert
        assertTrue(loaded.isPresent(), "Should have a persisted row after save");
        AIConfigEntity e = loaded.get();
        assertEquals("https://api.example.com/v1", e.getEndpoint());
        assertEquals("sk-roundtrip-key-1234", e.getApiKey());
        assertEquals("gpt-4o", e.getModel());
        assertEquals("You are helpful.", e.getSystemPrompt());
        assertEquals(45, e.getTimeoutSeconds());
        assertEquals(3, e.getMaxRetries());
        assertNotNull(e.getUpdatedAt());
    }

    @Test
    void save_twice_secondOverwritesFirst() {
        // First save
        AIConfig first = new AIConfig();
        first.setEndpoint("https://first.example.com");
        first.setApiKey("key-first");
        first.setModel("model-v1");
        first.setSystemPrompt("First prompt");
        first.setTimeoutSeconds(10);
        first.setMaxRetries(1);
        aiConfigStore.save(first);

        // Second save with different values
        AIConfig second = new AIConfig();
        second.setEndpoint("https://second.example.com");
        second.setApiKey("key-second");
        second.setModel("model-v2");
        second.setSystemPrompt("Second prompt");
        second.setTimeoutSeconds(20);
        second.setMaxRetries(5);
        aiConfigStore.save(second);

        // Should reflect second values
        Optional<AIConfigEntity> loaded = aiConfigStore.load();
        assertTrue(loaded.isPresent());
        AIConfigEntity e = loaded.get();
        assertEquals("https://second.example.com", e.getEndpoint());
        assertEquals("key-second", e.getApiKey());
        assertEquals("model-v2", e.getModel());
        assertEquals("Second prompt", e.getSystemPrompt());
        assertEquals(20, e.getTimeoutSeconds());
        assertEquals(5, e.getMaxRetries());
    }
}
