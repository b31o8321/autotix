package dev.autotix.interfaces.admin.dto;

/**
 * TODO: REST DTO for AI configuration.
 *  apiKey is masked on GET (e.g. "sk-***1234"), accepted in plaintext on PUT.
 */
public class AIConfigDTO {
    public String endpoint;
    public String apiKey;
    public String model;
    public String systemPrompt;
    public int timeoutSeconds;
    public int maxRetries;

    /** TODO: response of /test — used by UI to validate config before saving. */
    public static class TestResult {
        public boolean ok;
        public long latencyMs;
        public String sampleReply;
        public String error;
    }
}
