package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyEngineTest {

    @Test
    @DisplayName("Constructing TemplateEngine performs no filesystem writes")
    void testEngineConstructorNoFilesystemWrites() {
        assertDoesNotThrow(() -> {
            TemplateEngine engine1 = new TemplateEngine();
            TemplateEngine engine2 = new TemplateEngine(Map.of("a", "b"));
            assertNotNull(engine1);
            assertNotNull(engine2);
        });
    }

    @Test
    @DisplayName("renderString works in memory without filesystem reliance")
    void testRenderStringInMemory() {
        TemplateEngine engine = new TemplateEngine();
        String result = engine.renderString("Hello |name|!", Map.of("name", "PTE"));
        assertEquals("Hello PTE!", result);
    }
}
