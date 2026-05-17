package dev.autotix.interfaces.webhook;

import dev.autotix.application.ticket.ProcessWebhookUseCase;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.infrastructure.platform.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Single webhook entry for all platforms.
 * Route: POST /v2/webhook/{platform}/{token}
 *
 * Must respond quickly (< 5s); heavy AI work is async via QueueProvider.
 */
@RestController
@RequestMapping("/v2/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

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
        // 1. Resolve PlatformType
        PlatformType platformType;
        try {
            platformType = PlatformType.valueOf(platform.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        // 2. Lookup Channel by (platform, webhookToken) — only enabled channels
        Optional<Channel> channelOpt = channelRepository.findByWebhookToken(platformType, token);
        if (!channelOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Channel channel = channelOpt.get();

        // 3. Extract headers (+ inject WeCom query params as pseudo-headers for WECOM platform)
        Map<String, String> headers = extractHeaders(request);
        if (platformType == PlatformType.WECOM) {
            injectWecomQueryParams(headers, request);
        }

        // 4. Parse webhook (also verifies signature inside plugin)
        TicketEvent event;
        try {
            event = pluginRegistry.get(platformType).parseWebhook(channel, headers,
                    rawBody != null ? rawBody : "");
        } catch (AutotixException.AuthException e) {
            log.warn("Webhook signature verification failed for platform={} token={}: {}",
                    platform, token, e.getMessage());
            return ResponseEntity.status(401).build();
        }

        // 5. Ignore irrelevant events quickly
        if (event.type() == EventType.IGNORED) {
            log.debug("Ignored webhook event for platform={}", platform);
            return ResponseEntity.ok().build();
        }

        // 6. Process (idempotent; async AI dispatch happens inside via queue)
        processWebhook.handle(channel, event);

        return ResponseEntity.ok().build();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }

    /**
     * WeCom sends signature params as query parameters, not HTTP headers.
     * Inject them as pseudo-headers so WeComPlugin.parseWebhook can consume them uniformly.
     */
    private void injectWecomQueryParams(Map<String, String> headers, HttpServletRequest request) {
        String sig = request.getParameter("msg_signature");
        String ts  = request.getParameter("timestamp");
        String nc  = request.getParameter("nonce");
        if (sig != null) headers.put("x-wecom-msg-signature", sig);
        if (ts  != null) headers.put("x-wecom-timestamp", ts);
        if (nc  != null) headers.put("x-wecom-nonce", nc);
    }
}
