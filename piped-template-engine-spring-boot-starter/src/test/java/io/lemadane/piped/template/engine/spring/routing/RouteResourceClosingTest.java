package io.lemadane.piped.template.engine.spring.routing;

import io.lemadane.piped.template.engine.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RouteResourceClosingTest {

    @Test
    @DisplayName("Verifies InputStream close() is called after reading route resource")
    void testResourceStreamClosing() throws Exception {
        TemplateEngine engine = new TemplateEngine();
        PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine);

        AtomicBoolean streamClosed = new AtomicBoolean(false);
        String templateContent = "<h1>Stream Test</h1>";

        ByteArrayResource resource = new ByteArrayResource(templateContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(templateContent.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() throws IOException {
                        streamClosed.set(true);
                        super.close();
                    }
                };
            }
        };

        mapping.registerFileRoute("/stream-test", "stream-test/+page.pte", resource);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/stream-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var handlerExecutionChain = mapping.getHandler(request);
        assertNotNull(handlerExecutionChain);

        var handler = (org.springframework.web.HttpRequestHandler) handlerExecutionChain.getHandler();
        handler.handleRequest(request, response);

        assertTrue(streamClosed.get(), "InputStream must be closed after file route rendering");
        assertEquals("<h1>Stream Test</h1>", response.getContentAsString());
    }
}
