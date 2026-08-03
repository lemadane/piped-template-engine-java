package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.codegen.CompiledTemplateExecutable;
import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeMacroTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Macro renders correctly in bytecode and AST modes")
    void testMacroRendering() throws Exception {
        String template = """
            |macro badge(text)|
                <b>|text|</b>
            |/macro|
            |call badge('OK')|
            """;

        String htmlString = engine.renderString(template, Map.of());
        assertTrue(htmlString.contains("<b>OK</b>"));

        CompiledTemplateExecutable executable = engine.compileToBytecode(template);
        StringWriter sw = new StringWriter();
        executable.render(new TemplateContext(Map.of()), sw, engine);
        assertTrue(sw.toString().contains("<b>OK</b>"));
    }

    @Test
    @DisplayName("Nested macro calls work correctly")
    void testNestedMacroCalls() throws Exception {
        String template = """
            |macro outer(msg)|
                <div>|call inner(msg)|</div>
            |/macro|
            |macro inner(val)|
                <span>|val|</span>
            |/macro|
            |call outer('Hello')|
            """;

        String html = engine.renderString(template, Map.of());
        assertTrue(html.replaceAll("\\s+", "").contains("<div><span>Hello</span></div>"));
    }

    @Test
    @DisplayName("Unknown macro throws clear TemplateRenderException")
    void testUnknownMacroThrows() {
        String template = "|call nonExistentMacro()|";
        assertThrows(TemplateRenderException.class, () -> engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Infinite macro recursion is rejected with recursion depth error")
    void testMacroRecursionLimit() {
        String template = """
            |macro recurse()|
                |call recurse()|
            |/macro|
            |call recurse()|
            """;
        assertThrows(TemplateRenderException.class, () -> engine.renderString(template, Map.of()));
    }
}
