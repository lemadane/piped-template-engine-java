package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentRenderStressTest {

    final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Sharing one TemplateEngine across multiple threads with nested loops and failures maintains 0 state contamination")
    void testConcurrentRenderingStress() throws Exception {
        int threadCount = 10;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        String validTemplate = """
            |page title="Stress"|
            |each item in items|
              <span>Item: |item|</span>
            |/each|
            """;

        String failingTemplate = """
            |each item in items|
              |invalid_directive syntax|
            |/each|
            """;

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            tasks.add(() -> {
                for (int iter = 0; iter < iterationsPerThread; iter++) {
                    List<String> items = List.of("A", "B", "C");
                    Map<String, Object> model = Map.of("items", items);

                    if (iter % 3 == 0) {
                        try {
                            engine.renderTemplateSource(failingTemplate, model);
                        } catch (Exception expected) {
                            // Expected failure
                        }
                    }

                    RenderResult result = engine.renderTemplateSource(validTemplate, model);
                    String html = result.html();

                    assertTrue(html.contains("Item: A"));
                    assertTrue(html.contains("Item: B"));
                    assertTrue(html.contains("Item: C"));
                    assertEquals("Stress", result.metadata().get("title"));

                    assertNull(engine.topLevelRenderGuard.get());
                    assertTrue(engine.templateStack.get().isEmpty());
                    assertTrue(engine.sectionStack.get().isEmpty());
                    assertTrue(engine.slotStack.get().isEmpty());
                    assertEquals(0, engine.loopDepth.get());
                }
                return true;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Boolean> future : futures) {
            assertTrue(future.get());
        }
    }
}
