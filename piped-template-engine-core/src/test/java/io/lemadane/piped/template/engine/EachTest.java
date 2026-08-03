package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;

class EachTest {
   final TemplateEngine engine = new TemplateEngine();

   @Nested
   @DisplayName("List loops")
   class ListLoopTest {
      @Test
      @DisplayName("Loops over a list")
      void loopsOverList() {
         final var html = engine.renderString(
               """
                     <ul>
                        |each product in products|
                           <li>|product.name|</li>
                        |/each|
                     </ul>
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"),
                           Map.of("name", "Coffee"),
                           Map.of("name", "Sugar"))));

         final var output = compact(html);

         assertTrue(output.contains("<li>Rice</li>"));
         assertTrue(output.contains("<li>Coffee</li>"));
         assertTrue(output.contains("<li>Sugar</li>"));
      }

      @Test
      @DisplayName("Renders separator between items except the last")
      void rendersSeparatorBetweenItems() {
         final var html = engine.renderString(
               "|each tag in tags||tag||separator|, |/separator||/each|",
               Map.of("tags", List.of("java", "spring", "pte")));

         org.junit.jupiter.api.Assertions.assertEquals("java, spring, pte", html);
      }

      @Test
      @DisplayName("Throws syntax error when separator is outside each loop")
      void throwsErrorWhenSeparatorOutsideLoop() {
         assertThrows(TemplateSyntaxException.class, () -> {
            engine.renderString("|separator|, |/separator|", Map.of());
         });
      }

      @Test
      @DisplayName("Renders else block when list is empty")
      void rendersElseBlockWhenListIsEmpty() {
         final var html = engine.renderString(
               """
                     <ul>
                        |each product in products|
                           <li>|product.name|</li>
                        |else|
                           <li>No products found.</li>
                        |/each|
                     </ul>
                     """,
               Map.of("products", List.of()));

         final var output = compact(html);

         assertTrue(output.contains("<li>No products found.</li>"));
         assertFalse(output.contains("|product.name|"));
      }

      @Test
      @DisplayName("Renders else block when list is missing")
      void rendersElseBlockWhenListIsMissing() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |product.name|
                     |else|
                        No products found.
                     |/each|
                     """,
               Map.of());

         final var output = compact(html);

         assertTrue(output.contains("No products found."));
      }
   }

   @Nested
   @DisplayName("Each metadata")
   class EachMetadataTest {
      @Test
      @DisplayName("Supports each.index and each.index0")
      void supportsEachIndexAndIndexZero() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |each.index|/|each.index0|: |product.name|
                     |/each|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"),
                           Map.of("name", "Coffee"))));

         final var output = compact(html);

         assertTrue(output.contains("1/0: Rice"));
         assertTrue(output.contains("2/1: Coffee"));
      }

      @Test
      @DisplayName("Supports each.first and each.last")
      void supportsEachFirstAndLast() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |if each.first|
                           First: |product.name|
                        |/if|

                        |if each.last|
                           Last: |product.name|
                        |/if|
                     |/each|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"),
                           Map.of("name", "Coffee"),
                           Map.of("name", "Sugar"))));

         final var output = compact(html);

         assertTrue(output.contains("First: Rice"));
         assertTrue(output.contains("Last: Sugar"));
         assertFalse(output.contains("First: Coffee"));
         assertFalse(output.contains("Last: Coffee"));
      }

      @Test
      @DisplayName("Supports each.even and each.odd")
      void supportsEachEvenAndOdd() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |if each.odd|
                           Odd: |product.name|
                        |else|
                           Even: |product.name|
                        |/if|
                     |/each|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"),
                           Map.of("name", "Coffee"),
                           Map.of("name", "Sugar"))));

         final var output = compact(html);

         assertTrue(output.contains("Odd: Rice"));
         assertTrue(output.contains("Even: Coffee"));
         assertTrue(output.contains("Odd: Sugar"));
      }
   }

   @Nested
   @DisplayName("Map loops")
   class MapLoopTest {

      @Test
      @DisplayName("Loops over map using key and value variables")
      void loopsOverMapUsingKeyAndValueVariables() {
         final var prices = new LinkedHashMap<String, Object>();
         prices.put("Rice", 120);
         prices.put("Coffee", 150);
         prices.put("Sugar", 90);
         final var html = engine.renderString(
               """
                     <ul>
                        |each productName, price in prices|
                           <li>|productName| - ₱|price|</li>
                        |/each|
                     </ul>
                     """,
               Map.of("prices", prices));
         final var output = compact(html);
         System.out.println("output: " + output);
         assertTrue(output.contains("<li>Rice - ₱120</li>"));
         assertTrue(output.contains("<li>Coffee - ₱150</li>"));
         assertTrue(output.contains("<li>Sugar - ₱90</li>"));
      }

      @Test
      @DisplayName("Loops over map using entry variable")
      void loopsOverMapUsingEntryVariable() {
         final var settings = new LinkedHashMap<String, Object>();
         settings.put("theme", "dark");
         settings.put("language", "en");

         final var html = engine.renderString(
               """
                     |each entry in settings|
                        |entry.key| = |entry.value|
                     |/each|
                     """,
               Map.of("settings", settings));

         final var output = compact(html);

         assertTrue(output.contains("theme = dark"));
         assertTrue(output.contains("language = en"));
      }

      @Test
      @DisplayName("Renders else block when map is empty")
      void rendersElseBlockWhenMapIsEmpty() {
         final var html = engine.renderString(
               """
                     |each key, value in settings|
                        |key| = |value|
                     |else|
                        No settings.
                     |/each|
                     """,
               Map.of("settings", Map.of()));

         final var output = compact(html);

         assertTrue(output.contains("No settings."));
      }

      @Test
      @DisplayName("Each metadata works in map loops")
      void eachMetadataWorksInMapLoops() {
         final var settings = new LinkedHashMap<String, Object>();
         settings.put("theme", "dark");
         settings.put("language", "en");

         final var html = engine.renderString(
               """
                     |each key, value in settings|
                        |each.index|: |key| = |value|
                     |/each|
                     """,
               Map.of("settings", settings));

         final var output = compact(html);

         assertTrue(output.contains("1: theme = dark"));
         assertTrue(output.contains("2: language = en"));
      }
   }

   @Nested
   @DisplayName("Nested loops")
   class NestedLoopTest {
      @Test
      @DisplayName("Supports nested each blocks")
      void supportsNestedEachBlocks() {
         final var html = engine.renderString(
               """
                     |each category in categories|
                        Category: |category.name|
                        |each product in category.products|
                           Product: |product.name|
                        |/each|
                     |/each|
                     """,
               Map.of(
                     "categories", List.of(
                           Map.of(
                                 "name", "Food",
                                 "products", List.of(
                                       Map.of("name", "Rice"),
                                       Map.of("name", "Coffee"))),
                           Map.of(
                                 "name", "Supplies",
                                 "products", List.of(
                                       Map.of("name", "Soap"))))));

         final var output = compact(html);

         assertTrue(output.contains("Category: Food"));
         assertTrue(output.contains("Product: Rice"));
         assertTrue(output.contains("Product: Coffee"));
         assertTrue(output.contains("Category: Supplies"));
         assertTrue(output.contains("Product: Soap"));
      }

      @Test
      @DisplayName("Supports each blocks inside if blocks")
      void supportsEachBlocksInsideIfBlocks() {
         final var html = engine.renderString(
               """
                     |if products|
                        |each product in products|
                           |product.name|
                        |/each|
                     |else|
                        No products.
                     |/if|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of("name", "Rice"))));

         final var output = compact(html);

         assertTrue(output.contains("Rice"));
         assertFalse(output.contains("No products."));
      }
   }

   @Nested
   @DisplayName("Each syntax errors")
   class EachSyntaxErrorTest {
      @Test
      @DisplayName("Throws when each block is not closed")
      void throwsWhenEachBlockIsNotClosed() {
         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |each product in products|
                              |product.name|
                           """,
                     Map.of(
                           "products", List.of(
                                 Map.of("name", "Rice")))));
      }

      @Test
      @DisplayName("Throws when /each appears without matching each")
      void throwsWhenClosingEachAppearsWithoutMatchingEach() {
         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |/each|
                           """,
                     Map.of()));
      }

      @Test
      @DisplayName("Throws when each statement is invalid")
      void throwsWhenEachStatementIsInvalid() {
         assertThrows(
               TemplateSyntaxException.class,
               () -> engine.renderString(
                     """
                           |each products|
                              Invalid
                           |/each|
                           """,
                     Map.of(
                           "products", List.of(
                                 Map.of("name", "Rice")))));
      }
   }

   static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}