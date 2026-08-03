package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PwaCspIntegrationTest {

    @Configuration
    @EnableWebMvc
    static class TestAppConfig {
    }

    AnnotationConfigWebApplicationContext createContext(String... envProperties) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestAppConfig.class, PipedTemplateAutoConfiguration.class);
        if (envProperties.length > 0) {
            TestPropertyValues.of(envProperties).applyTo(context);
        }
        context.refresh();
        return context;
    }

    @Test
    @DisplayName("Default external PWA script asset /pte-assets/pwa-register.js returns HTTP 200 OK")
    void testDefaultExternalScriptReturns200() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.prefix=classpath:/pte-templates/",
                "spring.pipedtemplate.pwa.registration-mode=external"
        );
        try {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();
            mockMvc.perform(get("/pte-assets/pwa-register.js"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("serviceWorker")));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("External mode generates script tag with configured script URL and preserves sw query params")
    void testExternalModeGeneratesConfiguredScriptUrl() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=external",
                "spring.pipedtemplate.pwa.registration-script=/custom-assets/sw-loader.js"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            String html = engine.renderString("|pwa sw='/sw.js?v=1&scope=/app'|", Map.of());

            assertTrue(html.contains("<script src=\"/custom-assets/sw-loader.js\" data-pte-service-worker=\"/sw.js?v=1&scope=/app\" defer></script>"));
            assertFalse(html.contains("&amp;scope="));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Inline mode generates inline JavaScript")
    void testInlineModeGeneratesInlineJs() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=false"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            String html = engine.renderString("|pwa sw='/sw.js'|", Map.of());

            assertTrue(html.contains("<script>if('serviceWorker' in navigator)"));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Required nonce for inline mode throws exception when nonce is missing")
    void testRequiredNonceWithoutNonceIsRejected() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=true"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            assertThrows(TemplateSyntaxException.class, () -> engine.renderString("|pwa sw='/sw.js'|", Map.of()));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Inline mode with nonce succeeds and renders nonce attribute")
    void testInlineModeWithNonceSucceeds() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=true"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            String html = engine.renderString("|pwa sw='/sw.js' nonce='secret123'|", Map.of());

            assertTrue(html.contains("<script nonce=\"secret123\">if('serviceWorker' in navigator)"));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Invalid registration mode causes application context refresh failure")
    void testInvalidRegistrationModeFailsStartup() {
        assertThrows(Exception.class, () -> {
            AnnotationConfigWebApplicationContext ctx = createContext("spring.pipedtemplate.pwa.registration-mode=invalid");
            ctx.close();
        });
    }

    @Test
    @DisplayName("Template attributes override Spring application configuration")
    void testTemplateAttributesOverrideSpringConfig() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=external",
                "spring.pipedtemplate.pwa.registration-script=/default.js"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            String html = engine.renderString("|pwa sw='/sw.js' mode='inline' nonce='overrideNonce'|", Map.of());

            assertTrue(html.contains("<script nonce=\"overrideNonce\">if('serviceWorker' in navigator)"));
        } finally {
            ctx.close();
        }
    }
}
