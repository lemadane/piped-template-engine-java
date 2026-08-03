package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class LayoutTest {

   @Nested
   @DisplayName("In-memory layouts")
   class InMemoryLayoutTest {
      @Test
      @DisplayName("Renders layout with title and content sections")
      void rendersLayoutWithTitleAndContentSections() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     """
                           <!DOCTYPE html>
                           <html lang="en">
                           <head>
                              <title>|yield title|</title>
                           </head>
                           <body>
                              <main>
                                 |yield content|
                              </main>
                           </body>
                           </html>
                           """));

         final var html = engine.renderString(
               """
                     |layout layouts/main|

                     |section title|
                        Products
                     |/section|

                     |section content|
                        <h1>Product List</h1>
                        <p>Hello, |user.name|</p>
                     |/section|
                     """,
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel")));

         final var output = compact(html);

         assertTrue(output.contains("<title> Products </title>"));
         assertTrue(output.contains("<h1>Product List</h1>"));
         assertTrue(output.contains("<p>Hello, Lemuel</p>"));
      }

      @Test
      @DisplayName("Renders missing yield section as empty string")
      void rendersMissingYieldSectionAsEmptyString() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<title>|yield title|</title><main>|yield content|</main>"));

         final var html = engine.renderString(
               """
                     |layout layouts/main|

                     |section content|
                        <h1>Home</h1>
                     |/section|
                     """,
               Map.of());

         final var output = compact(html);

         assertTrue(output.contains("<title></title>"));
         assertTrue(output.contains("<main> <h1>Home</h1> </main>"));
      }

      @Test
      @DisplayName("Layout can include partials")
      void layoutCanIncludePartials() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     """
                           |include partials/header|
                           <main>|yield content|</main>
                           |include partials/footer|
                           """,
                     "partials/header",
                     "<header><h1>|appName|</h1></header>",
                     "partials/footer",
                     "<footer>&copy; |year|</footer>"));

         final var html = engine.renderString(
               """
                     |layout layouts/main|

                     |section content|
                        <p>Dashboard</p>
                     |/section|
                     """,
               Map.of(
                     "appName", "PTE",
                     "year", 2026));

         final var output = compact(html);

         assertTrue(output.contains("<header><h1>PTE</h1></header>"));
         assertTrue(output.contains("<main> <p>Dashboard</p> </main>"));
         assertTrue(output.contains("<footer>&copy; 2026</footer>"));
      }
   }

   @Nested
   @DisplayName("File-based layouts")
   class FileBasedLayoutTest {
      @TempDir
      Path templateRoot;

      @Test
      @DisplayName("Renders file-based layout")
      void rendersFileBasedLayout() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));
         Files.createDirectories(templateRoot.resolve("layouts"));

         Files.writeString(
               templateRoot.resolve("layouts/main.pte"),
               """
                     <!DOCTYPE html>
                     <html lang="en">
                     <head>
                        <title>|yield title|</title>
                     </head>
                     <body>
                        <main>|yield content|</main>
                     </body>
                     </html>
                     """);

         Files.writeString(
               templateRoot.resolve("pages/products.pte"),
               """
                     |layout layouts/main|

                     |section title|
                        Products - PTE
                     |/section|

                     |section content|
                        <h1>Products</h1>
                     |/section|
                     """);

         final var engine = new TemplateEngine(templateRoot);

         final var html = engine.render("pages/products", Map.of());

         final var output = compact(html);

         assertTrue(output.contains("<title> Products - PTE </title>"));
         assertTrue(output.contains("<main> <h1>Products</h1> </main>"));
      }

      @Test
      @DisplayName("Renders file-based layout with includes")
      void rendersFileBasedLayoutWithIncludes() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));
         Files.createDirectories(templateRoot.resolve("layouts"));
         Files.createDirectories(templateRoot.resolve("partials"));

         Files.writeString(
               templateRoot.resolve("partials/header.pte"),
               "<header>|appName|</header>");

         Files.writeString(
               templateRoot.resolve("partials/footer.pte"),
               "<footer>|year|</footer>");

         Files.writeString(
               templateRoot.resolve("layouts/main.pte"),
               """
                     |include partials/header|
                     <main>|yield content|</main>
                     |include partials/footer|
                     """);

         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               """
                     |layout layouts/main|

                     |section content|
                        <p>Welcome</p>
                     |/section|
                     """);

         final var engine = new TemplateEngine(templateRoot);

         final var html = engine.render(
               "pages/home",
               Map.of(
                     "appName", "Piped Template Engine",
                     "year", 2026));

         final var output = compact(html);

         assertTrue(output.contains("<header>Piped Template Engine</header>"));
         assertTrue(output.contains("<main> <p>Welcome</p> </main>"));
         assertTrue(output.contains("<footer>2026</footer>"));
      }

      @Test
      @DisplayName("Throws when layout template is missing")
      void throwsWhenLayoutTemplateIsMissing() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));

         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               """
                     |layout layouts/missing|

                     |section content|
                        <h1>Home</h1>
                     |/section|
                     """);

         final var engine = new TemplateEngine(templateRoot);

         assertThrows(
               TemplateRenderException.class,
               () -> engine.render(
                     "pages/home",
                     Map.of()));
      }
   }

   @Nested
   @DisplayName("Layout sections")
   class LayoutSectionTest {
      @Test
      @DisplayName("Sections can contain if blocks")
      void sectionsCanContainIfBlocks() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         final var html = engine.renderString(
               """
                     |layout layouts/main|

                     |section content|
                        |if user.admin|
                           <p>Admin</p>
                        |else|
                           <p>User</p>
                        |/if|
                     |/section|
                     """,
               Map.of(
                     "user", Map.of(
                           "admin", true)));

         final var output = compact(html);

         assertTrue(output.contains("<p>Admin</p>"));
         assertFalse(output.contains("<p>User</p>"));
      }

      @Test
      @DisplayName("Sections can contain each blocks")
      void sectionsCanContainEachBlocks() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<ul>|yield content|</ul>"));

         final var html = engine.renderString(
               """
                     |layout layouts/main|

                     |section content|
                        |each product in products|
                           <li>|product.name|</li>
                        |/each|
                     |/section|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"),
                           Map.of("name", "Coffee"))));

         final var output = compact(html);

         assertTrue(output.contains("<li>Rice</li>"));
         assertTrue(output.contains("<li>Coffee</li>"));
      }

      @Test
      @DisplayName("Sections can contain switch blocks")
      void sectionsCanContainSwitchBlocks() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         final var html = engine.renderString(
               """
                     |layout layouts/main|

                     |section content|
                        |switch status|
                           |case 'paid'|
                              <p>Paid</p>
                           |default|
                              <p>Unknown</p>
                        |/switch|
                     |/section|
                     """,
               Map.of("status", "paid"));

         final var output = compact(html);

         assertTrue(output.contains("<p>Paid</p>"));
         assertFalse(output.contains("<p>Unknown</p>"));
      }
   }

   @Nested
   @DisplayName("Layout syntax errors")
   class LayoutSyntaxErrorTest {
      @Test
      @DisplayName("Throws when layout directive is not first directive")
      void throwsWhenLayoutDirectiveIsNotFirstDirective() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           <p>Before layout</p>
                           |layout layouts/main|

                           |section content|
                              <h1>Home</h1>
                           |/section|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when text appears outside sections")
      void throwsWhenTextAppearsOutsideSections() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |layout layouts/main|

                           Text outside section

                           |section content|
                              <h1>Home</h1>
                           |/section|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when section is not closed")
      void throwsWhenSectionIsNotClosed() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |layout layouts/main|

                           |section content|
                              <h1>Home</h1>
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when section name is empty")
      void throwsWhenSectionNameIsEmpty() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |layout layouts/main|

                           |section |
                              <h1>Home</h1>
                           |/section|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when duplicate section exists")
      void throwsWhenDuplicateSectionExists() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |layout layouts/main|

                           |section content|
                              First
                           |/section|

                           |section content|
                              Second
                           |/section|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when nested section exists")
      void throwsWhenNestedSectionExists() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |layout layouts/main|

                           |section content|
                              |section inner|
                                 Inner
                              |/section|
                           |/section|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when yield is used outside layout template")
      void throwsWhenYieldIsUsedOutsideLayoutTemplate() {
         final var engine = new TemplateEngine();

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "|yield content|",
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when section is used without layout")
      void throwsWhenSectionIsUsedWithoutLayout() {
         final var engine = new TemplateEngine();

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |section content|
                              <h1>Home</h1>
                           |/section|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when closing section is used without section")
      void throwsWhenClosingSectionIsUsedWithoutSection() {
         final var engine = new TemplateEngine();

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "|/section|",
                     Map.of()));
      }
   }

   static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}