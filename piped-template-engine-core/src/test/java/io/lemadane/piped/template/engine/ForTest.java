package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ForTest {
    final TemplateEngine engine = new TemplateEngine();

    @Nested
    @DisplayName("Range-based for loops")
    class RangeForTest {
        @Test
        @DisplayName("Ascending range with default step")
        void ascendingRangeDefaultStep() {
            String template = "|for i from 1 to 5||i|,|/for|";
            String result = engine.renderString(template, Map.of());
            assertEquals("1,2,3,4,5,", result);
        }

        @Test
        @DisplayName("Ascending range with custom step")
        void ascendingRangeCustomStep() {
            String template = "|for i from 1 to 10 step 2||i|,|/for|";
            String result = engine.renderString(template, Map.of());
            assertEquals("1,3,5,7,9,", result);
        }

        @Test
        @DisplayName("Descending range with custom step reaching ending value exactly")
        void descendingRangeReachingEnd() {
            String template = "|for i from 10 to 1 step 3||i|,|/for|";
            String result = engine.renderString(template, Map.of());
            assertEquals("10,7,4,1,", result);
        }

        @Test
        @DisplayName("Descending range with custom step skipping unreachable ending value")
        void descendingRangeUnreachableEnd() {
            String template = "|for i from 10 to 1 step 2||i|,|/for|";
            String result = engine.renderString(template, Map.of());
            assertEquals("10,8,6,4,2,", result);
        }

        @Test
        @DisplayName("Equal start and end produces single iteration")
        void equalStartAndEnd() {
            String template = "|for i from 5 to 5||i||/for|";
            String result = engine.renderString(template, Map.of());
            assertEquals("5", result);
        }

        @Test
        @DisplayName("Expression-based start, end, and step")
        void expressionBasedRange() {
            String template = "|for i from startIndex to items.size() - 1 step interval|<span>|i|</span>|/for|";
            Map<String, Object> model = Map.of(
                "startIndex", 0,
                "items", List.of("a", "b", "c", "d", "e"),
                "interval", 2
            );
            String result = engine.renderString(template, model);
            assertEquals("<span>0</span><span>2</span><span>4</span>", result);
        }

        @Test
        @DisplayName("Bytecode mode supports range for loop")
        void bytecodeModeForLoop() throws Exception {
            String template = "|for i from 1 to 3||i||/for|";
            var executable = engine.compileToBytecode(template);
            StringWriter sw = new StringWriter();
            executable.render(new TemplateContext(Map.of()), sw, engine);
            assertEquals("123", sw.toString());
        }
    }

    @Nested
    @DisplayName("Empty-state else semantics")
    class EmptyStateElseTest {
        @Test
        @DisplayName("Does not execute else when loop runs 1 or more times")
        void doesNotExecuteElseOnIteration() {
            String template = "|for i from 1 to 3|<span>|i|</span>|else|<p>Empty</p>|/for|";
            String result = engine.renderString(template, Map.of());
            assertEquals("<span>1</span><span>2</span><span>3</span>", result);
        }

        @Test
        @DisplayName("Loop variable scope does not leak to else block or outer scope")
        void loopVariableScopeIsolation() {
            String template = "|for i from 1 to 3||i||/for||i ?? 'outer'|";
            String result = engine.renderString(template, Map.of());
            assertEquals("123outer", result);
        }
    }

    @Nested
    @DisplayName("Syntax and runtime errors")
    class ErrorHandlingTest {
        @Test
        @DisplayName("Throws error for zero step")
        void throwsForZeroStep() {
            assertThrows(TemplateRenderException.class, () ->
                engine.renderString("|for i from 1 to 5 step 0||i||/for|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws error for negative step")
        void throwsForNegativeStep() {
            assertThrows(TemplateRenderException.class, () ->
                engine.renderString("|for i from 1 to 5 step -1||i||/for|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws syntax error when loop variable is missing")
        void throwsMissingVariable() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|for from 1 to 5||/for|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws syntax error when from keyword is missing")
        void throwsMissingFrom() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|for i 1 to 5||/for|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws syntax error when to keyword is missing")
        void throwsMissingTo() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|for i from 1 step 2||/for|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws syntax error when /for closing tag is missing")
        void throwsUnclosedFor() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|for i from 1 to 5|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws syntax error for multiple else blocks in one for loop")
        void throwsMultipleElseInFor() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|for i from 1 to 5||i||else|e1|else|e2|/for|", Map.of())
            );
        }
    }
}
