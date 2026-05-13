package dev.autotix.infrastructure.formatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmailFormatter: Markdown -&gt; HTML conversion.
 */
class EmailFormatterTest {

    private EmailFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new EmailFormatter();
    }

    @Test
    void rendersBasicMarkdown() {
        String markdown = "# Hello\n\n**Bold text**\n\n- item one\n- item two\n\n[Link](https://example.com)";
        String html = formatter.format(markdown);

        // Heading
        assertTrue(html.contains("<h1>"), "Expected h1 tag. Got: " + html);
        assertTrue(html.contains("Hello"), "Expected heading text");

        // Bold
        assertTrue(html.contains("<strong>") || html.contains("<b>"), "Expected bold tag. Got: " + html);
        assertTrue(html.contains("Bold text"));

        // List
        assertTrue(html.contains("<li>"), "Expected list item tag. Got: " + html);
        assertTrue(html.contains("item one"));

        // Link
        assertTrue(html.contains("<a "), "Expected anchor tag. Got: " + html);
        assertTrue(html.contains("href=\"https://example.com\""), "Expected href attribute. Got: " + html);
    }

    @Test
    void stripsScriptTags() {
        String markdown = "Hello <script>alert(1)</script> World";
        String html = formatter.format(markdown);

        assertFalse(html.contains("<script>"),
                "Raw <script> tag must not pass through. Got: " + html);
        // The text should be escaped (e.g. &lt;script&gt;)
        assertTrue(html.contains("&lt;script&gt;") || !html.contains("script>"),
                "Script tag should be escaped. Got: " + html);
    }

    @Test
    void autolinksUrlBecomesAnchor() {
        String markdown = "Visit https://example.com for more.";
        String html = formatter.format(markdown);

        assertTrue(html.contains("<a "), "Expected anchor tag for bare URL. Got: " + html);
        assertTrue(html.contains("https://example.com"), "Expected URL in href. Got: " + html);
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertEquals("", formatter.format(""));
        assertEquals("", formatter.format(null));
    }
}
