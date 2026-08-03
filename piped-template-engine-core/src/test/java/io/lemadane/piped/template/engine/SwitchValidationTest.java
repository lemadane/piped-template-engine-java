package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwitchValidationTest {
    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Valid switch renders matching case and fallthrough")
    void testValidSwitch() {
        String template = """
            |switch role|
                |case 'admin'|
                    Admin
                    |fallthrough|
                |case 'manager'|
                    Manager
                |default|
                    Guest
            |/switch|
            """;

        String html = engine.renderString(template, Map.of("role", "admin"));
        assertTrue(html.contains("Admin"));
        assertTrue(html.contains("Manager"));
        assertFalse(html.contains("Guest"));
    }

    @Test
    @DisplayName("Rejects fallthrough outside switch")
    void testFallthroughOutsideSwitchRejection() {
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString("|fallthrough|", Map.of()));
    }

    @Test
    @DisplayName("Rejects fallthrough inside default block")
    void testFallthroughInDefaultRejection() {
        String template = """
            |switch value|
                |default|
                    Default
                    |fallthrough|
            |/switch|
            """;
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Rejects non-terminal fallthrough inside case")
    void testNonTerminalFallthroughRejection() {
        String template = """
            |switch value|
                |case 1|
                    A
                    |fallthrough|
                    B
                |case 2|
                    C
            |/switch|
            """;
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Rejects case after default block")
    void testCaseAfterDefaultRejection() {
        String template = """
            |switch value|
                |default|
                    Default
                |case 1|
                    Case 1
            |/switch|
            """;
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Rejects empty switch block")
    void testEmptySwitchRejection() {
        String template = "|switch value||/switch|";
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }

    @Test
    @DisplayName("Rejects unclosed switch block")
    void testUnclosedSwitchRejection() {
        String template = "|switch value||case 1|A";
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(template, Map.of()));
    }
}
