package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveNameValidationTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Rejects empty or invalid macro names")
    void rejectsInvalidMacroNames() {
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|macro ()|Content|/macro|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|macro 123name()|Content|/macro|"));
    }

    @Test
    @DisplayName("Rejects empty or invalid fragment names")
    void rejectsInvalidFragmentNames() {
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|fragment |Content|/fragment|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|fragment 123bad|Content|/fragment|"));
    }

    @Test
    @DisplayName("Rejects empty or invalid section and slot names")
    void rejectsInvalidSectionAndSlotNames() {
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|layout layouts/main|\n|section |Content|/section|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|component comp|\n|slot |Content|/slot|\n|/component|"));
    }

    @Test
    @DisplayName("Rejects invalid include, layout, and component template paths")
    void rejectsInvalidTemplatePaths() {
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|include |"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|include /absolute/path|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|include ../relative/path|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|include foo//bar|"));

        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|layout |"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|layout /abs/layout|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|layout ../layout|"));

        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|component |"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|component /abs/comp|"));
    }

    @Test
    @DisplayName("Rejects empty recover variable name")
    void rejectsEmptyRecoverVar() {
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|attempt|Content|recover as |Error|/attempt|"));
    }

    @Test
    @DisplayName("Rejects empty each or for loop variable name")
    void rejectsEmptyLoopVar() {
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|each in items|Item|/each|"));
        assertThrows(TemplateSyntaxException.class, () -> engine.compile("|for from 1 to 5|Num|/for|"));
    }

    @Test
    @DisplayName("Accepts positive names containing underscores and hyphens")
    void acceptsValidIdentifiersWithUnderscoresAndHyphens() {
        assertDoesNotThrow(() -> engine.compile("|macro my_custom-macro()|Hello|/macro|"));
        assertDoesNotThrow(() -> engine.compile("|fragment sub_nav-fragment|Nav|/fragment|"));
        assertDoesNotThrow(() -> engine.compile("|include partials/user_profile-header|"));
    }
}
