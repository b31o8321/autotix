package dev.autotix.domain.channel;

/**
 * TODO: Channel "big category" — drives reply format conversion.
 *  EMAIL → ReplyFormatter renders Markdown to HTML
 *  CHAT  → ReplyFormatter strips Markdown to plain text
 *  VOICE → reserved, not supported in v1
 *
 *  Note: ChannelType is bound to the integration INSTANCE, not the platform.
 *  E.g. Zendesk Tickets = EMAIL, Zendesk Sunshine = CHAT.
 */
public enum ChannelType {
    EMAIL,
    CHAT,
    VOICE  // TODO: not supported in v1; do not allow as active channel
}
