package dev.autotix.infrastructure.formatter;

import dev.autotix.domain.channel.ChannelType;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts AI Markdown reply to plain text for Chat channels.
 *
 * Strategy: regex-based stripping of Markdown syntax, JDK 8 compatible.
 *   - ATX headings (#, ##, ...) stripped to plain text
 *   - Bold (**text** or __text__) and italic (*text* or _text_) markers removed
 *   - Unordered list markers (- / * / +) replaced with "- "
 *   - Links [text](url) rendered as "text (url)"
 *   - Images ![alt](url) rendered as alt text only
 *   - Fenced code blocks: preserve inner text, strip fences
 *   - Inline code: strip backticks
 *   - Blockquotes and horizontal rules stripped
 *   - Newlines preserved
 */
@Component
public class ChatFormatter implements ChannelReplyFormatter {

    // ATX heading: strip leading # chars
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*?)\\s*#*$", Pattern.MULTILINE);

    // Images before links
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");

    // Links: [text](url)
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");

    // Bold: **text** or __text__
    private static final Pattern BOLD_STAR = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern BOLD_UNDER = Pattern.compile("__([^_]+)__");

    // Italic: *text* (single star, not double)
    private static final Pattern ITALIC_STAR = Pattern.compile("(?<!\\*)\\*(?!\\*)([^*\\n]+)(?<!\\*)\\*(?!\\*)");
    private static final Pattern ITALIC_UNDER = Pattern.compile("(?<!_)_(?!_)([^_\\n]+)(?<!_)_(?!_)");

    // Unordered list bullets at start of line
    private static final Pattern UNORDERED_LIST = Pattern.compile("^[*\\-+]\\s+", Pattern.MULTILINE);

    // Inline code
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");

    // Fenced code blocks
    private static final Pattern FENCED_CODE = Pattern.compile("```[^\\n]*\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    // Horizontal rules
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^[-*_]{3,}\\s*$", Pattern.MULTILINE);

    // Blockquotes
    private static final Pattern BLOCKQUOTE = Pattern.compile("^>+\\s?", Pattern.MULTILINE);

    @Override
    public ChannelType supports() {
        return ChannelType.CHAT;
    }

    @Override
    public String format(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = markdown;

        // Fenced code blocks: keep inner content
        text = FENCED_CODE.matcher(text).replaceAll("$1");

        // ATX headings
        text = HEADING.matcher(text).replaceAll("$1");

        // Images -> alt text
        text = IMAGE.matcher(text).replaceAll("$1");

        // Links -> text (url)
        text = replaceLinks(text);

        // Bold
        text = BOLD_STAR.matcher(text).replaceAll("$1");
        text = BOLD_UNDER.matcher(text).replaceAll("$1");

        // Italic (apply after bold to avoid partial matches)
        text = ITALIC_STAR.matcher(text).replaceAll("$1");
        text = ITALIC_UNDER.matcher(text).replaceAll("$1");

        // Unordered list markers
        text = UNORDERED_LIST.matcher(text).replaceAll("- ");

        // Inline code
        text = INLINE_CODE.matcher(text).replaceAll("$1");

        // Blockquotes
        text = BLOCKQUOTE.matcher(text).replaceAll("");

        // Horizontal rules
        text = HORIZONTAL_RULE.matcher(text).replaceAll("");

        return text.trim();
    }

    /** Replace [text](url) with "text (url)" using a loop (JDK 8 compatible). */
    private static String replaceLinks(String input) {
        StringBuffer sb = new StringBuffer();
        Matcher m = LINK.matcher(input);
        while (m.find()) {
            String replacement = m.group(1) + " (" + m.group(2) + ")";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
