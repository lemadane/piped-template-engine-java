package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.compiler.CompiledTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttemptRecoverTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Renders attempt block normally if no error occurs")
    void rendersAttemptNormally() {
        String template = """
            |attempt|
                <p>Hello World</p>
            |recover|
                <p>Error fallback</p>
            |/attempt|
            """;

        String html = engine.renderString(template, Map.of());
        assertTrue(html.contains("Hello World"));
        assertFalse(html.contains("Error fallback"));
    }

    @Test
    @DisplayName("Rolls back partial output and renders recover block when exception occurs")
    void rollsBackAndRecovers() {
        String template = """
            <div>
                |attempt|
                    <p>Partial text before error</p>
                    |1 / 0|
                |recover as err|
                    <div class="error">Failed: |err|</div>
                |/attempt|
            </div>
            """;

        String html = engine.renderString(template, Map.of());
        assertFalse(html.contains("Partial text before error"));
        assertTrue(html.contains("Failed:"));
        assertTrue(html.contains("/ by zero") || html.contains("by zero") || html.contains("ArithmeticException"));
    }

    @Test
    @DisplayName("Supports optional recover block in interpreter and compiled modes")
    void supportsOptionalRecoverBlock() throws Exception {
        String template = """
            |attempt|
                <p>No recover block needed</p>
            |/attempt|
            """;

        String htmlInterpreter = engine.renderString(template, Map.of());
        assertTrue(htmlInterpreter.contains("No recover block needed"));

        CompiledTemplate compiled = engine.compile(template);
        java.io.StringWriter writer = new java.io.StringWriter();
        compiled.render(new io.lemadane.piped.template.engine.expression.TemplateContext(Map.of()), writer);
        assertTrue(writer.toString().contains("No recover block needed"));
    }

    @Test
    @DisplayName("Compiles and renders attempt/recover in bytecode mode")
    void compilesAndRendersAttemptRecoverInBytecodeMode() throws Exception {
        String template = """
            <div>
                |attempt|
                    <p>Before error</p>
                    |1 / 0|
                |recover as err|
                    <div class="error">Recovered: |err|</div>
                |/attempt|
            </div>
            """;

        CompiledTemplate compiled = engine.compile(template);
        java.io.StringWriter writer = new java.io.StringWriter();
        compiled.render(new io.lemadane.piped.template.engine.expression.TemplateContext(Map.of()), writer);
        String html = writer.toString();

        assertFalse(html.contains("Before error"));
        assertTrue(html.contains("Recovered:"));
    }

    @Test
    @DisplayName("Rethrows loop break and continue signals inside attempt blocks")
    void rethrowsLoopControlSignalsInAttempt() {
        String template = """
            |for i from 1 to 5|
                |attempt|
                    |if i == 3|
                        |break|
                    |/if|
                    Item |i|
                |recover|
                    Error
                |/attempt|
            |/for|
            """;

        String html = engine.renderString(template, Map.of());
        assertTrue(html.contains("Item 1"));
        assertTrue(html.contains("Item 2"));
        assertFalse(html.contains("Item 3"));
        assertFalse(html.contains("Item 4"));
        assertFalse(html.contains("Error"));
    }

    @Test
    @DisplayName("Throws syntax error when recover appears without matching attempt")
    void throwsSyntaxErrorForOrphanRecover() {
        assertThrows(
            io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException.class,
            () -> engine.renderString("|recover|", Map.of())
        );
    }
}
