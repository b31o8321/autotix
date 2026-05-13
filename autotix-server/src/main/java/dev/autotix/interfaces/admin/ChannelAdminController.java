package dev.autotix.interfaces.admin;

import dev.autotix.application.channel.*;
import dev.autotix.interfaces.admin.dto.ChannelDTO;
import dev.autotix.interfaces.admin.dto.ConnectApiKeyRequest;
import dev.autotix.interfaces.admin.dto.OAuthStartRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TODO: Admin REST for Channel management — used by Settings page.
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
    public List<ChannelDTO> list() {
        // TODO: map domain Channel -> ChannelDTO
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/oauth/start")
    public Map<String, String> startOAuth(@RequestBody OAuthStartRequest req) {
        // TODO: return {authorizeUrl, state}
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/oauth/callback")
    public Map<String, String> oauthCallback(@RequestParam String state, @RequestParam String code) {
        // TODO: completeOAuth; return {channelId}
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/connect-api-key")
    public Map<String, String> connectWithApiKey(@RequestBody ConnectApiKeyRequest req) {
        // TODO: connectWithApiKey
        throw new UnsupportedOperationException("TODO");
    }

    @DeleteMapping("/{channelId}")
    public void disconnect(@PathVariable String channelId,
                           @RequestParam(defaultValue = "false") boolean hardDelete) {
        // TODO: disconnectChannel.disconnect
        throw new UnsupportedOperationException("TODO");
    }

    @PutMapping("/{channelId}/auto-reply")
    public void toggleAutoReply(@PathVariable String channelId, @RequestParam boolean enabled) {
        // TODO: updateChannel.setAutoReply
        throw new UnsupportedOperationException("TODO");
    }

    @PutMapping("/{channelId}/name")
    public void rename(@PathVariable String channelId, @RequestParam String displayName) {
        // TODO: updateChannel.rename
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/{channelId}/rotate-webhook")
    public Map<String, String> rotateToken(@PathVariable String channelId) {
        // TODO: return {webhookToken: newToken}
        throw new UnsupportedOperationException("TODO");
    }
}
