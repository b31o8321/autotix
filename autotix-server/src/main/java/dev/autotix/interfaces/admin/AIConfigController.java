package dev.autotix.interfaces.admin;

import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.ai.AIReplyPort;
import dev.autotix.domain.ai.AIRequest;
import dev.autotix.domain.ai.AIResponse;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.infrastructure.ai.AIConfig;
import dev.autotix.infrastructure.persistence.ai.AIConfigStore;
import dev.autotix.interfaces.admin.dto.AIConfigDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * AI configuration admin REST endpoint.
 *
 * GET  /api/admin/ai       — return current effective config (apiKey masked).
 * PUT  /api/admin/ai       — update in-memory AIConfig and persist to DB via AIConfigStore.
 * POST /api/admin/ai/test  — call AI with a sample prompt and return response + latency.
 */
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AIConfigController {

    private static final Logger log = LoggerFactory.getLogger(AIConfigController.class);

    private final AIConfig aiConfig;
    private final AIReplyPort aiReplyPort;
    private final AIConfigStore aiConfigStore;

    public AIConfigController(AIConfig aiConfig, AIReplyPort aiReplyPort, AIConfigStore aiConfigStore) {
        this.aiConfig = aiConfig;
        this.aiReplyPort = aiReplyPort;
        this.aiConfigStore = aiConfigStore;
    }

    @GetMapping
    public AIConfigDTO get() {
        return toDTO(aiConfig);
    }

    /**
     * Update the in-memory AIConfig and persist to DB.
     * Fields are applied directly to the mutable bean, then saved via AIConfigStore.
     */
    @PutMapping
    public AIConfigDTO update(@RequestBody AIConfigDTO dto) {
        if (dto.endpoint != null) {
            aiConfig.setEndpoint(dto.endpoint);
        }
        // Only update apiKey if provided and not masked
        if (dto.apiKey != null && !dto.apiKey.startsWith("sk-***") && !dto.apiKey.equals("***")) {
            aiConfig.setApiKey(dto.apiKey);
        }
        if (dto.model != null) {
            aiConfig.setModel(dto.model);
        }
        if (dto.systemPrompt != null) {
            aiConfig.setSystemPrompt(dto.systemPrompt);
        }
        if (dto.timeoutSeconds > 0) {
            aiConfig.setTimeoutSeconds(dto.timeoutSeconds);
        }
        if (dto.maxRetries >= 0) {
            aiConfig.setMaxRetries(dto.maxRetries);
        }
        // globalAutoReplyEnabled is a boolean; always apply it on PUT
        aiConfig.setGlobalAutoReplyEnabled(dto.globalAutoReplyEnabled);
        aiConfigStore.save(aiConfig);
        return toDTO(aiConfig);
    }

    @PostMapping("/test")
    public AIConfigDTO.TestResult test() {
        long start = System.currentTimeMillis();
        AIConfigDTO.TestResult result = new AIConfigDTO.TestResult();
        try {
            AIRequest request = new AIRequest(
                    ChannelType.EMAIL,
                    "Test User",
                    "Test prompt — reply with one short sentence.",
                    Collections.emptyList(),
                    null);
            AIResponse response = aiReplyPort.generate(request);
            result.ok = true;
            result.latencyMs = System.currentTimeMillis() - start;
            result.sampleReply = response.reply();
        } catch (Exception e) {
            log.warn("AI config test failed: {}", e.getMessage());
            result.ok = false;
            result.latencyMs = System.currentTimeMillis() - start;
            result.error = e.getMessage();
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AIConfigDTO toDTO(AIConfig config) {
        AIConfigDTO dto = new AIConfigDTO();
        dto.endpoint = config.getEndpoint();
        dto.apiKey = maskApiKey(config.getApiKey());
        dto.model = config.getModel();
        dto.systemPrompt = config.getSystemPrompt();
        dto.timeoutSeconds = config.getTimeoutSeconds();
        dto.maxRetries = config.getMaxRetries();
        dto.globalAutoReplyEnabled = config.isGlobalAutoReplyEnabled();
        return dto;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 5) {
            return "***";
        }
        return "sk-***" + apiKey.substring(apiKey.length() - 4);
    }
}
