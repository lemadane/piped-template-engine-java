package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryHygieneTest {

    @Test
    @DisplayName("Source directories contain no compiled .class files or temporary build artifacts")
    void testSourceDirectoriesClean() throws Exception {
        Path coreSrc = Path.of("src");
        if (Files.exists(coreSrc)) {
            try (var stream = Files.walk(coreSrc)) {
                List<Path> artifacts = stream
                        .filter(p -> p.toString().endsWith(".class") || p.toString().endsWith(".tmp"))
                        .toList();
                assertTrue(artifacts.isEmpty(), "Found compiled/temporary artifacts in src: " + artifacts);
            }
        }

        Path gitignore = Path.of("../.gitignore");
        if (!Files.exists(gitignore)) {
            gitignore = Path.of(".gitignore");
        }
        if (Files.exists(gitignore)) {
            String content = Files.readString(gitignore);
            assertTrue(content.contains(".gradle/"));
            assertTrue(content.contains("build/"));
            assertTrue(content.contains("*.class"));
        }
    }
}
