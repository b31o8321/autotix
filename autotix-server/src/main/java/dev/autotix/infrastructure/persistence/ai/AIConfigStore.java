package dev.autotix.infrastructure.persistence.ai;

import dev.autotix.infrastructure.ai.AIConfig;
import dev.autotix.infrastructure.persistence.ai.mapper.AIConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence store for the singleton AIConfig row.
 * Uses selectById(1) / insertOrUpdate to maintain one row with id=1.
 */
@Component
public class AIConfigStore {

    private static final Logger log = LoggerFactory.getLogger(AIConfigStore.class);
    private static final long SINGLETON_ID = 1L;

    private final AIConfigMapper mapper;

    public AIConfigStore(AIConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** Load the persisted AI config if present. */
    public Optional<AIConfigEntity> load() {
        return Optional.ofNullable(mapper.selectById(SINGLETON_ID));
    }

    /**
     * Persist the current in-memory AIConfig snapshot as the singleton row.
     * Performs an upsert: insert on first call, update on subsequent calls.
     */
    public void save(AIConfig snapshot) {
        AIConfigEntity entity = new AIConfigEntity();
        entity.setId(SINGLETON_ID);
        entity.setEndpoint(snapshot.getEndpoint());
        entity.setApiKey(snapshot.getApiKey());
        entity.setModel(snapshot.getModel());
        entity.setSystemPrompt(snapshot.getSystemPrompt());
        entity.setTimeoutSeconds(snapshot.getTimeoutSeconds());
        entity.setMaxRetries(snapshot.getMaxRetries());
        entity.setGlobalAutoReplyEnabled(snapshot.isGlobalAutoReplyEnabled());
        entity.setUpdatedAt(Instant.now());

        if (mapper.selectById(SINGLETON_ID) == null) {
            mapper.insert(entity);
            log.info("AI config persisted to DB for the first time");
        } else {
            mapper.updateById(entity);
            log.debug("AI config updated in DB");
        }
    }
}
