package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsCssDirectiveTest {
    final TemplateEngine engine = new TemplateEngine(Map.of());

    @Nested
    @DisplayName("JavaScript Directives (|js expr| and |js|...|/js|)")
    class JavaScriptDirectiveTest {

        @Test
        @DisplayName("Renders single-line JS literal expression inside script tags")
        void rendersSingleLineJsLiteral() {
            String html = engine.renderString("|js 'console.log(\"Hello World\");'|", Map.of());
            assertEquals("<script>console.log(\"Hello World\");</script>", html);
        }

        @Test
        @DisplayName("Renders single-line JS model variable inside script tags")
        void rendersSingleLineJsVariable() {
            String html = engine.renderString("|js scriptCode|", Map.of("scriptCode", "alert('hi');"));
            assertEquals("<script>alert('hi');</script>", html);
        }

        @Test
        @DisplayName("Renders single-line JS raw expression")
        void rendersSingleLineJsRaw() {
            String html = engine.renderString("|js console.log(123)|", Map.of());
            assertEquals("<script>console.log(123)</script>", html);
        }

        @Test
        @DisplayName("Renders single-line JS with true condition")
        void rendersSingleLineJsConditionalTrue() {
            String html = engine.renderString("|js 'alert(1);' if active|", Map.of("active", true));
            assertEquals("<script>alert(1);</script>", html);
        }

        @Test
        @DisplayName("Renders empty string for single-line JS with false condition")
        void rendersSingleLineJsConditionalFalse() {
            String html = engine.renderString("|js 'alert(1);' if active|", Map.of("active", false));
            assertEquals("", html);
        }

        @Test
        @DisplayName("Renders multi-line JS block with template interpolation")
        void rendersMultiLineJsBlock() {
            String template = """
                |js|
                var name = '|userName|';
                console.log(name);
                |/js|
                """;
            String html = engine.renderString(template, Map.of("userName", "Alice"));
            assertTrue(html.trim().startsWith("<script>"));
            assertTrue(html.trim().endsWith("</script>"));
            assertTrue(html.contains("var name = 'Alice';"));
            assertTrue(html.contains("console.log(name);"));
        }

        @Test
        @DisplayName("Throws syntax error for unclosed |js| block")
        void throwsForUnclosedJsBlock() {
            assertThrows(TemplateSyntaxException.class, () -> engine.renderString("|js| console.log(1);", Map.of()));
        }

        @Test
        @DisplayName("Throws syntax error for stray |/js| tag")
        void throwsForStrayEndJsTag() {
            assertThrows(TemplateSyntaxException.class, () -> engine.renderString("console.log(1); |/js|", Map.of()));
        }
    }

    @Nested
    @DisplayName("CSS Directives (|css expr| and |css|...|/css|)")
    class CssDirectiveTest {

        @Test
        @DisplayName("Renders single-line CSS literal expression inside style tags")
        void rendersSingleLineCssLiteral() {
            String html = engine.renderString("|css 'body { color: red; }'|", Map.of());
            assertEquals("<style>body { color: red; }</style>", html);
        }

        @Test
        @DisplayName("Renders single-line CSS model variable inside style tags")
        void rendersSingleLineCssVariable() {
            String html = engine.renderString("|css styleContent|", Map.of("styleContent", "h1 { margin: 0; }"));
            assertEquals("<style>h1 { margin: 0; }</style>", html);
        }

        @Test
        @DisplayName("Renders single-line CSS raw expression")
        void rendersSingleLineCssRaw() {
            String html = engine.renderString("|css body { background: blue; }|", Map.of());
            assertEquals("<style>body { background: blue; }</style>", html);
        }

        @Test
        @DisplayName("Renders single-line CSS with true condition")
        void rendersSingleLineCssConditionalTrue() {
            String html = engine.renderString("|css 'body { color: green; }' if themeActive|", Map.of("themeActive", true));
            assertEquals("<style>body { color: green; }</style>", html);
        }

        @Test
        @DisplayName("Renders empty string for single-line CSS with false condition")
        void rendersSingleLineCssConditionalFalse() {
            String html = engine.renderString("|css 'body { color: green; }' if themeActive|", Map.of("themeActive", false));
            assertEquals("", html);
        }

        @Test
        @DisplayName("Renders multi-line CSS block with template interpolation")
        void rendersMultiLineCssBlock() {
            String template = """
                |css|
                .header {
                    color: |mainColor|;
                }
                |/css|
                """;
            String html = engine.renderString(template, Map.of("mainColor", "#ff0000"));
            assertTrue(html.trim().startsWith("<style>"));
            assertTrue(html.trim().endsWith("</style>"));
            assertTrue(html.contains("color: #ff0000;"));
        }

        @Test
        @DisplayName("Throws syntax error for unclosed |css| block")
        void throwsForUnclosedCssBlock() {
            assertThrows(TemplateSyntaxException.class, () -> engine.renderString("|css| body { color: red; }", Map.of()));
        }

        @Test
        @DisplayName("Throws syntax error for stray |/css| tag")
        void throwsForStrayEndCssTag() {
            assertThrows(TemplateSyntaxException.class, () -> engine.renderString("body { color: red; } |/css|", Map.of()));
        }
    }

    @Nested
    @DisplayName("Bytecode compilation parity for JS and CSS directives")
    class BytecodeParityTest {

        @Test
        @DisplayName("Compiled bytecode renders identical JS and CSS outputs")
        void testBytecodeParity() throws Exception {
            String template = "|js 'alert(1);'|\n|css|body { color: |color|; }|/css|";
            Map<String, Object> model = Map.of("color", "blue");

            String interpreted = engine.renderString(template, model);

            var executable = engine.compileToBytecode(template);
            StringWriter sw = new StringWriter();
            executable.render(new TemplateContext(model), sw, engine);
            String compiled = sw.toString();

            assertEquals(interpreted, compiled);
        }
    }
}
