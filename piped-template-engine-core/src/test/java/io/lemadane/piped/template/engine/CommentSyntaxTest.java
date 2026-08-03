package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("Unclosed comment throws TemplateSyntaxException")
    void testUnclosedComment() {
        String template = "Hello |# unclosed comment";
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Old comment syntax |-- ... --| is rejected")
    void testOldCommentSyntaxRejected() {
        String template = "Hello |-- old comment --|World!";
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Comments in bytecode and AST compiled paths render nothing")
    void testParityCompiledComments() throws Exception {
        String template = "Start |# single | middle |# multi #| end";
        
        var compiled = engine.compile(template);
        String htmlAst = compiled.renderToString(new TemplateContext(Map.of()));
        assertEquals("Start  middle  end", htmlAst);

        var executable = engine.compileToBytecode(template);
        StringWriter sw = new StringWriter();
        executable.render(new TemplateContext(Map.of()), sw, engine);
        assertEquals("Start  middle  end", sw.toString());
    }
}
