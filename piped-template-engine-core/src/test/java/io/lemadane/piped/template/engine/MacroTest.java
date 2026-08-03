package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MacroTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Defines and calls a macro with parameters")
    void definesAndCallsMacro() {
        String template = """
            |macro input(name, value)|
                <input name="|name|" value="|value|">
            |/macro|
            
            |call input('username', user.name)|
            """;

        String html = engine.renderString(template, Map.of("user", Map.of("name", "Alice")));
        assertTrue(html.contains("name=\"username\""));
        assertTrue(html.contains("value=\"Alice\""));
    }

    @Test
    @DisplayName("Renders badge macro with multiple parameters")
    void rendersBadgeMacro() {
        String template = """
            |macro badge(text, status)|
                <span class="badge |status|">|text|</span>
            |/macro|
            
            |call badge('Active Account', 'is-success')|
            """;

        String html = engine.renderString(template, Map.of());
        assertTrue(html.contains("<span class=\"badge is-success\">Active Account</span>"));
    }
}
