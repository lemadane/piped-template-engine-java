package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnknownDirectiveTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Rejects unknown directives and suggests correct spelling for misspellings")
    void rejectsUnknownDirectivesAndSuggestsCorrection() {
        TemplateSyntaxException ex1 = assertThrows(TemplateSyntaxException.class, () -> engine.compile("|inculde partials/header|"));
        assertTrue(ex1.getMessage().contains("Did you mean '|include|'?"));

        TemplateSyntaxException ex2 = assertThrows(TemplateSyntaxException.class, () -> engine.compile("|ifx active|"));
        assertTrue(ex2.getMessage().contains("Did you mean '|if|'?"));

        TemplateSyntaxException ex3 = assertThrows(TemplateSyntaxException.class, () -> engine.compile("|swtich status|"));
        assertTrue(ex3.getMessage().contains("Did you mean '|switch|'?"));

        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|wat nonsense|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|name unexpectedToken|"));
    }

    @Test
    @DisplayName("Valid expressions continue to compile and render successfully")
    void validExpressionsCompileSuccessfully() {
        assertDoesNotThrow(() -> engine.compile("|name|"));
        assertDoesNotThrow(() -> engine.compile("|user.profile.displayName|"));
        assertDoesNotThrow(() -> engine.compile("|user?.profile?.displayName ?? 'Guest'|"));
        assertDoesNotThrow(() -> engine.compile("|price * quantity|"));
        assertDoesNotThrow(() -> engine.compile("|active ? 'Yes' : 'No'|"));
        assertDoesNotThrow(() -> engine.compile("|html trustedContent|"));
        assertDoesNotThrow(() -> engine.compile("|attr user.name|"));
        assertDoesNotThrow(() -> engine.compile("|json settings|"));
        assertDoesNotThrow(() -> engine.compile("|url query|"));
    }

    @Test
    @DisplayName("Does not break legitimate variable names resembling directive names")
    void doesNotBreakLegitimateVariableNames() {
        assertDoesNotThrow(() -> engine.compile("|includePath|"));
        assertDoesNotThrow(() -> engine.compile("|pageTitle|"));
        assertDoesNotThrow(() -> engine.compile("|layoutName|"));
    }
}
