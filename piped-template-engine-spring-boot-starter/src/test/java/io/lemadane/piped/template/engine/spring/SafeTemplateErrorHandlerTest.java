package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.exceptions.TemplateForbiddenException;
import io.lemadane.piped.template.engine.exceptions.TemplateUnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SafeTemplateErrorHandlerTest {

    int handleAndReturnStatus(Throwable error, MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        SafeTemplateErrorHandler.handleError(error, request, response);
        return response.getStatus();
    }

    @Test
    @DisplayName("Typed TemplateUnauthorizedException produces HTTP 401 Unauthorized")
    void testTypedUnauthorizedException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        int status = handleAndReturnStatus(new TemplateUnauthorizedException("Authentication required"), request, response);

        assertEquals(401, status);
        assertEquals("Unauthorized", response.getContentAsString());
        assertNotNull(response.getHeader("X-Correlation-ID"));
    }

    @Test
    @DisplayName("Typed TemplateForbiddenException produces HTTP 403 Forbidden")
    void testTypedForbiddenException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        int status = handleAndReturnStatus(new TemplateForbiddenException("Required role missing"), request, response);

        assertEquals(403, status);
        assertEquals("Forbidden", response.getContentAsString());
        assertNotNull(response.getHeader("X-Correlation-ID"));
    }

    @Test
    @DisplayName("Wrapped typed exceptions in cause chain produce corresponding status codes")
    void testWrappedTypedExceptions() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        int status1 = handleAndReturnStatus(new RuntimeException("Outer wrapper", new TemplateUnauthorizedException("Auth required")), req1, res1);
        assertEquals(401, status1);

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        int status2 = handleAndReturnStatus(new RuntimeException("Outer wrapper", new TemplateForbiddenException("Role missing")), req2, res2);
        assertEquals(403, status2);
    }

    @Test
    @DisplayName("Misleading messages in generic exceptions produce HTTP 500 without leaking exception details")
    void testMisleadingExceptionMessagesProduce500() throws Exception {
        Throwable[] misleadingErrors = new Throwable[] {
                new RuntimeException("Database returned 401 records"),
                new RuntimeException("Service produced 403 results"),
                new RuntimeException("Unauthorized column value"),
                new RuntimeException("Forbidden filename")
        };

        for (Throwable err : misleadingErrors) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            int status = handleAndReturnStatus(err, request, response);

            assertEquals(500, status, "Expected 500 for exception message: " + err.getMessage());
            assertEquals("Internal Server Error", response.getContentAsString());
            assertFalse(response.getContentAsString().contains(err.getMessage()), "Response must not contain internal message");
            assertNotNull(response.getHeader("X-Correlation-ID"));
        }
    }

    @Test
    @DisplayName("Committed responses are not rewritten by error handler")
    void testCommittedResponseNotRewritten() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        response.setStatus(200);

        SafeTemplateErrorHandler.handleError(new RuntimeException("Error after commit"), request, response);

        assertEquals(200, response.getStatus());
        assertNotNull(response.getHeader("X-Correlation-ID"));
    }
}
