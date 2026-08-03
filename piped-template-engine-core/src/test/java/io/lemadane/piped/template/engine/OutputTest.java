package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;

class TemplateEngineOutputTest {
   final TemplateEngine engine = new TemplateEngine(
         Path.of("src/main/resources/pte"));

   @Nested
   @DisplayName("Default output")
   class DefaultOutputTest {
      @Test
      @DisplayName("Renders simple value")
      void rendersSimpleValue() {
         final var html = engine.renderString(
               "|name|",
               Map.of("name", "Lemuel"));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Renders nested value")
      void rendersNestedValue() {
         final var html = engine.renderString(
               "|user.profile.displayName|",
               Map.of(
                     "user", Map.of(
                           "profile", Map.of(
                                 "displayName", "Lemuel"))));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Missing value renders empty string")
      void missingValueRendersEmptyString() {
         final var html = engine.renderString(
               "|missing|",
               Map.of());

         assertEquals("", html);
      }

      @Test
      @DisplayName("Null value renders empty string")
      void nullValueRendersEmptyString() {
         final var values = new LinkedHashMap<String, Object>();
         values.put("name", null);

         final var html = engine.renderString(
               "|name|",
               values);

         assertEquals("", html);
      }

      @Test
      @DisplayName("Escapes HTML by default")
      void escapesHtmlByDefault() {
         final var html = engine.renderString(
               "|content|",
               Map.of("content", "<strong>Hello</strong>"));

         assertEquals("&lt;strong&gt;Hello&lt;/strong&gt;", html);
      }
   }

   @Nested
   @DisplayName("Output modes")
   class OutputModeTest {
      @Test
      @DisplayName("Renders trusted HTML")
      void rendersTrustedHtml() {
         final var html = engine.renderString(
               "|html content|",
               Map.of("content", "<strong>Hello</strong>"));

         assertEquals("<strong>Hello</strong>", html);
      }

      @Test
      @DisplayName("Renders attribute escaped value")
      void rendersAttributeEscapedValue() {
         final var html = engine.renderString(
               "|attr value|",
               Map.of("value", "Rice \"Premium\" & Coffee"));

         assertEquals("Rice &quot;Premium&quot; &amp; Coffee", html);
      }

      @Test
      @DisplayName("Renders URL encoded value")
      void rendersUrlEncodedValue() {
         final var html = engine.renderString(
               "|url query|",
               Map.of("query", "rice & coffee"));

         assertEquals("rice+%26+coffee", html);
      }

      @Test
      @DisplayName("Renders JSON encoded value")
      void rendersJsonEncodedValue() {
         final var html = engine.renderString(
               "|json product|",
               Map.of(
                     "product", Map.of(
                           "name", "Rice",
                           "price", 120)));

         assertTrue(html.contains("\"name\":\"Rice\""));
         assertTrue(html.contains("\"price\":120"));
      }
   }

   @Nested
   @DisplayName("Output expressions")
   class OutputExpressionTest {
      @Test
      @DisplayName("Supports optional chaining")
      void supportsOptionalChaining() {
         final var html = engine.renderString(
               "|user?.profile?.displayName|",
               Map.of("user", Map.of()));

         assertEquals("", html);
      }

      @Test
      @DisplayName("Supports null coalescing")
      void supportsNullCoalescing() {
         final var html = engine.renderString(
               "|user?.profile?.displayName ?? 'Guest'|",
               Map.of("user", Map.of()));

         assertEquals("Guest", html);
      }

      @Test
      @DisplayName("Supports ternary expression")
      void supportsTernaryExpression() {
         final var html = engine.renderString(
               "|user.active ? 'Active' : 'Inactive'|",
               Map.of(
                     "user", Map.of(
                           "active", true)));

         assertEquals("Active", html);
      }

      @Test
      @DisplayName("Supports filters")
      void supportsFilters() {
         final var html = engine.renderString(
               "|name, trim, upper|",
               Map.of("name", "   lemuel   "));

         assertEquals("LEMUEL", html);
      }

      @Test
      @DisplayName("Supports output mode with filters")
      void supportsOutputModeWithFilters() {
         final var html = engine.renderString(
               "|attr name, trim, lower|",
               Map.of("name", "   LEM\"UEL   "));

         assertEquals("lem&quot;uel", html);
      }
   }

   @Nested
   @DisplayName("Conditional output")
   class ConditionalOutputTest {
      @Test
      @DisplayName("Renders default output when condition is true")
      void rendersDefaultOutputWhenConditionIsTrue() {
         final var html = engine.renderString(
               "|user.name if user.active|",
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel",
                           "active", true)));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Renders empty string when condition is false")
      void rendersEmptyStringWhenConditionIsFalse() {
         final var html = engine.renderString(
               "|user.name if user.active|",
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel",
                           "active", false)));

         assertEquals("", html);
      }

      @Test
      @DisplayName("Supports if not condition")
      void supportsIfNotCondition() {
         final var html = engine.renderString(
               "|user.name if not user.deleted|",
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel",
                           "deleted", false)));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Supports attr output with condition")
      void supportsAttrOutputWithCondition() {
         final var html = engine.renderString(
               "|attr value if enabled|",
               Map.of(
                     "value", "Rice \"Premium\"",
                     "enabled", true));

         assertEquals("Rice &quot;Premium&quot;", html);
      }

      @Test
      @DisplayName("Supports attr output with if not condition")
      void supportsAttrOutputWithIfNotCondition() {
         final var html = engine.renderString(
               "|attr 'disabled' if not enabled|",
               Map.of("enabled", false));

         assertEquals("disabled", html);
      }

      @Test
      @DisplayName("Supports html output with condition")
      void supportsHtmlOutputWithCondition() {
         final var html = engine.renderString(
               "|html content if published|",
               Map.of(
                     "content", "<strong>Published</strong>",
                     "published", true));

         assertEquals("<strong>Published</strong>", html);
      }

      @Test
      @DisplayName("Supports url output with condition")
      void supportsUrlOutputWithCondition() {
         final var html = engine.renderString(
               "|url query if query|",
               Map.of("query", "rice coffee"));

         assertEquals("rice+coffee", html);
      }

      @Test
      @DisplayName("Supports json output with condition")
      void supportsJsonOutputWithCondition() {
         final var html = engine.renderString(
               "|json product if product.visible|",
               Map.of(
                     "product", Map.of(
                           "name", "Rice",
                           "visible", true)));

         assertTrue(html.contains("\"name\":\"Rice\""));
         assertTrue(html.contains("\"visible\":true"));
      }

      @Test
      @DisplayName("Conditional output works inside HTML attribute")
      void conditionalOutputWorksInsideHtmlAttribute() {
         final var html = engine.renderString(
               "<button |attr 'disabled' if not enabled|>Save</button>",
               Map.of("enabled", false));

         assertEquals("<button disabled>Save</button>", html);
      }

      @Test
      @DisplayName("Conditional output skips inside HTML attribute")
      void conditionalOutputSkipsInsideHtmlAttribute() {
         final var html = engine.renderString(
               "<button |attr disabled if not enabled|>Save</button>",
               Map.of("enabled", true));

         assertEquals("<button>Save</button>", html);
      }

      @Test
      @DisplayName("Conditional output supports complex condition")
      void conditionalOutputSupportsComplexCondition() {
         final var html = engine.renderString(
               "|user.name if user.active and user.verified|",
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel",
                           "active", true,
                           "verified", true)));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Does not treat if inside quoted text as conditional output")
      void doesNotTreatIfInsideQuotedTextAsConditionalOutput() {
         final var html = engine.renderString(
               "|name, default 'show if empty'|",
               Map.of());

         assertEquals("show if empty", html);
      }
   }

   @Nested
   @DisplayName("Output inside blocks")
   class OutputInsideBlockTest {
      @Test
      @DisplayName("Renders output inside if block")
      void rendersOutputInsideIfBlock() {
         final var html = engine.renderString(
               """
                     |if user|
                        Hello, |user.name|
                     |/if|
                     """,
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel")));

         assertTrue(compact(html).contains("Hello, Lemuel"));
      }

      @Test
      @DisplayName("Renders output inside each block")
      void rendersOutputInsideEachBlock() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |product.name|
                     |/each|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"),
                           Map.of("name", "Coffee"))));

         final var output = compact(html);

         assertTrue(output.contains("Rice"));
         assertTrue(output.contains("Coffee"));
      }

      @Test
      @DisplayName("Renders conditional output inside each block")
      void rendersConditionalOutputInsideEachBlock() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |product.name if product.available|
                     |/each|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of(
                                 "name", "Rice",
                                 "available", true),
                           Map.of(
                                 "name", "Coffee",
                                 "available", false))));

         final var output = compact(html);

         assertTrue(output.contains("Rice"));
         assertTrue(!output.contains("Coffee"));
      }
   }

   @Nested
   @DisplayName("Output syntax errors")
   class OutputSyntaxErrorTest {
      @Test
      @DisplayName("Throws when expression is empty")
      void throwsWhenExpressionIsEmpty() {
         assertThrows(
               TemplateRenderException.class,
               () -> engine.renderString(
                     "||",
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when closing pipe is missing")
      void throwsWhenClosingPipeIsMissing() {
         assertThrows(
               TemplateRenderException.class,
               () -> engine.renderString(
                     "|name",
                     Map.of("name", "Lemuel")));
      }

      @Test
      @DisplayName("Throws when conditional output expression is empty")
      void throwsWhenConditionalOutputExpressionIsEmpty() {
         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "| if user.active|",
                     Map.of(
                           "user", Map.of(
                                 "active", true))));
      }

      @Test
      @DisplayName("Throws when conditional output condition is empty")
      void throwsWhenConditionalOutputConditionIsEmpty() {
         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     "|user.name if |",
                     Map.of(
                           "user", Map.of(
                                 "name", "Lemuel"))));
      }
   }

   static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}