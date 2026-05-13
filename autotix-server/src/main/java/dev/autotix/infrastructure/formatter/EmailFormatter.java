package dev.autotix.infrastructure.formatter;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import dev.autotix.domain.channel.ChannelType;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Converts AI Markdown reply to HTML for Email channels.
 *
 * Configuration:
 *   - AutolinkExtension: bare URLs become &lt;a href&gt; anchors
 *   - SOFT_BREAK set to &lt;br /&gt;\n so that single newlines render as line breaks
 *   - HtmlRenderer defaults: raw HTML is escaped (no XSS passthrough)
 */
@Component
public class EmailFormatter implements ChannelReplyFormatter {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public EmailFormatter() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(AutolinkExtension.create()));
        // Convert soft line breaks (single newline) to <br /> in output
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        // Escape raw HTML by default (HtmlRenderer.ESCAPE_HTML defaults to false; set true)
        options.set(HtmlRenderer.ESCAPE_HTML, true);

        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    @Override
    public ChannelType supports() {
        return ChannelType.EMAIL;
    }

    @Override
    public String format(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
