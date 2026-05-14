package dev.autotix.domain.channel;

/**
 * TODO: Enumerates supported third-party platforms.
 *  Each value maps to one TicketPlatformPlugin in infrastructure/platform/.
 *  Extend by:
 *    1) add enum constant here
 *    2) add a Plugin under infrastructure/platform/&lt;name&gt;/
 *    3) register in PluginRegistry
 */
public enum PlatformType {
    ZENDESK,
    ZENDESK_SUNSHINE,
    FRESHDESK,
    FRESHCHAT,
    GORGIAS,
    INTERCOM,
    LIVECHAT,
    SHOPIFY,
    AMAZON,
    GMAIL,
    OUTLOOK,
    LINE,
    WHATSAPP,
    WECOM,
    WECHAT,
    TIKTOK,
    /** Generic test/custom platform — no external API; healthCheck always passes; sendReply logs only. */
    CUSTOM,
    /** Native IMAP/SMTP email channel — polls via IMAP, sends via SMTP. */
    EMAIL,
    // TODO: add others as migrated from shulex-intelli
}
