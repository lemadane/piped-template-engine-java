package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataPropagationContractTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Metadata title is injected into model before rendering and accessible inside template")
    void testMetadataTitleInjectedBeforeRendering() {
        String template = """
            |page title="Default Page Title"|
            <h1>|title|</h1>
            """;

        RenderResult result = engine.renderTemplateSource(template, Map.of());
        assertEquals("Default Page Title", result.metadata().get("title"));
        assertTrue(result.html().contains("<h1>Default Page Title</h1>"));
    }

    @Test
    @DisplayName("Explicit model title overrides template metadata title")
    void testModelTitleOverridesMetadataTitle() {
        String template = """
            |page title="Default Page Title"|
            <h1>|title|</h1>
            """;

        RenderResult result = engine.renderTemplateSource(template, Map.of("title", "Overridden Title"));
        assertEquals("Default Page Title", result.metadata().get("title"));
        assertTrue(result.html().contains("<h1>Overridden Title</h1>"));
    }

    @Test
    @DisplayName("Caller-provided immutable input map is never mutated during metadata injection")
    void testImmutableInputMapNotMutated() {
        String template = """
            |page title="Default Page Title"|
            <h1>|title|</h1>
            """;

        Map<String, Object> immutableMap = Collections.unmodifiableMap(new HashMap<>());
        assertDoesNotThrow(() -> engine.renderTemplateSource(template, immutableMap));
        assertTrue(immutableMap.isEmpty());
    }
}
