package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.spring.routing.PipedFileRouteHandlerMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipedTemplateSpringBootApplicationTest {

    AnnotationConfigWebApplicationContext applicationContext;
    MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    @EnableAutoConfiguration
    static class TestAppConfig {

        @Controller
        static class SampleController {
            @GetMapping("/test-boot")
            String testBoot(Model model) {
                model.addAttribute("title", "Boot Controller");
                return "title-test";
            }
        }
    }

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.pipedtemplate.prefix", "classpath:/pte-templates/");
        env.setProperty("spring.pipedtemplate.suffix", ".pte");
        env.setProperty("spring.pipedtemplate.routing.fail-fast", "true");

        applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.setEnvironment(env);
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.register(TestAppConfig.class, PipedTemplateAutoConfiguration.class);
        applicationContext.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @AfterEach
    void tearDown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    @DisplayName("Auto-configuration registers TemplateEngine, PipedTemplateViewResolver, and PipedFileRouteHandlerMapping beans")
    void testAutoConfigurationRegistersBeans() {
        assertTrue(applicationContext.containsBean("pipedTemplateEngine"));
        assertTrue(applicationContext.containsBean("pipedTemplateViewResolver"));
        assertTrue(applicationContext.containsBean("pipedFileRouteHandlerMapping"));

        TemplateEngine engine = applicationContext.getBean(TemplateEngine.class);
        assertNotNull(engine);

        PipedTemplateProperties props = applicationContext.getBean(PipedTemplateProperties.class);
        assertEquals("classpath:/pte-templates/", props.getPrefix());
        assertEquals(".pte", props.getSuffix());
        assertTrue(props.getRouting().isFailFast());
    }

    @Test
    @DisplayName("Full Spring Boot boot test renders controller template and applies view resolution")
    void testControllerRenderingInSpringBoot() throws Exception {
        mockMvc.perform(get("/test-boot"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Boot Controller</title>")));
    }
}
