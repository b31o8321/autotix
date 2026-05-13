package dev.autotix.infrastructure.formatter;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches AI Markdown reply to the appropriate channel-type formatter.
 *  - EMAIL  -&gt; {@link EmailFormatter} (Markdown -&gt; HTML)
 *  - CHAT   -&gt; {@link ChatFormatter}  (Markdown -&gt; plain text)
 *  - VOICE  -&gt; unsupported (throws ValidationException)
 */
@Component
public class ReplyFormatter {

    private final Map<ChannelType, ChannelReplyFormatter> formatters = new EnumMap<>(ChannelType.class);
    private final List<ChannelReplyFormatter> allFormatters;

    @Autowired
    public ReplyFormatter(List<ChannelReplyFormatter> impls) {
        for (ChannelReplyFormatter f : impls) {
            ChannelType key = f.supports();
            if (formatters.containsKey(key)) {
                throw new IllegalStateException(
                        "Duplicate ChannelReplyFormatter for type: " + key);
            }
            formatters.put(key, f);
        }
        this.allFormatters = impls;
    }

    /**
     * Format the given Markdown reply for the specified channel type.
     *
     * @throws AutotixException.ValidationException if no formatter is registered for the type
     */
    public String format(ChannelType type, String markdown) {
        ChannelReplyFormatter formatter = formatters.get(type);
        if (formatter == null) {
            throw new AutotixException.ValidationException(
                    "No reply formatter registered for channel type: " + type);
        }
        return formatter.format(markdown);
    }
}
