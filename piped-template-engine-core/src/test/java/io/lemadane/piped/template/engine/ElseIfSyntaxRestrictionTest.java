package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElseIfSyntaxRestrictionTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("|else if expr| is valid and renders correctly")
    void testElseIfValidSyntax() {
        String template = "|if x == 1|ONE|else if x == 2|TWO|else|OTHER|/if|";

        assertEquals("TWO", engine.renderString(template, Map.of("x", 2)));
        assertEquals("ONE", engine.renderString(template, Map.of("x", 1)));
        assertEquals("OTHER", engine.renderString(template, Map.of("x", 3)));
    }

    @Test
    @DisplayName("|else-if expr| is rejected and throws TemplateSyntaxException")
    void testElseIfHyphenatedRejected() {
        String template = "|if x == 1|ONE|else-if x == 2|TWO|else|OTHER|/if|";

        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of("x", 2)));
    }

    @Test
    @DisplayName("|elseif expr| is rejected and throws TemplateSyntaxException")
    void testElseIfCombinedRejected() {
        String template = "|if x == 1|ONE|elseif x == 2|TWO|else|OTHER|/if|";

        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of("x", 2)));
    }
}
