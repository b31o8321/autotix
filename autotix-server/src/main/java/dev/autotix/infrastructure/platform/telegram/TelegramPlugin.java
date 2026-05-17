package dev.autotix.infrastructure.platform.telegram;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Telegram Bot API integration (CHAT channel type).
 *
 * <p>Auth: {@code bot_token} from @BotFather; optional {@code secret_token}
 * for webhook signature verification.
 *
 * <p>Inbound: Telegram POSTs Update JSON to {@code /v2/webhook/TELEGRAM/{webhookToken}}.
 * Verified via {@code X-Telegram-Bot-Api-Secret-Token} header if {@code secret_token} is set.
 *
 * <p>Outbound reply: {@code POST /sendMessage} with the {@code chat_id} stored in
 * {@code ticket.externalThreadId}.
 *
 * <p>Close: no-op (Telegram has no ticket-close concept).
 */
@Component
public class TelegramPlugin implements TicketPlatformPlugin {

    private final TelegramClient telegramClient;
    private final TelegramWebhookParser webhookParser;

    @Autowired
    public TelegramPlugin(TelegramClient telegramClient, TelegramWebhookParser webhookParser) {
        this.telegramClient = telegramClient;
        this.webhookParser = webhookParser;
    }

    @Override
    public PlatformType platform() {
        return PlatformType.TELEGRAM;
    }

    @Override
    public ChannelType defaultChannelType() {
        return ChannelType.CHAT;
    }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        return webhookParser.parseAndVerify(channel, headers, rawBody);
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        String botToken = getBotToken(channel);
        String chatId = ticket.externalNativeId();
        if (chatId == null || chatId.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "Telegram sendReply: ticket.externalNativeId (chat_id) is missing for ticket " + ticket.id());
        }
        try {
            telegramClient.sendMessage(botToken, chatId, formattedReply);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "telegram", "sendReply failed for chatId=" + chatId, e);
        }
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // Telegram has no concept of closing a conversation — no-op.
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        String botToken = getBotTokenFromCredential(credential);
        return telegramClient.ping(botToken);
    }

    /**
     * Register a webhook with Telegram so it starts POSTing updates to our inbound URL.
     *
     * @param channel       the Telegram channel (must have bot_token in credentials)
     * @param inboundUrl    the fully-qualified URL Telegram should call (e.g. https://host/v2/webhook/TELEGRAM/{token})
     */
    public void registerWebhook(Channel channel, String inboundUrl) {
        String botToken = getBotToken(channel);
        String secretToken = null;
        if (channel.credential() != null && channel.credential().attributes() != null) {
            secretToken = channel.credential().attributes().get("secret_token");
        }
        try {
            telegramClient.setWebhook(botToken, inboundUrl, secretToken);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "telegram", "setWebhook failed for url=" + inboundUrl, e);
        }
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.TELEGRAM,
                "Telegram",
                "chat",
                ChannelType.CHAT,
                Collections.singletonList(ChannelType.CHAT),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("bot_token", "Bot Token", "password", true)
                                .placeholder("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11")
                                .help("From @BotFather: /newbot → follow prompts → copy the token"),
                        PlatformDescriptor.AuthField.of("secret_token", "Webhook Secret Token", "password", false)
                                .placeholder("(optional) 1-256 chars A-Za-z0-9_-")
                                .help("Telegram echoes this in X-Telegram-Bot-Api-Secret-Token header; " +
                                      "we verify it on every inbound webhook call.")
                ),
                true,
                "https://core.telegram.org/bots/api",
                "1. In Telegram, talk to @BotFather → /newbot → follow prompts → copy the token.\n" +
                "2. Create the channel here; copy the Inbound URL from the Edit page.\n" +
                "3. Click 'Register webhook' button on the Edit page to have Autotix call setWebhook " +
                "for you — or run the curl command shown if you prefer to do it manually.\n" +
                "4. (Optional) Set a Webhook Secret Token to verify Telegram is the only caller.\n" +
                "Docs: https://core.telegram.org/bots/api"
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String getBotToken(Channel channel) {
        return getBotTokenFromCredential(channel.credential());
    }

    private String getBotTokenFromCredential(ChannelCredential credential) {
        if (credential == null || credential.attributes() == null) {
            throw new AutotixException.ValidationException(
                    "Telegram credential is missing");
        }
        String token = credential.attributes().get("bot_token");
        if (token == null || token.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "Telegram credential missing 'bot_token' attribute");
        }
        return token;
    }
}
