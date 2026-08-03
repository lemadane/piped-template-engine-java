package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PWACspCompatibilityTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("PWA directive with registration-script renders external script tag compliant with strict CSP script-src 'self'")
    void testExternalPwaRegistrationScript() {
        String template = "|pwa sw='/sw.js' registration-script='/pte-assets/pwa-register.js'|";
        String html = engine.renderTemplateSource(template, Map.of()).html();

        assertTrue(html.contains("<script src=\"/pte-assets/pwa-register.js\" data-pte-service-worker=\"/sw.js\" defer></script>"));
        assertFalse(html.contains("if('serviceWorker' in navigator)"), "Must not include inline script body when external registration script is configured");
    }

    @Test
    @DisplayName("PWA directive with nonce attribute renders script tag with CSP nonce")
    void testNoncePwaInlineRegistration() {
        String template = "|pwa sw='/sw.js' nonce='rAnd0mN0nc3'|";
        String html = engine.renderTemplateSource(template, Map.of()).html();

        assertTrue(html.contains("<script nonce=\"rAnd0mN0nc3\">if('serviceWorker' in navigator)"));
    }
}
