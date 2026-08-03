package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.spring.routing.PipedFileRouteHandlerMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
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
        @Controller
        static class SampleController {
            @GetMapping("/pwa-view")
            String pwaView() {
                return "pwa-view";
            }

            @GetMapping("/pwa-nonce-view")
            String pwaNonceView() {
                return "pwa-nonce-view";
            }
        }
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
    @DisplayName("Spring MVC Controller view renders inline PWA JavaScript via MockMvc according to Spring configuration")
    void testControllerViewRendersInlinePwaJs() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.prefix=classpath:/pte-templates/",
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=false"
        );
        try {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();
            mockMvc.perform(get("/pwa-view"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<script>if('serviceWorker' in navigator)")));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Spring MVC Controller view renders custom external registration script via MockMvc")
    void testControllerViewRendersCustomExternalScript() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.prefix=classpath:/pte-templates/",
                "spring.pipedtemplate.pwa.registration-mode=external",
                "spring.pipedtemplate.pwa.registration-script=/custom-assets/register.js"
        );
        try {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();
            mockMvc.perform(get("/pwa-view"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<script src=\"/custom-assets/register.js\" data-pte-service-worker=\"/sw.js\" defer></script>")));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Spring MVC Controller view enforces required nonce for inline PWA mode via MockMvc")
    void testControllerViewEnforcesNonceRequirement() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.prefix=classpath:/pte-templates/",
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=true"
        );
        try {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();

            // Missing nonce fails
            assertThrows(Exception.class, () -> mockMvc.perform(get("/pwa-view")));

            // Valid nonce succeeds
            mockMvc.perform(get("/pwa-nonce-view"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<script nonce=\"testNonce\">if('serviceWorker' in navigator)")));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("External mode generates script tag with configured script URL across renderString and renderTemplateSource")
    void testExternalModeGeneratesConfiguredScriptUrl() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=external",
                "spring.pipedtemplate.pwa.registration-script=/custom-assets/sw-loader.js"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            String pwaDirective = "|pwa sw='/sw.js?v=1&scope=/app'|";

            String htmlDirect = engine.renderString(pwaDirective, Map.of());
            assertTrue(htmlDirect.contains("<script src=\"/custom-assets/sw-loader.js\" data-pte-service-worker=\"/sw.js?v=1&scope=/app\" defer></script>"));

            String htmlSource = engine.renderTemplateSource(pwaDirective, Map.of()).html();
            assertTrue(htmlSource.contains("<script src=\"/custom-assets/sw-loader.js\" data-pte-service-worker=\"/sw.js?v=1&scope=/app\" defer></script>"));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Inline mode generates inline JavaScript across renderString and renderTemplateSource")
    void testInlineModeGeneratesInlineJs() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=false"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            String pwaDirective = "|pwa sw='/sw.js'|";

            String htmlDirect = engine.renderString(pwaDirective, Map.of());
            assertTrue(htmlDirect.contains("<script>if('serviceWorker' in navigator)"));

            String htmlSource = engine.renderTemplateSource(pwaDirective, Map.of()).html();
            assertTrue(htmlSource.contains("<script>if('serviceWorker' in navigator)"));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("Required nonce for inline mode throws exception when nonce is missing across renderTemplateSource")
    void testRequiredNonceWithoutNonceIsRejected() {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=true"
        );
        try {
            TemplateEngine engine = ctx.getBean(TemplateEngine.class);
            String pwaDirective = "|pwa sw='/sw.js'|";

            assertThrows(TemplateSyntaxException.class, () -> engine.renderString(pwaDirective, Map.of()));
            assertThrows(TemplateSyntaxException.class, () -> engine.renderTemplateSource(pwaDirective, Map.of()));
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
    @DisplayName("File-based route renders inline PWA JavaScript according to Spring configuration")
    void testFileRouteRendersInlinePwaJs() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=false"
        );
        try {
            PipedFileRouteHandlerMapping mapping = ctx.getBean(PipedFileRouteHandlerMapping.class);
            mapping.registerFileRoute("/file-pwa-inline", "pte-routes/file-pwa-inline/+page.pte",
                    new ByteArrayResource("|pwa sw='/sw.js'|\n<h1>Inline PWA Page</h1>".getBytes()));

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();
            mockMvc.perform(get("/file-pwa-inline"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<script>if('serviceWorker' in navigator)")));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("File-based route renders custom external registration script according to Spring configuration")
    void testFileRouteRendersCustomExternalScript() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=external",
                "spring.pipedtemplate.pwa.registration-script=/my-custom/sw-reg.js"
        );
        try {
            PipedFileRouteHandlerMapping mapping = ctx.getBean(PipedFileRouteHandlerMapping.class);
            mapping.registerFileRoute("/file-pwa-ext", "pte-routes/file-pwa-ext/+page.pte",
                    new ByteArrayResource("|pwa sw='/sw.js'|\n<h1>External PWA Page</h1>".getBytes()));

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();
            mockMvc.perform(get("/file-pwa-ext"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<script src=\"/my-custom/sw-reg.js\" data-pte-service-worker=\"/sw.js\" defer></script>")));
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("File-based route enforces required nonce for inline PWA mode")
    void testFileRouteEnforcesNonceRequirement() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=true"
        );
        try {
            PipedFileRouteHandlerMapping mapping = ctx.getBean(PipedFileRouteHandlerMapping.class);
            mapping.registerFileRoute("/file-pwa-no-nonce", "pte-routes/file-pwa-no-nonce/+page.pte",
                    new ByteArrayResource("|pwa sw='/sw.js'|\n<h1>No Nonce Page</h1>".getBytes()));

            mapping.registerFileRoute("/file-pwa-with-nonce", "pte-routes/file-pwa-with-nonce/+page.pte",
                    new ByteArrayResource("|pwa sw='/sw.js' nonce='fileNonce123'|\n<h1>With Nonce Page</h1>".getBytes()));

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();

            // Route without nonce fails (SafeTemplateErrorHandler returns HTTP 500)
            mockMvc.perform(get("/file-pwa-no-nonce"))
                    .andExpect(status().isInternalServerError());

            // Route with nonce succeeds
            mockMvc.perform(get("/file-pwa-with-nonce"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<script nonce=\"fileNonce123\">if('serviceWorker' in navigator)")));
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
