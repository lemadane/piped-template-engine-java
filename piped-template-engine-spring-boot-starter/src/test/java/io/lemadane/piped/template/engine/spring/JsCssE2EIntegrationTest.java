package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.res.ClasspathTemplateSourceResolver;
import io.lemadane.piped.template.engine.spring.routing.PipedFileRouteHandlerMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JsCssE2EIntegrationTest {

    MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    static class TestConfig implements WebMvcConfigurer {

        @Bean
        TemplateEngine templateEngine() {
            return new TemplateEngine(new ClasspathTemplateSourceResolver("classpath:/", ".pte"));
        }

        @Bean
        PipedFileRouteHandlerMapping pipedFileRouteHandlerMapping(TemplateEngine engine) {
            PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine);

            // Register E2E route for JS directives
            String jsRouteTemplate = """
                |page title='JS E2E Test'|
                <h1>JS E2E Test</h1>
                |js 'console.log("inline-js-e2e");'|
                |js scriptVar if active|
                |js|
                function runE2E() {
                    console.log("App |appName| version |version|");
                }
                runE2E();
                |/js|
                """;
            mapping.registerFileRoute("/e2e/js", "pte-routes/e2e/js/+page.pte",
                    new ByteArrayResource(jsRouteTemplate.getBytes()));
            mapping.registerPageDataLoader("/e2e/js", req -> Map.of(
                    "scriptVar", "alert('active-mode');",
                    "active", true,
                    "appName", "PipedEngine",
                    "version", "1.0.0"
            ));

            // Register E2E route for CSS directives
            String cssRouteTemplate = """
                |page title='CSS E2E Test'|
                <h1>CSS E2E Test</h1>
                |css 'body { font-family: sans-serif; }'|
                |css styleVar if showTheme|
                |css|
                .main-card {
                    color: |primaryColor|;
                    background-color: |bgColor|;
                }
                |/css|
                """;
            mapping.registerFileRoute("/e2e/css", "pte-routes/e2e/css/+page.pte",
                    new ByteArrayResource(cssRouteTemplate.getBytes()));
            mapping.registerPageDataLoader("/e2e/css", req -> Map.of(
                    "styleVar", "h1 { text-transform: uppercase; }",
                    "showTheme", true,
                    "primaryColor", "#112233",
                    "bgColor", "#ffffff"
            ));

            return mapping;
        }

        @Bean
        E2EController e2eController() {
            return new E2EController();
        }

        @Override
        public void configureViewResolvers(ViewResolverRegistry registry) {
            PipedTemplateViewResolver resolver = new PipedTemplateViewResolver();
            resolver.setPrefix("pte-templates/");
            resolver.setSuffix(".pte");
            registry.viewResolver(resolver);
        }
    }

    @Controller
    static class E2EController {

        @GetMapping("/e2e/controller-js")
        String jsController(Model model) {
            model.addAttribute("scriptVar", "console.log('controller-script');");
            model.addAttribute("active", true);
            model.addAttribute("appName", "SpringApp");
            model.addAttribute("version", "2.0.0");
            return "pwa-view";
        }
    }

    @BeforeEach
    void setUp() {
        MockServletContext servletContext = new MockServletContext();
        AnnotationConfigWebApplicationContext wac = new AnnotationConfigWebApplicationContext();
        wac.setServletContext(servletContext);
        wac.register(TestConfig.class);
        wac.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("E2E File-route test: JS single-line expressions, conditionals, and multi-line block tags render inside script tags")
    void testEndToEndJsRoute() throws Exception {
        mockMvc.perform(get("/e2e/js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>JS E2E Test</h1>")))
                .andExpect(content().string(containsString("<script>console.log(\"inline-js-e2e\");</script>")))
                .andExpect(content().string(containsString("<script>alert('active-mode');</script>")))
                .andExpect(content().string(containsString("<script>")))
                .andExpect(content().string(containsString("function runE2E()")))
                .andExpect(content().string(containsString("console.log(\"App PipedEngine version 1.0.0\");")))
                .andExpect(content().string(containsString("</script>")));
    }

    @Test
    @DisplayName("E2E File-route test: CSS single-line expressions, conditionals, and multi-line block tags render inside style tags")
    void testEndToEndCssRoute() throws Exception {
        mockMvc.perform(get("/e2e/css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>CSS E2E Test</h1>")))
                .andExpect(content().string(containsString("<style>body { font-family: sans-serif; }</style>")))
                .andExpect(content().string(containsString("<style>h1 { text-transform: uppercase; }</style>")))
                .andExpect(content().string(containsString("<style>")))
                .andExpect(content().string(containsString(".main-card {")))
                .andExpect(content().string(containsString("color: #112233;")))
                .andExpect(content().string(containsString("background-color: #ffffff;")))
                .andExpect(content().string(containsString("</style>")));
    }
}
