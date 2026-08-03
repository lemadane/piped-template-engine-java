package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageMetadataTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Parses different types of metadata options successfully")
    void parsesMetadataOptions() {
        String template = """
            |page title = "Pricing Options"|
            |page cache = "public, max-age=3600"|
            |page auth = true|
            |page roles = ["ADMIN", "MANAGER"]|
            |page custom = 123|
            <h1>Pricing</h1>
            """;

        var compiled = engine.compile(template);
        Map<String, Object> meta = compiled.getMetadata();

        assertEquals("Pricing Options", meta.get("title"));
        assertEquals("public, max-age=3600", meta.get("cache"));
        assertEquals(Boolean.TRUE, meta.get("auth"));
        assertEquals(123, meta.get("custom"));

        assertTrue(meta.get("roles") instanceof List);
        List<?> roles = (List<?>) meta.get("roles");
        assertEquals(2, roles.size());
        assertEquals("ADMIN", roles.get(0));
        assertEquals("MANAGER", roles.get(1));
    }

    @Test
    @DisplayName("Page title metadata is accessible inside template during rendering")
    void rendersMetadataTitleInTemplate() {
        String template = """
            |page title = "Dashboard"|
            <title>|title|</title>
            """;

        RenderResult result = engine.renderTemplateSource(template, Map.of());
        assertEquals("<title>Dashboard</title>", result.html().trim());
    }

    @Test
    @DisplayName("Explicitly supplied model title overrides metadata title")
    void modelTitleOverridesMetadataTitle() {
        String template = """
            |page title = "Dashboard"|
            <title>|title|</title>
            """;

        RenderResult result = engine.renderTemplateSource(template, Map.of("title", "Custom Title"));
        assertEquals("<title>Custom Title</title>", result.html().trim());
    }
}
