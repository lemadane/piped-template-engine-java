package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnclosedBlockValidationTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Rejects unclosed if block with line and column position")
    void testUnclosedIf() {
        TemplateSyntaxException ex = assertThrows(
            TemplateSyntaxException.class,
            () -> engine.renderString("\n\n  |if true|\n    Body", Map.of())
        );
        assertTrue(ex.getMessage().contains("Unclosed |if|"));
        assertTrue(ex.getMessage().contains("line 3"));
        assertTrue(ex.getMessage().contains("expected |/if|"));
    }

    @Test
    @DisplayName("Rejects unclosed macro block")
    void testUnclosedMacro() {
        TemplateSyntaxException ex = assertThrows(
            TemplateSyntaxException.class,
            () -> engine.renderString("|macro example()|\n    Body", Map.of())
        );
        assertTrue(ex.getMessage().contains("Unclosed |macro|"));
        assertTrue(ex.getMessage().contains("expected |/macro|"));
    }

    @Test
    @DisplayName("Rejects unclosed fragment block")
    void testUnclosedFragment() {
        TemplateSyntaxException ex = assertThrows(
            TemplateSyntaxException.class,
            () -> engine.renderString("|fragment content|\n    Body", Map.of())
        );
        assertTrue(ex.getMessage().contains("Unclosed |fragment|"));
        assertTrue(ex.getMessage().contains("expected |/fragment|"));
    }

    @Test
    @DisplayName("Rejects unclosed minify block")
    void testUnclosedMinify() {
        TemplateSyntaxException ex = assertThrows(
            TemplateSyntaxException.class,
            () -> engine.renderString("|minify|\n    Body", Map.of())
        );
        assertTrue(ex.getMessage().contains("Unclosed |minify|"));
        assertTrue(ex.getMessage().contains("expected |/minify|"));
    }

    @Test
    @DisplayName("Rejects unclosed each loop")
    void testUnclosedEach() {
        TemplateSyntaxException ex = assertThrows(
            TemplateSyntaxException.class,
            () -> engine.renderString("|each item in items|\n    Item", Map.of("items", java.util.List.of("1")))
        );
        assertTrue(ex.getMessage().contains("Unclosed |each|"));
    }

    @Test
    @DisplayName("Rejects unclosed for loop")
    void testUnclosedFor() {
        TemplateSyntaxException ex = assertThrows(
            TemplateSyntaxException.class,
            () -> engine.renderString("|for i from 1 to 5|\n    Item |i|", Map.of())
        );
        assertTrue(ex.getMessage().contains("Unclosed |for|"));
    }

    @Test
    @DisplayName("Rejects empty macro name")
    void testEmptyMacroName() {
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString("|macro ()|Body|/macro|", Map.of()));
    }

    @Test
    @DisplayName("Rejects empty fragment name")
    void testEmptyFragmentName() {
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString("|fragment |Body|/fragment|", Map.of()));
    }
}
