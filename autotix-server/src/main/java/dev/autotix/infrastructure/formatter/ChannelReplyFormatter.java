package dev.autotix.infrastructure.formatter;

import dev.autotix.domain.channel.ChannelType;

/**
 * TODO: Per-channel-type formatter (EMAIL -&gt; HTML, CHAT -&gt; plain text).
 */
public interface ChannelReplyFormatter {
    ChannelType supports();
    String format(String markdown);
}
