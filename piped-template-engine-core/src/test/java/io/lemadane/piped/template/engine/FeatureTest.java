package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FeatureTest {
   final TemplateEngine engine = new TemplateEngine();

   @Nested
   class OutputModesTest {
      @Test
      @DisplayName("Escapes HTML by default")
      void escapesHtmlByDefault() {
         final var html = engine.renderString(
               "|comment|",
               Map.of(
                     "comment",
                     "<strong>Hello</strong>"));
         assertEquals(
               "&lt;strong&gt;Hello&lt;/strong&gt;",
               html);
      }

      @Test
      @DisplayName("Renders trusted HTML without escaping")
      void rendersTrustedHtml() {
         final var html = engine.renderString(
               "|html body|",
               Map.of(
                     "body",
                     "<strong>Hello</strong>"));
         assertEquals(
               "<strong>Hello</strong>",
               html);
      }

      @Test
      @DisplayName("Escapes HTML attributes")
      void escapesAttributes() {
         final var html = engine.renderString(
               "<input value=\"|attr name|\">",
               Map.of(
                     "name",
                     "Juan \"Boss\""));
         assertEquals(
               "<input value=\"Juan &quot;Boss&quot;\">",
               html);
      }

      @Test
      @DisplayName("Encodes URL values")
      void encodesUrlValues() {
         final var html = engine.renderString(
               "/search?q=|url query|",
               Map.of(
                     "query",
                     "coffee & sugar"));
         assertEquals(
               "/search?q=coffee+%26+sugar",
               html);
      }

      @Test
      @DisplayName("Renders JSON safely")
      void rendersJsonSafely() {
         final var product = new LinkedHashMap<String, Object>();
         product.put(
               "name",
               "Rice");
         product.put(
               "description",
               "<strong>Premium</strong>");
         final var html = engine.renderString(
               "|json product|",
               Map.of("product", product));
         assertTrue(html.contains(
               "\"name\":\"Rice\""));
         assertTrue(html.contains(
               "\\u003Cstrong\\u003EPremium\\u003C/strong\\u003E"));
      }
   }

   @Nested
   class ExpressionTest {
      @Test
      @DisplayName("Supports optional chaining and null coalescing")
      void supportsOptionalChainingAndNullCoalescing() {
         final var html = engine.renderString(
               "|user?.profile?.displayName ?? 'Guest'|",
               Map.of());
         assertEquals(
               "Guest",
               html);
      }

      @Test
      @DisplayName("Supports ternary operator")
      void supportsTernary() {
         final var html = engine.renderString(
               "|user.active ? 'Active' : 'Inactive'|",
               Map.of(
                     "user", Map.of(
                           "active", true)));

         assertEquals("Active", html);
      }

      @Test
      @DisplayName("Supports truthy and falsy rules")
      void supportsTruthyFalsyRules() {
         final var html = engine.renderString(
               """
                     |if products|
                        Has products
                     |else|
                        No products
                     |/if|
                     """,
               Map.of("products", List.of()));

         assertTrue(compact(html).contains("No products"));
      }
   }

   @Nested
   class ConditionalTest {
      @Test
      void supportsIfElseIfElse() {
         final var html = engine.renderString(
               """
                     |if user.role == 'admin'|
                        Admin
                     |else if user.role == 'manager'|
                        Manager
                     |else|
                        User
                     |/if|
                     """,
               Map.of(
                     "user", Map.of(
                           "role", "manager")));

         assertTrue(compact(html).contains("Manager"));
      }
   }

   @Nested
   class EachTest {
      @Test
      void loopsOverList() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |each.index|. |product.name|
                     |/each|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"),
                           Map.of("name", "Coffee"))));

         final var compactHtml = compact(html);

         assertTrue(compactHtml.contains("1. Rice"));
         assertTrue(compactHtml.contains("2. Coffee"));
      }

      @Test
      void rendersElseForEmptyList() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |product.name|
                     |else|
                        No products
                     |/each|
                     """,
               Map.of("products", List.of()));

         assertTrue(compact(html).contains("No products"));
      }
   }

   @Nested
   class SwitchTest {
      @Test
      void switchBreaksAutomatically() {
         final var html = engine.renderString(
               """
                     |switch role|
                        |case 'admin'|
                           Admin
                        |case 'manager'|
                           Manager
                        |default|
                           User
                     |/switch|
                     """,
               Map.of("role", "admin"));

         final var compactHtml = compact(html);

         assertTrue(compactHtml.contains("Admin"));
         assertTrue(!compactHtml.contains("Manager"));
         assertTrue(!compactHtml.contains("User"));
      }

      @Test
      void switchSupportsFallthrough() {
         final var html = engine.renderString(
               """
                     |switch role|
                        |case 'admin'|
                           Admin
                           |fallthrough|
                        |case 'manager'|
                           Reports
                        |default|
                           User
                     |/switch|
                     """,
               Map.of("role", "admin"));

         final var compactHtml = compact(html);

         assertTrue(compactHtml.contains("Admin"));
         assertTrue(compactHtml.contains("Reports"));
      }
   }

   @Nested
   class FilterTest {
      @Test
      void supportsTextAndNumberFilters() {
         final var html = engine.renderString(
               """
                     |name, upper|
                     |messyText, trim, capitalize|
                     |price, currency '₱'|
                     """,
               Map.of(
                     "name", "lemuel",
                     "messyText", "   hello pte   ",
                     "price", 120));

         final var compactHtml = compact(html);

         assertTrue(compactHtml.contains("LEMUEL"));
         assertTrue(compactHtml.contains("Hello pte"));
         assertTrue(compactHtml.contains("₱120.00"));
      }

      @Test
      void supportsDateFilter() {
         final var html = engine.renderString(
               "|createdAt, date 'yyyy-MM-dd'|",
               Map.of(
                     "createdAt", LocalDate.of(2026, 7, 7)));

         assertEquals("2026-07-07", html);
      }
   }

   @Nested
   class FileTemplateTest {
      @TempDir
      Path templateRoot;

      @Test
      void rendersFileBasedTemplateWithInclude() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));
         Files.createDirectories(templateRoot.resolve("partials"));

         Files.writeString(
               templateRoot.resolve("partials/header.pte"),
               "<header>|title|</header>");

         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               """
                     |include partials/header|
                     <main>Hello</main>
                     """);

         final var fileEngine = new TemplateEngine(templateRoot);

         final var html = fileEngine.render(
               "pages/home",
               Map.of("title", "PTE"));

         final var compactHtml = compact(html);

         assertTrue(compactHtml.contains("<header>PTE</header>"));
         assertTrue(compactHtml.contains("<main>Hello</main>"));
      }

      @Test
      void rendersPartial() throws Exception {
         Files.createDirectories(
               templateRoot.resolve(
                     "partials"));
         Files.writeString(
               templateRoot.resolve(
                     "partials/product-card.pte"),
               "<li>|name| - ₱|price|</li>");
         final var fileEngine = new TemplateEngine(
               templateRoot);
         final var html = fileEngine.renderPartial(
               "partials/product-card",
               Map.of(
                     "name", "Rice",
                     "price", 120));
         assertEquals(
               "<li>Rice - ₱120</li>", html);
      }
   }

   @Nested
   class LayoutAndSlotTest {

      @TempDir
      Path templateRoot;

      @Test
      @DisplayName("Renders layout sections and yield")
      void rendersLayoutSectionsAndYield() throws Exception {
         Files.createDirectories(
               templateRoot.resolve("layouts"));
         Files.createDirectories(
               templateRoot.resolve("pages"));
         Files.writeString(
               templateRoot.resolve(
                     "layouts/main.pte"),
               """
                     <html>
                     <head><title>|yield title|</title></head>
                     <body>|yield content|</body>
                     </html>
                     """);
         Files.writeString(
               templateRoot.resolve(
                     "pages/products.pte"),
               """
                     |layout layouts/main|
                     |section title|
                        Products
                     |/section|
                     |section content|
                        <h1>Product List</h1>
                     |/section|
                     """);
         final var fileEngine = new TemplateEngine(
               templateRoot);
         final var html = fileEngine.render(
               "pages/products",
               Map.of());
         final var compactHtml = compact(html);
         assertTrue(
               compactHtml.contains(
                     "<title> Products </title>"));
         assertTrue(
               compactHtml.contains(
                     "<h1>Product List</h1>"));
      }

      @Test
      @DisplayName("Renders component slots")
      void rendersComponentSlots() throws Exception {
         Files.createDirectories(
               templateRoot.resolve(
                     "components"));

         Files.writeString(
               templateRoot.resolve(
                     "components/card.pte"),
               """
                     <section>
                        <h3>|slot title|</h3>
                        <div>|slot body|</div>
                     </section>
                     """);
         final var fileEngine = new TemplateEngine(
               templateRoot);
         final var html = fileEngine.renderString(
               """
                     |component components/card|
                         |slot title|
                             Product List
                         |/slot|
                         |slot body|
                             <p>Products here</p>
                         |/slot|
                     |/component|
                     """,
               Map.of());
         final var compactHtml = compact(html);
         assertTrue(
               compactHtml.contains(
                     "<h3> Product List </h3>"));
         assertTrue(
               compactHtml.contains(
                     "<p>Products here</p>"));
      }
   }

   @Nested
   @DisplayName("Bytecode Debugging Test")
   class BytecodeDebuggingTest {
      @Test
      @DisplayName("Prints java source to stdout when pte.debug system property is true")
      void printsJavaSourceToStdoutWhenDebugPropertyIsTrue() {
         System.setProperty("pte.debug", "true");
         final var originalOut = System.out;
         final var bos = new java.io.ByteArrayOutputStream();
         final var printStream = new java.io.PrintStream(bos);
         System.setOut(printStream);

         try {
            engine.compileToBytecode("Hello |name|!");
            final var output = bos.toString();
            if (io.lemadane.piped.template.engine.codegen.InMemoryBytecodeCompiler.isAvailable()) {
               assertTrue(output.contains("public final class") || output.contains("writer.write("));
            }
         } finally {
            System.setProperty("pte.debug", "false");
            System.setOut(originalOut);
         }
      }
   }

   static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}