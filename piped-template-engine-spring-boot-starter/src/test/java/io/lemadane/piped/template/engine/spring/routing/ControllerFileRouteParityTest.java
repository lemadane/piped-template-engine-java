package io.lemadane.piped.template.engine.spring.routing;

import io.lemadane.piped.template.engine.RenderResult;
import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.spring.PipedResponseMetadataApplicator;
import io.lemadane.piped.template.engine.spring.PipedTemplateView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ControllerFileRouteParityTest {

    TemplateEngine templateEngine;
    PipedTemplateView view;
    PipedFileRouteHandlerMapping routeMapping;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine(Path.of("src/test/resources/pte-templates"));

        MockServletContext servletContext = new MockServletContext();
        GenericWebApplicationContext wac = new GenericWebApplicationContext(servletContext);
        wac.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, wac);

        view = new PipedTemplateView();
        view.setTemplateEngine(templateEngine);
        view.setApplicationContext(wac);
        view.setServletContext(servletContext);

        routeMapping = new PipedFileRouteHandlerMapping(templateEngine);
        routeMapping.discoverAndRegisterRoutes();
    }

    @Test
    @DisplayName("Controller and file route produce byte-for-byte identical HTML and identical response headers")
    void testControllerAndFileRouteParity() throws Exception {
        Map<String, Object> model = Map.of(
                "appName", "Piped Parity Test",
                "user", Map.of("name", "Alice", "admin", true, "role", "ADMIN"),
                "items", List.of("One", "Two", "Three"),
                "product", Map.of("name", "Widget", "price", "$99")
        );

        routeMapping.registerPageDataLoader("/parity", req -> model);

        // 1. Controller path rendering
        MockHttpServletRequest controllerReq = new MockHttpServletRequest("GET", "/parity-controller");
        MockHttpServletResponse controllerResp = new MockHttpServletResponse();
        view.setUrl("parity/controller-page");
        view.render(model, controllerReq, controllerResp);

        String controllerHtml = controllerResp.getContentAsString();

        // 2. File-route path rendering
        MockHttpServletRequest routeReq = new MockHttpServletRequest("GET", "/parity");
        MockHttpServletResponse routeResp = new MockHttpServletResponse();
        var executionChain = routeMapping.getHandler(routeReq);
        assertNotNull(executionChain, "Handler for /parity file route should be found");
        var handler = (org.springframework.web.HttpRequestHandler) executionChain.getHandler();
        handler.handleRequest(routeReq, routeResp);

        String fileRouteHtml = routeResp.getContentAsString();

        // 3. Assert Byte-for-Byte HTML Equality
        assertEquals(controllerHtml, fileRouteHtml, "Controller HTML and File Route HTML must be byte-for-byte identical");

        // 4. Assert Header Equality
        assertEquals(controllerResp.getContentType(), routeResp.getContentType(), "Content-Type must match");
        assertEquals(controllerResp.getHeader("Cache-Control"), routeResp.getHeader("Cache-Control"), "Cache-Control must match");
        assertEquals(controllerResp.getHeader("HX-Trigger"), routeResp.getHeader("HX-Trigger"), "HX-Trigger must match");
        assertEquals(controllerResp.getHeader("HX-Redirect"), routeResp.getHeader("HX-Redirect"), "HX-Redirect must match");
        assertEquals(controllerResp.getHeader("HX-Push-Url"), routeResp.getHeader("HX-Push-Url"), "HX-Push-Url must match");
        assertEquals(controllerResp.getHeader("HX-Refresh"), routeResp.getHeader("HX-Refresh"), "HX-Refresh must match");
    }

    @Test
    @DisplayName("File-route include regression: X|include partials/greeting|Y renders XHi JoeY without error")
    void testFileRouteIncludeRegression() throws Exception {
        MockHttpServletRequest routeReq = new MockHttpServletRequest("GET", "/inc-test");
        routeReq.setParameter("name", "Joe");
        MockHttpServletResponse routeResp = new MockHttpServletResponse();

        var executionChain = routeMapping.getHandler(routeReq);
        assertNotNull(executionChain);
        var handler = (org.springframework.web.HttpRequestHandler) executionChain.getHandler();

        assertDoesNotThrow(() -> handler.handleRequest(routeReq, routeResp));

        String html = routeResp.getContentAsString();
        assertEquals("XHi JoeY", html.trim());
    }

    @Test
    @DisplayName("File-route layout regression: resolves layout, sections, and yields correctly")
    void testFileRouteLayoutRegression() throws Exception {
        MockHttpServletRequest routeReq = new MockHttpServletRequest("GET", "/layout-test");
        MockHttpServletResponse routeResp = new MockHttpServletResponse();

        var executionChain = routeMapping.getHandler(routeReq);
        assertNotNull(executionChain);
        var handler = (org.springframework.web.HttpRequestHandler) executionChain.getHandler();

        handler.handleRequest(routeReq, routeResp);

        String html = routeResp.getContentAsString();
        assertTrue(html.contains("<title>"), "Output should contain title tag");
        assertTrue(html.contains("Dashboard"), "Output should contain yielded title content");
        assertTrue(html.contains("<h1>Hello</h1>"), "Output should contain yielded body content");
    }

    @Test
    @DisplayName("File-route component regression: renders named slots and model values")
    void testFileRouteComponentRegression() throws Exception {
        routeMapping.registerPageDataLoader("/comp-test", req -> Map.of("product", Map.of("name", "Widget")));
        MockHttpServletRequest routeReq = new MockHttpServletRequest("GET", "/comp-test");
        MockHttpServletResponse routeResp = new MockHttpServletResponse();

        var executionChain = routeMapping.getHandler(routeReq);
        assertNotNull(executionChain);
        var handler = (org.springframework.web.HttpRequestHandler) executionChain.getHandler();

        handler.handleRequest(routeReq, routeResp);

        String html = routeResp.getContentAsString();
        assertTrue(html.contains("Product Details"), "Output must contain slot header");
        assertTrue(html.contains("Widget"), "Output must contain model value");
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
    void testMetadataResponseHeaders() throws Exception {
        PipedResponseMetadataApplicator applicator = new PipedResponseMetadataApplicator();
        Map<String, Object> metadata = Map.of(
                "contentType", "text/html;charset=UTF-8",
                "cache", "no-cache",
                "hxTrigger", "eventTriggered",
                "hxRedirect", "/target",
                "hxPushUrl", "/new-url",
                "hxRefresh", true
        );

        MockHttpServletResponse resp = new MockHttpServletResponse();
        applicator.apply(metadata, resp);

        assertEquals("text/html;charset=UTF-8", resp.getContentType());
        assertEquals("no-cache", resp.getHeader("Cache-Control"));
        assertEquals("eventTriggered", resp.getHeader("HX-Trigger"));
        assertEquals("/target", resp.getHeader("HX-Redirect"));
        assertEquals("/new-url", resp.getHeader("HX-Push-Url"));
        assertEquals("true", resp.getHeader("HX-Refresh"));
    }
}
