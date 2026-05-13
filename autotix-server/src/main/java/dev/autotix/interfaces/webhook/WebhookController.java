package dev.autotix.interfaces.webhook;

import dev.autotix.application.ticket.ProcessWebhookUseCase;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.infrastructure.platform.PluginRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * TODO: Single webhook entry for all platforms.
 *  Route: POST /v2/webhook/{platform}/{token}
 *
 *  Flow:
 *    1. Resolve PlatformType from path segment
 *    2. Lookup Channel by (platform, webhookToken)
 *    3. Resolve Plugin from PluginRegistry
 *    4. Plugin.parseWebhook(...) — also verifies signature
 *    5. ProcessWebhookUseCase.handle(channel, event)
 *    6. Return 200 fast (work is async)
 *
 *  Must respond &lt; 5s; heavy work is enqueued via QueueProvider.
 */
@RestController
@RequestMapping("/v2/webhook")
public class WebhookController {

    private final ChannelRepository channelRepository;
    private final PluginRegistry pluginRegistry;
    private final ProcessWebhookUseCase processWebhook;

    public WebhookController(ChannelRepository channelRepository,
                             PluginRegistry pluginRegistry,
                             ProcessWebhookUseCase processWebhook) {
        this.channelRepository = channelRepository;
        this.pluginRegistry = pluginRegistry;
        this.processWebhook = processWebhook;
    }

    @PostMapping("/{platform}/{token}")
    public ResponseEntity<Void> receive(@PathVariable String platform,
                                        @PathVariable String token,
                                        @RequestBody(required = false) String rawBody,
                                        HttpServletRequest request) {
        // TODO:
        //   - parse PlatformType.valueOf(platform.toUpperCase())
        //   - extract headers as Map<String,String>
        //   - findByWebhookToken; 404 if missing
        //   - plugin.parseWebhook -> event; if IGNORED, return 200
        //   - processWebhook.handle
        //   - on signature failure return 401
        throw new UnsupportedOperationException("TODO");
    }

    // TODO: helper for header extraction
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        throw new UnsupportedOperationException("TODO");
    }
}
