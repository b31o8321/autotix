package dev.autotix.infrastructure.platform.intercom;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * TODO: Intercom integration (CHAT channel type).
 *  Auth: Personal access token (workspace-scoped).
 *  Webhook: X-Hub-Signature (HMAC-SHA1 of payload with client_secret).
 */
@Component
public class IntercomPlugin implements TicketPlatformPlugin {

    @Override public PlatformType platform() { return PlatformType.INTERCOM; }
    @Override public ChannelType defaultChannelType() { return ChannelType.CHAT; }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // TODO: verify X-Hub-Signature; parse conversation.user.replied / created
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: POST /conversations/{id}/reply
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // TODO: POST /conversations/{id}/parts with type=close
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        // TODO: GET /me
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.INTERCOM,
                "Intercom",
                "chat",
                ChannelType.CHAT,
                Collections.singletonList(ChannelType.CHAT),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("workspace_id", "Workspace ID", "string", true)
                                .placeholder("abcd1234")
                                .help("Your Intercom workspace ID (visible in the app URL or Settings → General)"),
                        PlatformDescriptor.AuthField.of("access_token", "Access Token", "password", true)
                                .placeholder("dG9rxxxxxxxxxxxxxxxx")
                                .help("Personal access token from Intercom Developer Hub")
                ),
                false,
                "https://developers.intercom.com/docs/build-an-integration/getting-started/",
                "1. Log in to Intercom and go to Settings → Developers → Build an App (or use your existing app).\n" +
                "2. In your app, click the \"Authentication\" tab.\n" +
                "3. Copy the Access token shown there.\n" +
                "4. Find your Workspace ID in Settings → General or from the app URL (app.intercom.com/a/apps/{workspace_id}).\n" +
                "5. Paste both values below.\n" +
                "Docs: https://developers.intercom.com/docs/build-an-integration/getting-started/"
        );
    }
}
