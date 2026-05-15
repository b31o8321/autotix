package dev.autotix.interfaces.admin;

import dev.autotix.application.channel.*;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.interfaces.admin.dto.ChannelDTO;
import dev.autotix.interfaces.admin.dto.ConnectApiKeyRequest;
import dev.autotix.interfaces.admin.dto.OAuthStartRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin REST for Channel management — used by Settings page.
 * Auth: ROLE_ADMIN (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/channels")
public class ChannelAdminController {

    private final ListChannelsUseCase listChannels;
    private final ConnectChannelUseCase connectChannel;
    private final DisconnectChannelUseCase disconnectChannel;
    private final UpdateChannelSettingsUseCase updateChannel;

    public ChannelAdminController(ListChannelsUseCase listChannels,
                                  ConnectChannelUseCase connectChannel,
                                  DisconnectChannelUseCase disconnectChannel,
                                  UpdateChannelSettingsUseCase updateChannel) {
        this.listChannels = listChannels;
        this.connectChannel = connectChannel;
        this.disconnectChannel = disconnectChannel;
        this.updateChannel = updateChannel;
    }

    @GetMapping
    public List<ChannelDTO> list(@RequestParam(required = false) String platform) {
        // Optional filter by platform — v1 passes credentials through as-is (no strict validation)
        if (platform != null && !platform.isEmpty()) {
            PlatformType pt = PlatformType.valueOf(platform.toUpperCase());
            return listChannels.listByPlatform(pt).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
        return listChannels.list().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping("/oauth/start")
    public Map<String, String> startOAuth(@RequestBody OAuthStartRequest req) {
        // OAuth flow deferred to v2 — throws UnsupportedOperationException -> 501 via ErrorHandler
        PlatformType platform = PlatformType.valueOf(req.platform.toUpperCase());
        ChannelType type = ChannelType.valueOf(req.channelType.toUpperCase());
        String url = connectChannel.startOAuth(platform, type, req.displayName);
        Map<String, String> result = new HashMap<>();
        result.put("authorizeUrl", url);
        return result;
    }

    @GetMapping("/oauth/callback")
    public Map<String, String> oauthCallback(@RequestParam String state, @RequestParam String code) {
        // OAuth flow deferred to v2 — throws UnsupportedOperationException -> 501 via ErrorHandler
        ChannelId id = connectChannel.completeOAuth(state, code);
        Map<String, String> result = new HashMap<>();
        result.put("channelId", id.value());
        return result;
    }

    @PostMapping("/connect-api-key")
    public Map<String, String> connectWithApiKey(@RequestBody ConnectApiKeyRequest req) {
        PlatformType platform = PlatformType.valueOf(req.platform.toUpperCase());
        ChannelType type = ChannelType.valueOf(req.channelType.toUpperCase());
        ChannelId id = connectChannel.connectWithApiKey(platform, type, req.displayName,
                req.credentials != null ? req.credentials : new java.util.HashMap<>());
        Map<String, String> result = new HashMap<>();
        result.put("channelId", id.value());
        return result;
    }

    @DeleteMapping("/{channelId}")
    public void disconnect(@PathVariable String channelId,
                           @RequestParam(defaultValue = "false") boolean hardDelete) {
        disconnectChannel.disconnect(new ChannelId(channelId), hardDelete);
    }

    @PutMapping("/{channelId}/auto-reply")
    public void toggleAutoReply(@PathVariable String channelId, @RequestParam boolean enabled) {
        updateChannel.setAutoReply(new ChannelId(channelId), enabled);
    }

    @PutMapping("/{channelId}/name")
    public void rename(@PathVariable String channelId, @RequestParam String displayName) {
        updateChannel.rename(new ChannelId(channelId), displayName);
    }

    @PostMapping("/{channelId}/rotate-webhook")
    public Map<String, String> rotateToken(@PathVariable String channelId) {
        String newToken = updateChannel.rotateWebhookToken(new ChannelId(channelId));
        Map<String, String> result = new HashMap<>();
        result.put("webhookToken", newToken);
        return result;
    }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private ChannelDTO toDTO(Channel c) {
        ChannelDTO dto = new ChannelDTO();
        dto.id = c.id() != null ? c.id().value() : null;
        dto.platform = c.platform().name();
        dto.channelType = c.type().name();
        dto.displayName = c.displayName();
        dto.webhookToken = c.webhookToken();
        dto.enabled = c.isEnabled();
        dto.autoReplyEnabled = c.isAutoReplyEnabled();
        dto.connectedAt = c.createdAt();
        return dto;
    }
}
