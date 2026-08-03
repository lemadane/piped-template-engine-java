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

class ComponentSlotTest {

   @Nested
   @DisplayName("In-memory components")
   class InMemoryComponentTest {
      @Test
      @DisplayName("Renders component with named slots")
      void rendersComponentWithNamedSlots() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     """
                           <section class="card">
                              <header>
                                 <h2>|slot title|</h2>
                              </header>

                              <div class="card-body">
                                 |slot body|
                              </div>

                              <footer>
                                 |slot actions|
                              </footer>
                           </section>
                           """));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot title|
                           Product List
                        |/slot|

                        |slot body|
                           <p>Available products</p>
                        |/slot|

                        |slot actions|
                           <a href="/products/new">Add Product</a>
                        |/slot|
                     |/component|
                     """,
               Map.of());
         final var output = compact(html);
         assertTrue(output.contains("<h2> Product List </h2>"));
         assertTrue(output.contains("<p>Available products</p>"));
         assertTrue(output.contains("<a href=\"/products/new\">Add Product</a>"));
      }

      @Test
      @DisplayName("Renders missing slot as empty string")
      void rendersMissingSlotAsEmptyString() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section><h2>|slot title|</h2><div>|slot body|</div></section>"));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot title|
                           Empty Body Card
                        |/slot|
                     |/component|
                     """,
               Map.of());

         final var output = compact(html);

         assertTrue(output.contains("<h2> Empty Body Card </h2>"));
         assertTrue(output.contains("<div></div>"));
      }

      @Test
      @DisplayName("Slot content can render model values")
      void slotContentCanRenderModelValues() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section><h2>|slot title|</h2><div>|slot body|</div></section>"));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot title|
                           Hello, |user.name|
                        |/slot|

                        |slot body|
                           <p>Role: |user.role|</p>
                        |/slot|
                     |/component|
                     """,
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel",
                           "role", "Admin")));

         final var output = compact(html);

         assertTrue(output.contains("<h2> Hello, Lemuel </h2>"));
         assertTrue(output.contains("<p>Role: Admin</p>"));
      }
   }

   @Nested
   @DisplayName("File-based components")
   class FileBasedComponentTest {
      @TempDir
      Path templateRoot;

      @Test
      @DisplayName("Renders file-based component")
      void rendersFileBasedComponent() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));
         Files.createDirectories(templateRoot.resolve("components"));

         Files.writeString(
               templateRoot.resolve("components/card.pte"),
               """
                     <section class="card">
                        <h2>|slot title|</h2>
                        <div>|slot body|</div>
                     </section>
                     """);

         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               """
                     |component components/card|
                        |slot title|
                           Dashboard
                        |/slot|

                        |slot body|
                           <p>Welcome back.</p>
                        |/slot|
                     |/component|
                     """);

         final var engine = new TemplateEngine(templateRoot);

         final var html = engine.render("pages/home", Map.of());

         final var output = compact(html);

         assertTrue(output.contains("<h2> Dashboard </h2>"));
         assertTrue(output.contains("<p>Welcome back.</p>"));
      }

      @Test
      @DisplayName("Throws when component template is missing")
      void throwsWhenComponentTemplateIsMissing() throws Exception {
         Files.createDirectories(templateRoot.resolve("pages"));

         Files.writeString(
               templateRoot.resolve("pages/home.pte"),
               """
                     |component components/missing|
                        |slot body|
                           Missing component
                        |/slot|
                     |/component|
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
   @DisplayName("Slot content")
   class SlotContentTest {
      @Test
      @DisplayName("Slot can contain if blocks")
      void slotCanContainIfBlocks() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>"));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot body|
                           |if user.admin|
                              <p>Admin panel</p>
                           |else|
                              <p>User panel</p>
                           |/if|
                        |/slot|
                     |/component|
                     """,
               Map.of(
                     "user", Map.of(
                           "admin", true)));

         final var output = compact(html);

         assertTrue(output.contains("<p>Admin panel</p>"));
         assertFalse(output.contains("<p>User panel</p>"));
      }

      @Test
      @DisplayName("Slot can contain each blocks")
      void slotCanContainEachBlocks() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<ul>|slot body|</ul>"));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot body|
                           |each product in products|
                              <li>|product.name|</li>
                           |/each|
                        |/slot|
                     |/component|
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
      @DisplayName("Slot can contain switch blocks")
      void slotCanContainSwitchBlocks() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>"));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot body|
                           |switch status|
                              |case 'paid'|
                                 <p>Paid</p>
                              |default|
                                 <p>Unknown</p>
                           |/switch|
                        |/slot|
                     |/component|
                     """,
               Map.of("status", "paid"));

         final var output = compact(html);

         assertTrue(output.contains("<p>Paid</p>"));
         assertFalse(output.contains("<p>Unknown</p>"));
      }

      @Test
      @DisplayName("Slot can contain includes")
      void slotCanContainIncludes() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>",
                     "partials/product-card",
                     "<li>|name| - ₱|price|</li>"));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot body|
                           |include partials/product-card with product|
                        |/slot|
                     |/component|
                     """,
               Map.of(
                     "product", Map.of(
                           "name", "Rice",
                           "price", 120)));

         final var output = compact(html);

         assertTrue(output.contains("<li>Rice - ₱120</li>"));
      }
   }

   @Nested
   @DisplayName("Multiple components")
   class MultipleComponentTest {
      @Test
      @DisplayName("Renders multiple component instances independently")
      void rendersMultipleComponentInstancesIndependently() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section><h2>|slot title|</h2><div>|slot body|</div></section>"));

         final var html = engine.renderString(
               """
                     |component components/card|
                        |slot title|
                           First Card
                        |/slot|

                        |slot body|
                           <p>First body</p>
                        |/slot|
                     |/component|

                     |component components/card|
                        |slot title|
                           Second Card
                        |/slot|

                        |slot body|
                           <p>Second body</p>
                        |/slot|
                     |/component|
                     """,
               Map.of());

         final var output = compact(html);

         assertTrue(output.contains("<h2> First Card </h2>"));
         assertTrue(output.contains("<p>First body</p>"));
         assertTrue(output.contains("<h2> Second Card </h2>"));
         assertTrue(output.contains("<p>Second body</p>"));
      }

      @Test
      @DisplayName("Renders component inside each block")
      void rendersComponentInsideEachBlock() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section><h2>|slot title|</h2><div>|slot body|</div></section>"));

         final var html = engine.renderString(
               """
                     |each product in products|
                        |component components/card|
                           |slot title|
                              |product.name|
                           |/slot|

                           |slot body|
                              ₱|product.price|
                           |/slot|
                        |/component|
                     |/each|
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

         assertTrue(output.contains("<h2> Rice </h2>"));
         assertTrue(output.contains("<div> ₱120 </div>"));
         assertTrue(output.contains("<h2> Coffee </h2>"));
         assertTrue(output.contains("<div> ₱150 </div>"));
      }
   }

   @Nested
   @DisplayName("Component syntax errors")
   class ComponentSyntaxErrorTest {
      @Test
      @DisplayName("Throws when component block is not closed")
      void throwsWhenComponentBlockIsNotClosed() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |component components/card|
                              |slot body|
                                 Missing component end
                              |/slot|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when closing component appears without matching component")
      void throwsWhenClosingComponentAppearsWithoutMatchingComponent() {
         final var engine = new TemplateEngine();

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "|/component|",
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when text appears outside slots inside component")
      void throwsWhenTextAppearsOutsideSlotsInsideComponent() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |component components/card|
                              Text outside slot

                              |slot body|
                                 Body
                              |/slot|
                           |/component|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when duplicate slot exists")
      void throwsWhenDuplicateSlotExists() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |component components/card|
                              |slot body|
                                 First
                              |/slot|

                              |slot body|
                                 Second
                              |/slot|
                           |/component|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when slot block is not closed")
      void throwsWhenSlotBlockIsNotClosed() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |component components/card|
                              |slot body|
                                 Missing slot end
                           |/component|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when nested slot exists")
      void throwsWhenNestedSlotExists() {
         final var engine = new TemplateEngine(
               Map.of(
                     "components/card",
                     "<section>|slot body|</section>"));

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |component components/card|
                              |slot body|
                                 |slot inner|
                                    Inner
                                 |/slot|
                              |/slot|
                           |/component|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when slot is rendered outside component template")
      void throwsWhenSlotIsRenderedOutsideComponentTemplate() {
         final var engine = new TemplateEngine();

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "|slot body|",
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when closing slot appears without matching slot")
      void throwsWhenClosingSlotAppearsWithoutMatchingSlot() {
         final var engine = new TemplateEngine();

         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "|/slot|",
                     Map.of()));
      }
   }

   @Nested
   @DisplayName("Component with layout")
   class ComponentWithLayoutTest {
      @Test
      @DisplayName("Component can render inside layout section")
      void componentCanRenderInsideLayoutSection() {
         final var engine = new TemplateEngine(
               Map.of(
                     "layouts/main",
                     "<main>|yield content|</main>",
                     "components/card",
                     "<section><h2>|slot title|</h2><div>|slot body|</div></section>"));

         final var html = engine.renderString(
               """
                     |layout layouts/main|

                     |section content|
                        |component components/card|
                           |slot title|
                              Product List
                           |/slot|

                           |slot body|
                              <p>Products go here.</p>
                           |/slot|
                        |/component|
                     |/section|
                     """,
               Map.of());

         final var output = compact(html);

         assertTrue(output.contains("<main>"));
         assertTrue(output.contains("<h2> Product List </h2>"));
         assertTrue(output.contains("<p>Products go here.</p>"));
      }
   }

   static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}