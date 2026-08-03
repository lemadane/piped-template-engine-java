package io.lemadane.piped.template.engine.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SafeRouteErrorHandlingTest {

    @Test
    @DisplayName("Returns generic HTTP 500 error response without exposing internal exception message or stack trace")
    void testGeneric500Response() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-error");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Exception internalError = new RuntimeException("Internal DB connection error at /var/secrets/db.pass");

        SafeTemplateErrorHandler.handleError(internalError, request, response);

        assertEquals(500, response.getStatus());
        assertEquals("Internal Server Error", response.getContentAsString());
        assertNotNull(response.getHeader("X-Correlation-ID"));
        assertFalse(response.getContentAsString().contains("/var/secrets/db.pass"));
    }

    @Test
    @DisplayName("Preserves 401 Unauthorized status")
    void testPreserves401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SafeTemplateErrorHandler.handleError(new io.lemadane.piped.template.engine.exceptions.TemplateUnauthorizedException("Unauthorized access"), request, response);

        assertEquals(401, response.getStatus());
        assertEquals("Unauthorized", response.getContentAsString());
    }

    @Test
    @DisplayName("Preserves 403 Forbidden status")
    void testPreserves403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SafeTemplateErrorHandler.handleError(new io.lemadane.piped.template.engine.exceptions.TemplateForbiddenException("Forbidden - Missing role ADMIN"), request, response);

        assertEquals(403, response.getStatus());
        assertEquals("Forbidden", response.getContentAsString());
    }
}
