package dev.autotix.infrastructure.ai;

import dev.autotix.infrastructure.persistence.ai.AIConfigEntity;
import dev.autotix.infrastructure.persistence.ai.AIConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * On startup:
 *  - If a persisted row exists in ai_config, override the yml-loaded AIConfig bean with DB values.
 *  - If no row exists (first run), write the current yml values to DB so future restarts read them back.
 */
@Component
public class AIConfigBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AIConfigBootstrap.class);

    private final AIConfig aiConfig;
    private final AIConfigStore aiConfigStore;

    public AIConfigBootstrap(AIConfig aiConfig, AIConfigStore aiConfigStore) {
        this.aiConfig = aiConfig;
        this.aiConfigStore = aiConfigStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<AIConfigEntity> persisted = aiConfigStore.load();
        if (persisted.isPresent()) {
            AIConfigEntity e = persisted.get();
            log.info("Loading AI config from DB (endpoint={}, model={})", e.getEndpoint(), e.getModel());
            if (e.getEndpoint() != null) aiConfig.setEndpoint(e.getEndpoint());
            if (e.getApiKey() != null) aiConfig.setApiKey(e.getApiKey());
            if (e.getModel() != null) aiConfig.setModel(e.getModel());
            aiConfig.setSystemPrompt(e.getSystemPrompt());
            if (e.getTimeoutSeconds() != null && e.getTimeoutSeconds() > 0) {
                aiConfig.setTimeoutSeconds(e.getTimeoutSeconds());
            }
            if (e.getMaxRetries() != null && e.getMaxRetries() >= 0) {
                aiConfig.setMaxRetries(e.getMaxRetries());
            }
        } else {
            log.info("No AI config in DB — seeding from yml values (endpoint={}, model={})",
                    aiConfig.getEndpoint(), aiConfig.getModel());
            aiConfigStore.save(aiConfig);
        }
    }
}
