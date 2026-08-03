package io.lemadane.piped.template.engine.spring;

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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PwaSpringMvcAndFileRouteIntegrationTest {

    @Configuration
    @EnableWebMvc
    static class TestAppConfig {
        @Controller
        static class SamplePwaController {
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
    @DisplayName("Bundled external PWA script asset /pte-assets/pwa-register.js returns HTTP 200 OK")
    void testBundledExternalScriptAssetReturns200() throws Exception {
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
    @DisplayName("Spring MVC Controller view renders inline PWA JavaScript according to Spring configuration")
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
    @DisplayName("Spring MVC Controller view renders custom external registration script according to Spring configuration")
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
    @DisplayName("Spring MVC Controller view enforces required nonce for inline PWA mode")
    void testControllerViewEnforcesNonceRequirement() throws Exception {
        AnnotationConfigWebApplicationContext ctx = createContext(
                "spring.pipedtemplate.prefix=classpath:/pte-templates/",
                "spring.pipedtemplate.pwa.registration-mode=inline",
                "spring.pipedtemplate.pwa.require-nonce-for-inline=true"
        );
        try {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build();

            // View without nonce throws ServletException wrapping TemplateSyntaxException
            assertThrows(Exception.class, () -> mockMvc.perform(get("/pwa-view")));

            // View with nonce succeeds
            mockMvc.perform(get("/pwa-nonce-view"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<script nonce=\"testNonce\">if('serviceWorker' in navigator)")));
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

            // Route without nonce fails (SafeTemplateErrorHandler catches exception and returns HTTP 500)
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
}
