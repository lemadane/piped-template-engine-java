package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.compiler.Lexer;
import io.lemadane.piped.template.engine.compiler.Parser;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ParserConcurrencyTest {

    @Test
    @DisplayName("Parser is completely thread-safe under concurrent compilation")
    void testConcurrentParsingThreadSafety() throws InterruptedException, ExecutionException {
        final Parser parser = new Parser();
        final Lexer lexer = new Lexer();
        final int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                if (index % 2 == 0) {
                    // Valid template with loop
                    String tpl = "|each item in items|Item |item||/each|";
                    assertNotNull(parser.parse(lexer.tokenize(tpl)));
                    return true;
                } else {
                    // Invalid top-level break - MUST fail even if another thread is parsing a loop
                    String invalidTpl = "|break|";
                    assertThrows(TemplateSyntaxException.class, () -> {
                        parser.parse(lexer.tokenize(invalidTpl));
                    });
                    return true;
                }
            }));
        }

        for (Future<Boolean> future : futures) {
            assertTrue(future.get());
        }

        executor.shutdown();
    }
}
