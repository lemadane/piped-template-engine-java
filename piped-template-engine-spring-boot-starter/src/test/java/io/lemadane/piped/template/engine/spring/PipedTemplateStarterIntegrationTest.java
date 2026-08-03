package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.spring.routing.PipedFileRouteHandlerMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PipedTemplateStarterIntegrationTest.TestApplication.class,
        properties = {
                "spring.pipedtemplate.prefix=classpath:/pte-templates/",
                "spring.pipedtemplate.suffix=.pte",
                "spring.pipedtemplate.routing.fail-fast=true"
        }
)
class PipedTemplateStarterIntegrationTest {

    @SpringBootApplication
    static class TestApplication {

        @Controller
        static class SampleController {
            @GetMapping("/test-boot-controller")
            String testController(Model model) {
                model.addAttribute("title", "Boot Integration");
                return "title-test";
            }
        }
    }

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    TemplateEngine templateEngine;

    @Autowired
    PipedTemplateViewResolver viewResolver;

    @Autowired
    PipedFileRouteHandlerMapping fileRouteHandlerMapping;

    @Autowired
    PipedTemplateProperties properties;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("Starter auto-configuration is discovered automatically via AutoConfiguration.imports")
    void starterIsDiscoveredAutomatically() {
        assertNotNull(templateEngine);
        assertNotNull(viewResolver);
        assertNotNull(fileRouteHandlerMapping);
    }

    @Test
    @DisplayName("PipedTemplateProperties binds configuration properties from environment")
    void testPropertyBinding() {
        assertEquals("classpath:/pte-templates/", properties.getPrefix());
        assertEquals(".pte", properties.getSuffix());
        assertTrue(properties.getRouting().isFailFast());
    }

    @Test
    @DisplayName("MockMvc renders controller template using automatically discovered starter beans")
    void testControllerRendering() throws Exception {
        mockMvc.perform(get("/test-boot-controller"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Boot Integration</title>")));
    }

    @Test
    @DisplayName("File routes from classpath:/pte-routes are automatically discovered and rendered")
    void testFileRouteDiscoveryAndRendering() throws Exception {
        mockMvc.perform(get("/parity"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard")));
    }

    @Test
    @DisplayName("Metadata headers are applied to responses")
    void testMetadataHeadersApplied() throws Exception {
        mockMvc.perform(get("/parity"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("User-supplied replacement beans override default starter auto-configuration beans")
    void testUserCustomBeansReplacement() {
        @Configuration
        @SpringBootApplication
        static class CustomBeansApp {
            @Bean
            TemplateEngine customEngine() {
                return new TemplateEngine();
            }
        }

        try (ConfigurableApplicationContext customCtx = new SpringApplicationBuilder(CustomBeansApp.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .properties("spring.pipedtemplate.prefix=classpath:/pte-templates/")
                .run()) {

            TemplateEngine engine = customCtx.getBean(TemplateEngine.class);
            TemplateEngine customEngine = customCtx.getBean("customEngine", TemplateEngine.class);
            assertSame(customEngine, engine);
        }
    }

    @Test
    @DisplayName("Fail-fast mode prevents application startup when invalid route resources exist")
    void testStartupFailureForMalformedRoutes() {
        @Configuration
        @SpringBootApplication
        static class InvalidRoutesApp {
            @Bean
            PipedFileRouteHandlerMapping customMapping(TemplateEngine engine) {
                PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine, true);
                mapping.convertToSpringUrlPattern("posts/[id/+page.pte", new org.springframework.core.io.ByteArrayResource(new byte[0]));
                return mapping;
            }
        }

        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext invalidCtx = new SpringApplicationBuilder(InvalidRoutesApp.class)
                    .web(org.springframework.boot.WebApplicationType.NONE)
                    .run()) {
                assertNotNull(invalidCtx);
            }
        });
    }
}
