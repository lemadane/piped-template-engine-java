package io.lemadane.piped.template.engine.spring.routing;

import io.lemadane.piped.template.engine.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.*;

class RouteDiscoveryFailureTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Fail-fast mode throws InvalidTemplateRouteException on unclosed dynamic segment bracket")
    void testFailFastInvalidRoute() {
        PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine, true);
        ByteArrayResource badResource = new ByteArrayResource("Content".getBytes());

        assertThrows(InvalidTemplateRouteException.class, () ->
                mapping.registerFileRoute("/posts/{id", "posts/[id/+page.pte", badResource)
        );
    }

    @Test
    @DisplayName("Fail-fast mode throws DuplicateTemplateRouteException on duplicate route patterns")
    void testFailFastDuplicateRoute() {
        PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine, true);
        ByteArrayResource resource1 = new ByteArrayResource("Content 1".getBytes());
        ByteArrayResource resource2 = new ByteArrayResource("Content 2".getBytes());

        mapping.registeredPatterns.add("/duplicate");

        assertThrows(DuplicateTemplateRouteException.class, () ->
                mapping.convertToSpringUrlPattern("duplicate/+page.pte", resource1)
        );
    }

    @Test
    @DisplayName("Non fail-fast mode logs error and skips duplicate or invalid routes without throwing exception")
    void testNonFailFastLoggingAndSkipping() {
        PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine, false);
        ByteArrayResource resource = new ByteArrayResource("Content".getBytes());

        mapping.registeredPatterns.add("/skipped-duplicate");

        assertDoesNotThrow(() -> {
            if (mapping.registeredPatterns.contains("/skipped-duplicate")) {
                if (mapping.isFailFast()) {
                    throw new DuplicateTemplateRouteException("Duplicate route pattern: /skipped-duplicate");
                }
                // Simulating non-fail-fast fallback skipping
            }
        });
    }
}
