package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FragmentTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Renders template containing fragments normally")
    void rendersFullTemplateNormally() {
        String template = """
            <div>
                <h1>Header</h1>
                |fragment content-block|
                    <p>Inside Fragment</p>
                |/fragment|
            </div>
            """;

        String html = engine.renderString(template, Map.of());
        assertTrue(html.contains("<h1>Header</h1>"));
        assertTrue(html.contains("<p>Inside Fragment</p>"));
    }

    @Test
    @DisplayName("Renders only the targeted fragment block")
    void rendersTargetedFragmentOnly() {
        String template = """
            <div>
                <h1>Header</h1>
                |fragment content-block|
                    <p>Inside Fragment</p>
                |/fragment|
            </div>
            """;

        String html = engine.renderFragment(template, "content-block", Map.of());
        assertFalse(html.contains("<h1>Header</h1>"));
        assertTrue(html.contains("<p>Inside Fragment</p>"));
    }
}
