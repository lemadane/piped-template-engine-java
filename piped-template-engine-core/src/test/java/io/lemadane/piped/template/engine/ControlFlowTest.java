package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ControlFlowTest {
    private final TemplateEngine engine = new TemplateEngine();

    @Nested
    @DisplayName("Continue directive tests")
    class ContinueTest {
        @Test
        @DisplayName("Continue during middle iteration skips remaining iteration body")
        void continueMiddleIteration() {
            String template = """
                |for i from 1 to 5|
                    |if i == 3|
                        |continue|
                    |/if|
                    [|i|]
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("[1] [2] [4] [5]", result);
        }

        @Test
        @DisplayName("Continue during first iteration")
        void continueFirstIteration() {
            String template = """
                |for i from 1 to 3|
                    |if i == 1|
                        |continue|
                    |/if|
                    [|i|]
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("[2] [3]", result);
        }

        @Test
        @DisplayName("Continue during final iteration")
        void continueFinalIteration() {
            String template = """
                |for i from 1 to 3|
                    |if i == 3|
                        |continue|
                    |/if|
                    [|i|]
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("[1] [2]", result);
        }

        @Test
        @DisplayName("Content after continue in the same iteration does not render")
        void contentAfterContinueNotRendered() {
            String template = "|for i from 1 to 3||if i == 2|BEFORE|continue|AFTER|/if|[|i|]|/for|";
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("[1]BEFORE[3]", result);
        }
    }

    @Nested
    @DisplayName("Break directive tests")
    class BreakTest {
        @Test
        @DisplayName("Break during middle iteration terminates loop")
        void breakMiddleIteration() {
            String template = """
                |for i from 1 to 10|
                    |if i == 6|
                        |break|
                    |/if|
                    [|i|]
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("[1] [2] [3] [4] [5]", result);
        }

        @Test
        @DisplayName("Break during first iteration")
        void breakFirstIteration() {
            String template = """
                |for i from 1 to 5|
                    |if i == 1|
                        |break|
                    |/if|
                    [|i|]
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("", result);
        }

        @Test
        @DisplayName("Break during final iteration")
        void breakFinalIteration() {
            String template = """
                |for i from 1 to 3|
                    |if i == 3|
                        |break|
                    |/if|
                    [|i|]
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("[1] [2]", result);
        }

        @Test
        @DisplayName("Content after break in the same iteration does not render")
        void contentAfterBreakNotRendered() {
            String template = "|for i from 1 to 5||if i == 3|STOP|break|NO_RENDER|/if|[|i|]|/for|";
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("[1][2]STOP", result);
        }
    }

    @Nested
    @DisplayName("Nested loops and scoping")
    class NestedLoopTest {
        @Test
        @DisplayName("Inner loop break does not terminate outer loop")
        void innerLoopBreakDoesNotAffectOuterLoop() {
            String template = """
                |for i from 1 to 2|
                    OUT:|i|{
                    |for j from 1 to 5|
                        |if j == 3|
                            |break|
                        |/if|
                        IN:|j|
                    |/for|
                    }
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("OUT:1{ IN:1 IN:2 } OUT:2{ IN:1 IN:2 }", result);
        }

        @Test
        @DisplayName("Inner loop continue does not skip outer loop iteration")
        void innerLoopContinueDoesNotAffectOuterLoop() {
            String template = """
                |for i from 1 to 2|
                    OUT:|i|{
                    |for j from 1 to 3|
                        |if j == 2|
                            |continue|
                        |/if|
                        IN:|j|
                    |/for|
                    }
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("OUT:1{ IN:1 IN:3 } OUT:2{ IN:1 IN:3 }", result);
        }

        @Test
        @DisplayName("Supports control flow inside switch statement inside loop")
        void breakInsideSwitch() {
            String template = """
                |for i from 1 to 5|
                    |switch i|
                        |case 3|
                            |break|
                        |default|
                            |i|
                    |/switch|
                |/for|
                """;
            String result = compact(engine.renderString(template, Map.of()));
            assertEquals("1 2", result);
        }
    }

    @Nested
    @DisplayName("Each loop integration with control flow & else")
    class EachIntegrationTest {
        @Test
        @DisplayName("Each loop with non-empty collection")
        void eachNonEmpty() {
            String template = "|each x in list||x||else|EMPTY|/each|";
            String result = engine.renderString(template, Map.of("list", List.of("A", "B")));
            assertEquals("AB", result);
        }

        @Test
        @DisplayName("Each loop with empty collection renders else")
        void eachEmptyRendersElse() {
            String template = "|each x in list||x||else|EMPTY|/each|";
            String result = engine.renderString(template, Map.of("list", List.of()));
            assertEquals("EMPTY", result);
        }

        @Test
        @DisplayName("Each loop with null collection renders else")
        void eachNullRendersElse() {
            String template = "|each x in list||x||else|EMPTY|/each|";
            String result = engine.renderString(template, Map.of());
            assertEquals("EMPTY", result);
        }

        @Test
        @DisplayName("Each loop executing break after iteration started does NOT render else")
        void eachBreakDoesNotRenderElse() {
            String template = """
                |each item in items|
                    |if item == 'B'|
                        |break|
                    |/if|
                    |item|
                |else|
                    EMPTY
                |/each|
                """;
            String result = compact(engine.renderString(template, Map.of("items", List.of("A", "B", "C"))));
            assertEquals("A", result);
        }

        @Test
        @DisplayName("Each loop executing continue on every item does NOT render else")
        void eachContinueAllItemsDoesNotRenderElse() {
            String template = """
                |each item in items|
                    |continue|
                    |item|
                |else|
                    EMPTY
                |/each|
                """;
            String result = compact(engine.renderString(template, Map.of("items", List.of("A", "B"))));
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Misplaced directives and compilation errors")
    class SyntaxErrorTest {
        @Test
        @DisplayName("Throws error when continue is outside loop")
        void continueOutsideLoop() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|continue|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws error when break is outside loop")
        void breakOutsideLoop() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|break|", Map.of())
            );
        }

        @Test
        @DisplayName("Throws error when else is used outside loop or if")
        void elseOutsideBlock() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("Hello |else| World", Map.of())
            );
        }

        @Test
        @DisplayName("Throws error when multiple else blocks appear in one each loop")
        void duplicateElseInEach() {
            assertThrows(TemplateSyntaxException.class, () ->
                engine.renderString("|each x in list||x||else|e1|else|e2|/each|", Map.of("list", List.of()))
            );
        }
    }

    @Nested
    @DisplayName("Bytecode mode control flow")
    class BytecodeControlFlowTest {
        @Test
        @DisplayName("Bytecode mode handles continue and break in loops")
        void bytecodeBreakAndContinue() throws Exception {
            String template = """
                |for i from 1 to 5|
                    |if i == 2|
                        |continue|
                    |/if|
                    |if i == 4|
                        |break|
                    |/if|
                    |i|
                |/for|
                """;
            var executable = engine.compileToBytecode(template);
            StringWriter sw = new StringWriter();
            executable.render(new TemplateContext(Map.of()), sw, engine);
            assertEquals("1 3", compact(sw.toString()));
        }
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
