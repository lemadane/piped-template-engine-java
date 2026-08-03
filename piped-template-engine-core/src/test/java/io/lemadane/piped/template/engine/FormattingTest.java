package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormattingTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Minifies inline blocks with |minify|")
    void minifiesInlineBlock() {
        String template = """
            |minify|
                <div class="row">
                    <p>Hello World</p>
                </div>
            |/minify|
            """;

        String html = engine.renderString(template, Map.of());
        assertEquals("<div class=\"row\"><p>Hello World</p></div>", html.trim());
    }

    @Test
    @DisplayName("Applies global minification")
    void appliesGlobalMinification() {
        engine.setMinify(true);
        try {
            String template = "<div>\n   <p>Text</p>\n</div>";
            String html = engine.renderString(template, Map.of());
            assertEquals("<div><p>Text</p></div>", html);
        } finally {
            engine.setMinify(false);
        }
    }

    @Test
    @DisplayName("Applies global prettifying")
    void appliesGlobalPrettifying() {
        engine.setPrettify(true);
        try {
            String template = "<div><p>Formatted text</p></div>";
            String html = engine.renderString(template, Map.of());
            assertTrue(html.contains("  <p>Formatted text</p>"));
        } finally {
            engine.setPrettify(false);
        }
    }
}
