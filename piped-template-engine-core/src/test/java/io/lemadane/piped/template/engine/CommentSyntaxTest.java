package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommentSyntaxTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Single-line comment |# ... | renders nothing")
    void testSingleLineComment() {
        String template = "Hello |# single line comment |World!";
        assertEquals("Hello World!", engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Multi-line comment |# ... #| renders nothing")
    void testMultiLineComment() {
        String template = "Hello |#\n multi line\n comment\n #|World!";
        assertEquals("Hello World!", engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Single-line comment containing # character renders nothing")
    void testSingleLineCommentWithHash() {
        String template = "Hello |# comment with #1 item |World!";
        assertEquals("Hello World!", engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Multiline comment suppresses internal directives, expressions, and pipes")
    void multilineCommentSuppressesDirectivesAndExpressions() throws Exception {
        String template = """
                A|#
                |include definitely-missing|
                |if malformed|
                |user.secret|
                #|B
                """;

        assertEquals("AB\n", engine.renderString(template, Map.of()));
        assertEquals("AB\n", engine.compile(template).renderToString(new TemplateContext(Map.of())));
        assertEquals("AB\n", engine.renderTemplateSource(template, Map.of()).html());

        var executable = engine.compileToBytecode(template);
        StringWriter sw = new StringWriter();
        executable.render(new TemplateContext(Map.of()), sw, engine);
        assertEquals("AB\n", sw.toString());
    }

    @Test
    @DisplayName("Single-line comment cannot cross newlines")
    void singleLineCommentCannotCrossNewline() {
        String template = "A|# comment\ncontinued|B";
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile(template).renderToString(new TemplateContext(Map.of())));
        assertThrows(TemplateSyntaxException.class, () -> engine.compileToBytecode(template));
        assertThrows(TemplateSyntaxException.class, () -> engine.renderTemplateSource(template, Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A|-- old --|B",
            "A|-- 1 --|B",
            "A|----|B"
    })
    @DisplayName("Old comment syntax |-- ... --| is always rejected across all rendering paths")
    void oldCommentSyntaxIsAlwaysRejected(String template) {
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile(template).renderToString(new TemplateContext(Map.of())));
        assertThrows(TemplateSyntaxException.class, () -> engine.compileToBytecode(template));
        assertThrows(TemplateSyntaxException.class, () -> engine.renderTemplateSource(template, Map.of()));
    }

    @Test
    @DisplayName("Unclosed comment throws TemplateSyntaxException")
    void testUnclosedComment() {
        String template = "Hello |# unclosed comment";
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }
}
