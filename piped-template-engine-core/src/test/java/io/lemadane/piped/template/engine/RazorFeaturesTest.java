package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RazorFeaturesTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Parses model directive contract without output")
    void parsesModelDirective() {
        String html = engine.renderString("|model com.example.TaskModel|\n<h1>Hello</h1>", Map.of());
        assertTrue(html.contains("<h1>Hello</h1>"));
    }

    @Test
    @DisplayName("Renders field form binding attributes")
    void rendersFieldFormBinding() {
        String html = engine.renderString("<input |field user.email|>", Map.of("user", Map.of("email", "test@example.com")));
        assertTrue(html.contains("name=\"email\""));
        assertTrue(html.contains("value=\"test@example.com\""));
    }

    @Test
    @DisplayName("Renders display and editor templates")
    void rendersDisplayAndEditorTemplates() {
        String displayHtml = engine.renderString("|display user.name|", Map.of("user", Map.of("name", "Alice")));
        assertEquals("Alice", displayHtml);

        String editorHtml = engine.renderString("|editor user.email|", Map.of("user", Map.of("email", "alice@example.com")));
        assertTrue(editorHtml.contains("<input type=\"text\" name=\"email\""));
    }
}
