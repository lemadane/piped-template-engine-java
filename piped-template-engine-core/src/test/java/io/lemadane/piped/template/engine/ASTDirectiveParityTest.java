package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.res.InMemoryTemplateSourceResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ASTDirectiveParityTest {

    @Test
    @DisplayName("Includes render correctly: X|include partial|Y -> XHi JoeY")
    void testIncludeParity() {
        InMemoryTemplateSourceResolver resolver = new InMemoryTemplateSourceResolver();
        resolver.register("partials/header.pte", "Hi |name|");

        TemplateEngine engine = new TemplateEngine(resolver);
        String template = "X|include partials/header|Y";

        String result = engine.renderString(template, Map.of("name", "Joe"));
        assertEquals("XHi JoeY", result);
    }

    @Test
    @DisplayName("Includes with model expression render correctly")
    void testIncludeWithModel() {
        InMemoryTemplateSourceResolver resolver = new InMemoryTemplateSourceResolver();
        resolver.register("partials/user.pte", "User: |name|");

        TemplateEngine engine = new TemplateEngine(resolver);
        String template = "|include partials/user with user|";

        String result = engine.renderString(template, Map.of("user", Map.of("name", "Alice")));
        assertEquals("User: Alice", result);
    }

    @Test
    @DisplayName("Layouts, sections, and yields render correctly")
    void testLayoutAndSections() {
        InMemoryTemplateSourceResolver resolver = new InMemoryTemplateSourceResolver();
        resolver.register("layouts/main.pte", "<html><head><title>|yield title|</title></head><body>|yield content|</body></html>");

        TemplateEngine engine = new TemplateEngine(resolver);
        String template = """
            |layout layouts/main|
            |section title|Dashboard|/section|
            |section content|<h1>Hello World</h1>|/section|
            """;

        String result = engine.renderString(template, Map.of());
        assertTrue(result.contains("<title>Dashboard</title>"));
        assertTrue(result.contains("<h1>Hello World</h1>"));
    }

    @Test
    @DisplayName("Components and slots render correctly")
    void testComponentAndSlots() {
        InMemoryTemplateSourceResolver resolver = new InMemoryTemplateSourceResolver();
        resolver.register("components/card.pte", "<div class=\"card\"><h3>|slot header|</h3><p>|name|</p></div>");

        TemplateEngine engine = new TemplateEngine(resolver);
        String template = """
            |component components/card with product|
                |slot header|Product Details|/slot|
            |/component|
            """;

        String result = engine.renderString(template, Map.of("product", Map.of("name", "Laptop")));
        assertTrue(result.contains("<h3>Product Details</h3>"));
        assertTrue(result.contains("<p>Laptop</p>"));
    }
}
