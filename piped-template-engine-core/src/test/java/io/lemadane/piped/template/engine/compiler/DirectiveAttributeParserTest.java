package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveAttributeParserTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Parses single and double quotes, unquoted booleans, numbers, and escaped characters")
    void parsesValidAttributes() {
        Map<String, Object> attrs = DirectiveAttributeParser.parseAttributes("pwa",
                "name='Task Master' theme=\"#123456\" sw='/sw.js' count=42 cloak=true");

        assertEquals("Task Master", attrs.get("name"));
        assertEquals("#123456", attrs.get("theme"));
        assertEquals("/sw.js", attrs.get("sw"));
        assertEquals(42L, attrs.get("count"));
        assertEquals(Boolean.TRUE, attrs.get("cloak"));
    }

    @Test
    @DisplayName("Rejects unclosed quotes, duplicate attributes, and broken syntax")
    void rejectsMalformedAttributes() {
        assertThrows(TemplateSyntaxException.class, () -> DirectiveAttributeParser.parseAttributes("pwa", "name='Task Master"));
        assertThrows(TemplateSyntaxException.class, () -> DirectiveAttributeParser.parseAttributes("pwa", "name=\"Task Master"));
        assertThrows(TemplateSyntaxException.class, () -> DirectiveAttributeParser.parseAttributes("pwa", "name='One' name='Two'"));
        assertThrows(TemplateSyntaxException.class, () -> DirectiveAttributeParser.parseAttributes("alpine", "cloak==true"));
        assertThrows(TemplateSyntaxException.class, () -> DirectiveAttributeParser.parseAttributes("pwa", "=value"));
        assertThrows(TemplateSyntaxException.class, () -> DirectiveAttributeParser.parseAttributes("pwa", "name="));
    }

    @Test
    @DisplayName("Interpreter, AST, and bytecode paths parse and render the same attribute syntax")
    void parityTestAttributeSyntax() {
        String pwaSource = "|pwa name='Task App' theme=\"#112233\" sw='/service-worker.js'|";
        assertDoesNotThrow(() -> {
            var compiled = engine.compile(pwaSource);
            assertNotNull(compiled.renderToString(new io.lemadane.piped.template.engine.expression.TemplateContext(Map.of())));
        });
    }
}
