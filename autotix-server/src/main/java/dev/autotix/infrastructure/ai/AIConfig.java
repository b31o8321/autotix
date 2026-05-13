package dev.autotix.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TODO: Binds autotix.ai.* from application.yml.
 *  Overridable at runtime by admin UI (persisted in DB, hot-loaded).
 */
@Component
@ConfigurationProperties(prefix = "autotix.ai")
public class AIConfig {

    private String endpoint;
    private String apiKey;
    private String model;
    private String systemPrompt;
    private int timeoutSeconds = 30;
    private int maxRetries = 2;

    // getters & setters
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int t) { this.timeoutSeconds = t; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int r) { this.maxRetries = r; }
}
