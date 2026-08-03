package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;

class IncludeTest {

   @Nested
   @DisplayName("In-memory includes")
   class InMemoryIncludeTest {
      @Test
      @DisplayName("Renders included template using current context")
      void rendersIncludedTemplateUsingCurrentContext() {
         final var engine = new TemplateEngine(
               Map.of(
                     "partials/header",
                     "<header><h1>|title|</h1></header>"));
         final var html = engine.renderString(
               """
                     |include partials/header|
                     <main>Home</main>
                     """,
               Map.of("title", "PTE"));
         final var output = compact(html);
         assertTrue(
               output.contains(
                     "<header><h1>PTE</h1></header>"));
         assertTrue(
               output.contains(
                     "<main>Home</main>"));
      }

      @Test
      @DisplayName("Supports included template names with .pte extension")
      void supportsIncludedTemplateNamesWithPipedExtension() {
         final var engine = new TemplateEngine(
               Map.of(
                     "partials/footer.pte",
                     "<footer>|year| PTE</footer>"));
         final var html = engine.renderString(
               "|include partials/footer|",
               Map.of("year", 2026));
         assertEquals(
               "<footer>2026 PTE</footer>",
               html);
      }

      @Test
      @DisplayName("Throws when in-memory include is missing")
      void throwsWhenInMemoryIncludeIsMissing() {
         final var engine = new TemplateEngine();
         assertThrows(
               TemplateRenderException.class,
               () -> engine.renderString(
                     "|include partials/missing|",
                     Map.of()));
      }
   }

   @Nested
   @DisplayName("File-based includes")
   class FileBasedIncludeTest {
      @TempDir
      Path templateRoot;

      @Test
      @DisplayName("Renders file-based include")
      void rendersFileBasedInclude() throws Exception {
         Files.createDirectories(
               templateRoot.resolve("pages"));
         Files.createDirectories(
               templateRoot.resolve("partials"));
         Files.writeString(
               templateRoot.resolve("partials/header.pte"),
               "<header><h1>|title|</h1></header>");
         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               """
                     |include partials/header|
                     <main>Hello</main>
                     """);
         final var engine = new TemplateEngine(templateRoot);
         final var html = engine.render(
               "pages/home",
               Map.of("title", "Piped Template Engine"));
         final var output = compact(html);
         assertTrue(
               output.contains(
                     "<header><h1>Piped Template Engine</h1></header>"));
         assertTrue(
               output.contains(
                     "<main>Hello</main>"));
      }

      @Test
      @DisplayName("Throws when file-based include is missing")
      void throwsWhenFileBasedIncludeIsMissing() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));

         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               "|include partials/missing|");

         final var engine = new TemplateEngine(templateRoot);

         assertThrows(
               TemplateRenderException.class,
               () -> engine.render(
                     "pages/home",
                     Map.of()));
      }
   }

   @Nested
   @DisplayName("Include with custom context")
   class IncludeWithCustomContextTest {
      @Test
      @DisplayName("Renders include with map context")
      void rendersIncludeWithMapContext() {
         final var engine = new TemplateEngine(
               Map.of(
                     "partials/product-card",
                     "<li>|name| - ₱|price|</li>"));

         final var html = engine.renderString(
               "|include partials/product-card with product|",
               Map.of(
                     "product", Map.of(
                           "name", "Rice",
                           "price", 120)));

         assertEquals("<li>Rice - ₱120</li>", html);
      }

      @Test
      @DisplayName("Renders include with object context using it")
      void rendersIncludeWithObjectContextUsingIt() {
         final var engine = new TemplateEngine(
               Map.of(
                     "partials/product-card",
                     "<li>|it.name| - ₱|it.price|</li>"));

         final var html = engine.renderString(
               "|include partials/product-card with product|",
               Map.of(
                     "product",
                     new Product("Coffee", 150)));

         assertEquals("<li>Coffee - ₱150</li>", html);
      }

      @Test
      @DisplayName("Renders include inside each block")
      void rendersIncludeInsideEachBlock() {
         final var engine = new TemplateEngine(
               Map.of(
                     "partials/product-card",
                     "<li>|name| - ₱|price|</li>"));

         final var html = engine.renderString(
               """
                     <ul>
                        |each product in products|
                           |include partials/product-card with product|
                        |/each|
                     </ul>
                     """,
               Map.of(
                     "products", List.of(
                           Map.of(
                                 "name", "Rice",
                                 "price", 120),
                           Map.of(
                                 "name", "Coffee",
                                 "price", 150))));

         final var output = compact(html);

         assertTrue(output.contains("<li>Rice - ₱120</li>"));
         assertTrue(output.contains("<li>Coffee - ₱150</li>"));
      }
   }

   @Nested
   @DisplayName("Partial rendering")
   class PartialRenderingTest {
      @TempDir
      Path templateRoot;

      @Test
      @DisplayName("Renders partial with map value")
      void rendersPartialWithMapValue() throws Exception {
         Files.createDirectories(templateRoot.resolve("partials"));

         Files.writeString(
               templateRoot.resolve("partials/product-card.pte"),
               "<li>|name| - ₱|price|</li>");

         final var engine = new TemplateEngine(templateRoot);

         final var html = engine.renderPartial(
               "partials/product-card",
               Map.of(
                     "name", "Sugar",
                     "price", 90));

         assertEquals("<li>Sugar - ₱90</li>", html);
      }

      @Test
      @DisplayName("Renders partial with object value as it")
      void rendersPartialWithObjectValueAsIt() throws Exception {
         Files.createDirectories(templateRoot.resolve("partials"));

         Files.writeString(
               templateRoot.resolve("partials/product-card.pte"),
               "<li>|it.name| - ₱|it.price|</li>");

         final var engine = new TemplateEngine(templateRoot);

         final var html = engine.renderPartial(
               "partials/product-card",
               new Product("Milk", 110));

         assertEquals("<li>Milk - ₱110</li>", html);
      }
   }

   @Nested
   @DisplayName("Include safety")
   class IncludeSafetyTest {
      @Test
      @DisplayName("Detects circular includes")
      void detectsCircularIncludes() {
         final var engine = new TemplateEngine(
               Map.of(
                     "partials/a",
                     "A |include partials/b|",
                     "partials/b",
                     "B |include partials/a|"));

         final var exception = assertThrows(
               TemplateRenderException.class,
               () -> engine.renderString(
                     "|include partials/a|",
                     Map.of()));

         assertTrue(exception.getMessage().contains("Circular include detected"));
      }

      @TempDir
      Path templateRoot;

      @Test
      @DisplayName("Rejects include path escaping template root")
      void rejectsIncludePathEscapingTemplateRoot() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));

         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               "|include ../secret|");

         final var engine = new TemplateEngine(templateRoot);

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.render(
                     "pages/home",
                     Map.of()));
      }

      @Test
      @DisplayName("Rejects absolute include path")
      void rejectsAbsoluteIncludePath() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));
         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               "|include /partials/header|");
         final var engine = new TemplateEngine(templateRoot);
         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.render(
                     "pages/home",
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when include with expression is empty")
      void throwsWhenIncludeWithExpressionIsEmpty() {
         final var engine = new TemplateEngine(
               Map.of(
                     "partials/card",
                     "<div>|name|</div>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "|include partials/card with |",
                     Map.of()));
      }
   }

   record Product(
         String name,
         int price) {
   }

   static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}