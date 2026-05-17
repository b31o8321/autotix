package dev.autotix.interfaces.webhook;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.infrastructure.platform.wecom.WecomWebhookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Handles WeCom (企业微信客服) URL verification GET requests.
 *
 * <p>Route: {@code GET /v2/webhook/WECOM/{token}}
 *
 * <p>When an operator saves the callback URL in the WeCom admin console, WeCom immediately
 * sends a {@code GET} request with {@code msg_signature}, {@code timestamp}, {@code nonce},
 * and {@code echostr}. This controller decrypts the echostr and returns the plaintext as
 * {@code text/plain} — WeCom expects the raw string, NOT JSON.
 *
 * <p>This is a separate controller (Option A from the spec) to keep zero blast radius on the
 * generic {@link WebhookController} which handles POSTs.
 *
 * <p>Security: {@code /v2/webhook/**} is already in the JWT filter's permit-all list;
 * no additional security configuration required.
 */
@RestController
@RequestMapping("/v2/webhook/WECOM")
public class WecomVerificationController {

    private static final Logger log = LoggerFactory.getLogger(WecomVerificationController.class);

    private final ChannelRepository channelRepository;
    private final WecomWebhookHandler webhookHandler;

    public WecomVerificationController(ChannelRepository channelRepository,
                                       WecomWebhookHandler webhookHandler) {
        this.channelRepository = channelRepository;
        this.webhookHandler = webhookHandler;
    }

    /**
     * Handles WeCom's URL verification handshake.
     *
     * @param token        channel webhook token (path variable)
     * @param msgSignature WeCom msg_signature query param
     * @param timestamp    request timestamp
     * @param nonce        random nonce
     * @param echostr      encrypted echostr to decrypt and echo back
     * @return plaintext echostr as text/plain, or 404/401 on failure
     */
    @GetMapping(value = "/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @PathVariable String token,
            @RequestParam(name = "msg_signature", required = false, defaultValue = "") String msgSignature,
            @RequestParam(name = "timestamp",     required = false, defaultValue = "") String timestamp,
            @RequestParam(name = "nonce",         required = false, defaultValue = "") String nonce,
            @RequestParam(name = "echostr",       required = false, defaultValue = "") String echostr) {

        Optional<Channel> channelOpt = channelRepository.findByWebhookToken(PlatformType.WECOM, token);
        if (!channelOpt.isPresent()) {
            log.warn("[WeCom] GET verification: channel not found for token {}", token);
            return ResponseEntity.notFound().build();
        }
        Channel channel = channelOpt.get();

        if (echostr.isEmpty()) {
            // Not a verification request — may be a browser probe; return OK
            log.debug("[WeCom] GET on channel {} with no echostr — returning 200", channel.id().value());
            return ResponseEntity.ok("ok");
        }

        try {
            String plaintext = webhookHandler.handleGet(channel, msgSignature, timestamp, nonce, echostr);
            log.info("[WeCom] URL verification passed for channel {}", channel.id().value());
            return ResponseEntity.ok(plaintext);
        } catch (AutotixException.AuthException e) {
            log.warn("[WeCom] GET verification failed for channel {}: {}", channel.id().value(), e.getMessage());
            return ResponseEntity.status(401).body("signature mismatch");
        }
    }
}
