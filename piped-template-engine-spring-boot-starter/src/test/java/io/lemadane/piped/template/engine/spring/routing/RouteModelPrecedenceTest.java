package io.lemadane.piped.template.engine.spring.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RouteModelPrecedenceTest {

    @Test
    @DisplayName("Path variables override query parameters in root model collision while preserving route and query namespaces")
    void testModelNamespacesAndPrecedence() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts/123");
        request.setParameter("id", "999");
        request.setParameter("filter", "active");

        Map<String, String> pathVars = Map.of("id", "123");
        Map<String, Object> loaderData = Map.of("title", "Post Title");

        Map<String, Object> model = RouteModelBuilder.buildModel(request, pathVars, loaderData);

        // Root model collision: path variable "123" takes precedence over query parameter "999"
        assertEquals("123", model.get("id"));
        assertEquals("active", model.get("filter"));
        assertEquals("Post Title", model.get("title"));

        // Route namespace
        @SuppressWarnings("unchecked")
        Map<String, String> routeNs = (Map<String, String>) model.get("route");
        assertNotNull(routeNs);
        assertEquals("123", routeNs.get("id"));

        // Query namespace
        @SuppressWarnings("unchecked")
        Map<String, Object> queryNs = (Map<String, Object>) model.get("query");
        assertNotNull(queryNs);
        assertEquals("999", queryNs.get("id"));
        assertEquals("active", queryNs.get("filter"));
    }
}
