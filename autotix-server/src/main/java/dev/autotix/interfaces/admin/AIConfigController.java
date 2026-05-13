package dev.autotix.interfaces.admin;

import dev.autotix.infrastructure.ai.AIConfig;
import dev.autotix.interfaces.admin.dto.AIConfigDTO;
import org.springframework.web.bind.annotation.*;

/**
 * TODO: AI configuration admin REST.
 *  GET — return current effective config (api_key masked).
 *  PUT — update config (persist to DB; hot reload AIConfig bean).
 *  POST /test — call AI with a sample prompt and return the response (config validation).
 */
@RestController
@RequestMapping("/api/admin/ai")
public class AIConfigController {

    public AIConfigController(AIConfig aiConfig) {
        // TODO: inject AIConfig + a config persistence service
    }

    @GetMapping
    public AIConfigDTO get() {
        // TODO: mask apiKey before returning
        throw new UnsupportedOperationException("TODO");
    }

    @PutMapping
    public void update(@RequestBody AIConfigDTO dto) {
        // TODO: persist + reload
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/test")
    public AIConfigDTO.TestResult test(@RequestBody AIConfigDTO dto) {
        // TODO: invoke AIReplyPort with a fixed sample prompt; return latency + sample reply
        throw new UnsupportedOperationException("TODO");
    }
}
