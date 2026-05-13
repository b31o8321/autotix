package dev.autotix.infrastructure.formatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatFormatter: Markdown -&gt; plain text.
 */
class ChatFormatterTest {

    private ChatFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ChatFormatter();
    }

    @Test
    void stripsMarkdownSyntax_preservesText() {
        String markdown = "# Heading\n\n**Bold** and *italic* text.\n\n- item one\n- item two";
        String result = formatter.format(markdown);

        // No Markdown markers
        assertFalse(result.contains("#"), "Hash heading markers should be stripped. Got: " + result);
        assertFalse(result.contains("**"), "Bold markers should be stripped. Got: " + result);
        // Text preserved
        assertTrue(result.contains("Heading"), "Heading text should remain. Got: " + result);
        assertTrue(result.contains("Bold"), "Bold text should remain. Got: " + result);
        assertTrue(result.contains("italic"), "Italic text should remain. Got: " + result);
        assertTrue(result.contains("item one"), "List item text should remain. Got: " + result);
    }

    @Test
    void preservesNewlines() {
        String markdown = "Line one\n\nLine two\n\nLine three";
        String result = formatter.format(markdown);

        assertTrue(result.contains("Line one"), "Got: " + result);
        assertTrue(result.contains("Line two"), "Got: " + result);
        assertTrue(result.contains("Line three"), "Got: " + result);
        // Should have a newline between lines
        assertTrue(result.contains("\n"), "Should preserve newlines. Got: " + result);
    }

    @Test
    void linksContainTextAndUrl() {
        String markdown = "Check out [the docs](https://docs.example.com) for details.";
        String result = formatter.format(markdown);

        assertFalse(result.contains("["), "Bracket syntax should be removed. Got: " + result);
        assertTrue(result.contains("the docs"), "Link text should be visible. Got: " + result);
        assertTrue(result.contains("https://docs.example.com"), "URL should be visible. Got: " + result);
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertEquals("", formatter.format(""));
        assertEquals("", formatter.format(null));
    }
}
