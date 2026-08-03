package io.lemadane.piped.template.engine.spring.routing;

import io.lemadane.piped.template.engine.RenderResult;
import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.spring.PipedResponseMetadataApplicator;
import io.lemadane.piped.template.engine.spring.PipedTemplateViewResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;

import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerFileRouteParityTest {

    MockMvc mockMvc;
    TemplateEngine templateEngine;
    PipedFileRouteHandlerMapping routeMapping;

    @Configuration
    @EnableWebMvc
    static class TestConfig implements WebMvcConfigurer {

        @Bean
        TemplateEngine templateEngine() {
            return new TemplateEngine(Path.of("src/test/resources/pte-templates"));
        }

        @Bean
        PipedFileRouteHandlerMapping pipedFileRouteHandlerMapping(TemplateEngine engine) {
            PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine);
            mapping.registerPageDataLoader("/parity", req -> Map.of(
                    "appName", "Piped Parity Test",
                    "user", Map.of("name", "Alice", "admin", true, "role", "ADMIN"),
                    "items", List.of("One", "Two", "Three"),
                    "product", Map.of("name", "Widget", "price", "$99")
            ));
            mapping.registerPageDataLoader("/comp-test", req -> Map.of("product", Map.of("name", "Widget")));
            mapping.registerPageDataLoader("/context-override", req -> Map.of("page", "Explicit Page Model"));
            mapping.registerPageDataLoader("/title-override", req -> Map.of("title", "Custom Title"));
            return mapping;
        }

        @Bean
        TestParityController testParityController() {
            return new TestParityController();
        }

        @Override
        public void configureViewResolvers(ViewResolverRegistry registry) {
            PipedTemplateViewResolver resolver = new PipedTemplateViewResolver();
            resolver.setPrefix("");
            resolver.setSuffix(".pte");
            registry.viewResolver(resolver);
        }
    }

    @Controller
    static class TestParityController {

        @GetMapping("/parity-controller")
        String parityController(Model model) {
            model.addAttribute("appName", "Piped Parity Test");
            model.addAttribute("user", Map.of("name", "Alice", "admin", true, "role", "ADMIN"));
            model.addAttribute("items", List.of("One", "Two", "Three"));
            model.addAttribute("product", Map.of("name", "Widget", "price", "$99"));
            return "parity/controller-page";
        }

        @GetMapping("/title-test-controller")
        String titleTestController() {
            return "title-test";
        }

        @GetMapping("/title-override-controller")
        String titleOverrideController(Model model) {
            model.addAttribute("title", "Custom Title");
            return "title-test";
        }

        @GetMapping("/context-controller")
        String contextController() {
            return "context/controller-page";
        }

        @GetMapping("/context-override-controller")
        String contextOverrideController(Model model) {
            model.addAttribute("page", "Explicit Page Model");
            return "context-override/controller-page";
        }
    }

    @BeforeEach
    void setUp() {
        MockServletContext servletContext = new MockServletContext();
        AnnotationConfigWebApplicationContext wac = new AnnotationConfigWebApplicationContext();
        wac.setServletContext(servletContext);
        wac.register(TestConfig.class);
        wac.refresh();

        templateEngine = wac.getBean(TemplateEngine.class);
        routeMapping = wac.getBean(PipedFileRouteHandlerMapping.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("Metadata title timing: title is available inside template during rendering via both controller and file route")
    void testMetadataTitleTiming() throws Exception {
        // 1. Controller path: metadata title 'Dashboard' is available during rendering
        mockMvc.perform(get("/title-test-controller"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Dashboard</title>")));

        // 2. File-route path: metadata title 'Dashboard' is available during rendering
        mockMvc.perform(get("/title-test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Dashboard</title>")));

        // 3. Explicitly supplied model title overrides metadata title in both controller and file route
        mockMvc.perform(get("/title-override-controller"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Custom Title</title>")));

        mockMvc.perform(get("/title-override"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Custom Title</title>")));
    }

    @Test
    @DisplayName("MockMvc Controller vs File-Route parity: byte-for-byte identical HTML and identical response headers")
    void testControllerAndFileRouteParity() throws Exception {
        // 1. Controller path via MockMvc
        MvcResult controllerResult = mockMvc.perform(get("/parity-controller"))
                .andExpect(status().isOk())
                .andReturn();
        String controllerHtml = controllerResult.getResponse().getContentAsString();

        // 2. File-route path via MockMvc
        MvcResult routeResult = mockMvc.perform(get("/parity"))
                .andExpect(status().isOk())
                .andReturn();
        String fileRouteHtml = routeResult.getResponse().getContentAsString();

        // 3. Byte-for-byte HTML Equality
        assertEquals(controllerHtml, fileRouteHtml, "Controller HTML and File Route HTML must be byte-for-byte identical");

        // 4. Meaningful content assertions
        assertTrue(controllerHtml.contains("Dashboard"), "Must contain rendered metadata title");
        assertTrue(controllerHtml.contains("<title>"), "Must contain title tag");
        assertTrue(controllerHtml.contains("<h1>Welcome Alice</h1>"), "Must contain condition-rendered heading");
        assertTrue(controllerHtml.contains("<p>Admin Panel</p>"), "Must contain admin panel");
        assertTrue(controllerHtml.contains("<span>Item 1</span>"), "Must contain for loop item");
        assertTrue(controllerHtml.contains("<div>One</div>"), "Must contain each loop item");
        assertTrue(controllerHtml.contains("<div>Role: Admin</div>"), "Must contain switch case output");
        assertTrue(controllerHtml.contains("Featured Product"), "Must contain component slot");
        assertTrue(controllerHtml.contains("<span class=\"badge\">Active</span>"), "Must contain macro call output");
        assertTrue(controllerHtml.contains("<p>Attempt content</p>"), "Must contain attempt block output");

        // 5. Response Header Equality
        var cResp = controllerResult.getResponse();
        var rResp = routeResult.getResponse();
        assertEquals(cResp.getContentType(), rResp.getContentType(), "Content-Type must match");
        assertEquals(cResp.getHeader("Cache-Control"), rResp.getHeader("Cache-Control"), "Cache-Control must match");
        assertEquals(cResp.getHeader("HX-Trigger"), rResp.getHeader("HX-Trigger"), "HX-Trigger must match");
        assertEquals(cResp.getHeader("HX-Redirect"), rResp.getHeader("HX-Redirect"), "HX-Redirect must match");
        assertEquals(cResp.getHeader("HX-Push-Url"), rResp.getHeader("HX-Push-Url"), "HX-Push-Url must match");
        assertEquals(cResp.getHeader("HX-Refresh"), rResp.getHeader("HX-Refresh"), "HX-Refresh must match");
    }

    @Test
    @DisplayName("Page-context parity through MockMvc: renders request properties and protects explicit page model key")
    void testPageContextParity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user_id", "user_123");

        MvcResult controllerResult = mockMvc.perform(get("/context-controller?q=search-term")
                        .header("x-custom", "custom-header-val")
                        .header("HX-Request", "true")
                        .header("HX-Target", "#target-element")
                        .header("HX-Trigger", "trigger-event")
                        .header("HX-Current-URL", "http://localhost/current")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult routeResult = mockMvc.perform(get("/context?q=search-term")
                        .header("x-custom", "custom-header-val")
                        .header("HX-Request", "true")
                        .header("HX-Target", "#target-element")
                        .header("HX-Trigger", "trigger-event")
                        .header("HX-Current-URL", "http://localhost/current")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        String controllerHtml = controllerResult.getResponse().getContentAsString();
        String routeHtml = routeResult.getResponse().getContentAsString();

        // requestUri naturally differs between routes, while query, method, param, header, session, HTMX render identically
        assertTrue(controllerHtml.contains("<p>URI:/context-controller</p>"));
        assertTrue(routeHtml.contains("<p>URI:/context</p>"));
        assertTrue(controllerHtml.contains("<p>Query:q=search-term</p>"));
        assertTrue(routeHtml.contains("<p>Query:q=search-term</p>"));
        assertTrue(controllerHtml.contains("<p>Method:GET</p>"));
        assertTrue(routeHtml.contains("<p>Method:GET</p>"));
        assertTrue(controllerHtml.contains("<p>Param:search-term</p>"));
        assertTrue(routeHtml.contains("<p>Param:search-term</p>"));
        assertTrue(controllerHtml.contains("<p>Header:custom-header-val</p>"));
        assertTrue(routeHtml.contains("<p>Header:custom-header-val</p>"));
        assertTrue(controllerHtml.contains("<p>Session:user_123</p>"));
        assertTrue(routeHtml.contains("<p>Session:user_123</p>"));
        assertTrue(controllerHtml.contains("<p>HTMX:true</p>"));
        assertTrue(routeHtml.contains("<p>HTMX:true</p>"));
        assertTrue(controllerHtml.contains("<p>Target:#target-element</p>"));
        assertTrue(routeHtml.contains("<p>Target:#target-element</p>"));
        assertTrue(controllerHtml.contains("<p>Trigger:trigger-event</p>"));
        assertTrue(routeHtml.contains("<p>Trigger:trigger-event</p>"));
        assertTrue(controllerHtml.contains("<p>CurrentURL:http://localhost/current</p>"));
        assertTrue(routeHtml.contains("<p>CurrentURL:http://localhost/current</p>"));

        // Collision protection test: explicit model 'page' value is NOT overwritten by PipedPageContext
        mockMvc.perform(get("/context-override-controller"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Explicit Page Model")));

        mockMvc.perform(get("/context-override"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Explicit Page Model")));
    }

    @Test
    @DisplayName("File-route include regression through MockMvc: X|include partials/greeting|Y renders XHi JoeY without error")
    void testFileRouteIncludeRegression() throws Exception {
        mockMvc.perform(get("/inc-test").param("name", "Joe"))
                .andExpect(status().isOk())
                .andExpect(content().string("XHi JoeY"));
    }

    @Test
    @DisplayName("File-route layout regression through MockMvc: resolves layout, sections, and yields correctly")
    void testFileRouteLayoutRegression() throws Exception {
        mockMvc.perform(get("/layout-test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>")))
                .andExpect(content().string(containsString("Dashboard")))
                .andExpect(content().string(containsString("<h1>Hello</h1>")));
    }

    @Test
    @DisplayName("File-route component regression through MockMvc: renders named slots and model values")
    void testFileRouteComponentRegression() throws Exception {
        mockMvc.perform(get("/comp-test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Product Details")))
                .andExpect(content().string(containsString("Widget")));
    }

    @Test
    @DisplayName("Execution mode regression: both controller and file route report BYTECODE when available")
    void testExecutionModeParity() {
        Map<String, Object> model = Map.of(
                "appName", "Test",
                "user", Map.of("name", "Bob", "admin", false, "role", "USER"),
                "items", List.of("A"),
                "product", Map.of("name", "Gadget", "price", "$10")
        );

        RenderResult namedResult = templateEngine.renderNamedTemplate("parity/controller-page", model);
        assertEquals(TemplateEngine.ExecutionMode.BYTECODE, namedResult.executionMode());

        String source = "|layout layouts/parity-main|\n|section title|Title|/section|\n|section content|<p>Hello</p>|/section|";
        RenderResult sourceResult = templateEngine.renderTemplateSource(source, model);
        assertEquals(TemplateEngine.ExecutionMode.BYTECODE, sourceResult.executionMode());

        assertEquals(namedResult.executionMode(), sourceResult.executionMode(), "Execution modes must match between named and source template rendering");
    }

    @Test
    @DisplayName("Metadata header regression: identical metadata produces identical response headers")
    void testMetadataResponseHeaders() {
        PipedResponseMetadataApplicator applicator = new PipedResponseMetadataApplicator();
        Map<String, Object> metadata = Map.of(
                "contentType", "text/html;charset=UTF-8",
                "cache", "no-cache",
                "hxTrigger", "eventTriggered",
                "hxRedirect", "/target",
                "hxPushUrl", "/new-url",
                "hxRefresh", true
        );

        org.springframework.mock.web.MockHttpServletResponse resp = new org.springframework.mock.web.MockHttpServletResponse();
        applicator.apply(metadata, resp);

        assertEquals("text/html;charset=UTF-8", resp.getContentType());
        assertEquals("no-cache", resp.getHeader("Cache-Control"));
        assertEquals("eventTriggered", resp.getHeader("HX-Trigger"));
        assertEquals("/target", resp.getHeader("HX-Redirect"));
        assertEquals("/new-url", resp.getHeader("HX-Push-Url"));
        assertEquals("true", resp.getHeader("HX-Refresh"));
    }
}
