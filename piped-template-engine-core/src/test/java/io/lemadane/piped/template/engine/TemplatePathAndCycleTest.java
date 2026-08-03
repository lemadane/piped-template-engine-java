package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateCircularDependencyException;
import io.lemadane.piped.template.engine.exceptions.TemplateNotFoundException;
import io.lemadane.piped.template.engine.res.ClasspathTemplateSourceResolver;
import io.lemadane.piped.template.engine.res.FileSystemTemplateSourceResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplatePathAndCycleTest {

    @Test
    @DisplayName("Direct circular dependency A -> A throws TemplateCircularDependencyException with chain")
    void testDirectCircularDependency() {
        Map<String, String> templates = Map.of(
                "cycle_a.pte", "|include cycle_a|"
        );
        TemplateEngine engine = new TemplateEngine(templates);

        TemplateCircularDependencyException ex = assertThrows(
                TemplateCircularDependencyException.class,
                () -> engine.renderNamedTemplate("cycle_a", Map.of())
        );

        assertTrue(ex.getMessage().contains("Circular template dependency"));
        assertTrue(ex.getMessage().contains("cycle_a -> cycle_a"));
    }

    @Test
    @DisplayName("Indirect circular dependency A -> B -> C -> A throws TemplateCircularDependencyException with full chain")
    void testIndirectCircularDependency() {
        Map<String, String> templates = Map.of(
                "page_a.pte", "|include page_b|",
                "page_b.pte", "|include page_c|",
                "page_c.pte", "|include page_a|"
        );
        TemplateEngine engine = new TemplateEngine(templates);

        TemplateCircularDependencyException ex = assertThrows(
                TemplateCircularDependencyException.class,
                () -> engine.renderNamedTemplate("page_a", Map.of())
        );

        assertTrue(ex.getMessage().contains("Circular template dependency"));
        assertTrue(ex.getMessage().contains("page_a -> page_b -> page_c -> page_a"));
    }

    @Test
    @DisplayName("Legal sequential includes of the same partial do NOT trigger circular dependency exception")
    void testLegalSequentialIncludes() {
        Map<String, String> templates = Map.of(
                "main.pte", "|include item|\n|include item|",
                "item.pte", "<span>Item</span>"
        );
        TemplateEngine engine = new TemplateEngine(templates);

        RenderResult result = engine.renderNamedTemplate("main", Map.of());
        assertTrue(result.html().contains("<span>Item</span>"));
    }

    @Test
    @DisplayName("Path traversal using ../ is rejected by FileSystemTemplateSourceResolver")
    void testFileSystemPathTraversalRejection() {
        FileSystemTemplateSourceResolver resolver = new FileSystemTemplateSourceResolver("src/test/resources/pte-templates");
        assertThrows(TemplateNotFoundException.class, () -> resolver.resolve("../../../etc/passwd"));
    }

    @Test
    @DisplayName("Absolute path is rejected by FileSystemTemplateSourceResolver")
    void testFileSystemAbsolutePathRejection() {
        FileSystemTemplateSourceResolver resolver = new FileSystemTemplateSourceResolver("src/test/resources/pte-templates");
        assertThrows(TemplateNotFoundException.class, () -> resolver.resolve("/etc/passwd"));
    }

    @Test
    @DisplayName("Path traversal using ../ is rejected by ClasspathTemplateSourceResolver")
    void testClasspathPathTraversalRejection() {
        ClasspathTemplateSourceResolver resolver = new ClasspathTemplateSourceResolver("classpath:/pte-templates/", ".pte");
        assertThrows(TemplateNotFoundException.class, () -> resolver.resolve("../application.properties"));
    }
}
