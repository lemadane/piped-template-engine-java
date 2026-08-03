package io.lemadane.piped.template.engine.pwa;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.utils.JavaScriptStringEscaper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PWAJavaScriptEscapingTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("JavaScript string escaper preserves URL characters & = ? / # and escapes quotes, newlines, and script termination")
    void testJavaScriptStringEscaper() {
        String input = "/sw.js?v=1&scope=/app'\"</script>\n";
        String escaped = JavaScriptStringEscaper.escapeJsString(input);

        assertTrue(escaped.contains("&"), "Must preserve ampersand");
        assertTrue(escaped.contains("="), "Must preserve equals");
        assertTrue(escaped.contains("?"), "Must preserve question mark");
        assertTrue(escaped.contains("/"), "Must preserve forward slash");
        assertTrue(escaped.contains("\\'"), "Must escape single quote");
        assertTrue(escaped.contains("\\\""), "Must escape double quote");
        assertTrue(escaped.contains("\\u003C"), "Must encode < as \\u003C to prevent </script> injection");
        assertTrue(escaped.contains("\\n"), "Must escape newline");
        assertFalse(escaped.contains("&amp;"), "Must not use HTML entity encoding for JavaScript script string");
    }

    @Test
    @DisplayName("PWA directive renders service worker URL with JS escaping rather than HTML entity escaping")
    void testPwaDirectiveJsEscaping() {
        String template = "|pwa sw='/sw.js?v=1&scope=/app'|";
        String html = engine.renderTemplateSource(template, java.util.Map.of()).html();

        assertTrue(html.contains("register('/sw.js?v=1&scope=/app')"), "Must contain un-escaped ampersand in JS string");
        assertFalse(html.contains("&amp;scope="), "Must not HTML-escape ampersand in JS string");
    }
}
