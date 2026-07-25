package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterTest {
   private final TemplateEngine engine = new TemplateEngine();

   @Nested
   @DisplayName("Text filters")
   class TextFilterTest {
      @Test
      @DisplayName("Supports upper filter")
      void supportsUpperFilter() {
         final var html = engine.renderString(
               "|user.name, upper|",
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel")));

         assertEquals("LEMUEL", html);
      }

      @Test
      @DisplayName("Supports lower filter")
      void supportsLowerFilter() {
         final var html = engine.renderString(
               "|user.name, lower|",
               Map.of(
                     "user", Map.of(
                           "name", "LEMUEL")));

         assertEquals("lemuel", html);
      }

      @Test
      @DisplayName("Supports trim filter")
      void supportsTrimFilter() {
         final var html = engine.renderString(
               "|name, trim|",
               Map.of("name", "   Lemuel   "));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Supports capitalize filter")
      void supportsCapitalizeFilter() {
         final var html = engine.renderString(
               "|name, capitalize|",
               Map.of("name", "lemuel"));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Supports slug filter")
      void supportsSlugFilter() {
         final var html = engine.renderString(
               "|title, slug|",
               Map.of("title", "  Hello World & Welcome!  "));

         assertEquals("hello-world-welcome", html);
      }

      @Test
      @DisplayName("Supports chained text filters")
      void supportsChainedTextFilters() {
         final var html = engine.renderString(
               "|name, trim, lower, capitalize|",
               Map.of("name", "   LEMUEL   "));

         assertEquals("Lemuel", html);
      }

      @Test
      @DisplayName("Text filters handle null as empty string")
      void textFiltersHandleNullAsEmptyString() {
         final var html = engine.renderString(
               "|missing, upper|",
               Map.of());

         assertEquals("", html);
      }
   }

   @Nested
   @DisplayName("Default filter")
   class DefaultFilterTest {
      @Test
      @DisplayName("Returns original value when value is truthy")
      void returnsOriginalValueWhenValueIsTruthy() {
         final var html = engine.renderString(
               "|nickname, default 'No nickname'|",
               Map.of("nickname", "Lem"));

         assertEquals("Lem", html);
      }

      @Test
      @DisplayName("Returns default value when value is missing")
      void returnsDefaultValueWhenValueIsMissing() {
         final var html = engine.renderString(
               "|nickname, default 'No nickname'|",
               Map.of());

         assertEquals("No nickname", html);
      }

      @Test
      @DisplayName("Returns default value when value is blank")
      void returnsDefaultValueWhenValueIsBlank() {
         final var html = engine.renderString(
               "|nickname, default 'No nickname'|",
               Map.of("nickname", "   "));

         assertEquals("No nickname", html);
      }

      @Test
      @DisplayName("Default value can come from model expression")
      void defaultValueCanComeFromModelExpression() {
         final var html = engine.renderString(
               "|user.nickname, default fallbackName|",
               Map.of(
                     "fallbackName", "Guest",
                     "user", Map.of()));

         assertEquals("Guest", html);
      }

      @Test
      @DisplayName("Throws when default filter has no argument")
      void throwsWhenDefaultFilterHasNoArgument() {
         assertThrows(
               IllegalArgumentException.class,
               () -> engine.renderString(
                     "|nickname, default|",
                     Map.of()));
      }
   }

   @Nested
   @DisplayName("Length filter")
   class LengthFilterTest {
      @Test
      @DisplayName("Returns string length")
      void returnsStringLength() {
         final var html = engine.renderString(
               "|name, length|",
               Map.of("name", "Lemuel"));

         assertEquals("6", html);
      }

      @Test
      @DisplayName("Returns list length")
      void returnsListLength() {
         final var html = engine.renderString(
               "|products, length|",
               Map.of(
                     "products", List.of(
                           "Rice",
                           "Coffee",
                           "Sugar")));

         assertEquals("3", html);
      }

      @Test
      @DisplayName("Returns map length")
      void returnsMapLength() {
         final var html = engine.renderString(
               "|settings, length|",
               Map.of(
                     "settings", Map.of(
                           "theme", "dark",
                           "language", "en")));

         assertEquals("2", html);
      }

      @Test
      @DisplayName("Returns zero for missing value")
      void returnsZeroForMissingValue() {
         final var html = engine.renderString(
               "|missing, length|",
               Map.of());

         assertEquals("0", html);
      }
   }

   @Nested
   @DisplayName("Number filters")
   class NumberFilterTest {
      @Test
      @DisplayName("Supports currency filter without symbol")
      void supportsCurrencyFilterWithoutSymbol() {
         final var html = engine.renderString(
               "|price, currency|",
               Map.of("price", 1234.5));

         assertEquals("1,234.50", html);
      }

      @Test
      @DisplayName("Supports currency filter with symbol")
      void supportsCurrencyFilterWithSymbol() {
         final var html = engine.renderString(
               "|price, currency '₱'|",
               Map.of("price", 1234.5));

         assertEquals("₱1,234.50", html);
      }

      @Test
      @DisplayName("Supports number filter with default pattern")
      void supportsNumberFilterWithDefaultPattern() {
         final var html = engine.renderString(
               "|total, number|",
               Map.of("total", 1234.5));

         assertEquals("1,234.5", html);
      }

      @Test
      @DisplayName("Supports number filter with custom pattern")
      void supportsNumberFilterWithCustomPattern() {
         final var html = engine.renderString(
               "|total, number '#,##0.00'|",
               Map.of("total", 1234.5));

         assertEquals("1,234.50", html);
      }

      @Test
      @DisplayName("Number filters return empty string for null")
      void numberFiltersReturnEmptyStringForNull() {
         final var html = engine.renderString(
               "|missing, currency '₱'|",
               Map.of());

         assertEquals("", html);
      }

      @Test
      @DisplayName("Throws when number filter receives non numeric value")
      void throwsWhenNumberFilterReceivesNonNumericValue() {
         assertThrows(
               NumberFormatException.class,
               () -> engine.renderString(
                     "|name, number|",
                     Map.of("name", "abc")));
      }
   }

   @Nested
   @DisplayName("Date and time filters")
   class DateAndTimeFilterTest {
      @Test
      @DisplayName("Supports date filter with default pattern")
      void supportsDateFilterWithDefaultPattern() {
         final var html = engine.renderString(
               "|createdAt, date|",
               Map.of("createdAt", LocalDate.of(2026, 7, 8)));

         assertEquals("2026-07-08", html);
      }

      @Test
      @DisplayName("Supports date filter with custom pattern")
      void supportsDateFilterWithCustomPattern() {
         final var html = engine.renderString(
               "|createdAt, date 'MM/dd/yyyy'|",
               Map.of("createdAt", LocalDate.of(2026, 7, 8)));

         assertEquals("07/08/2026", html);
      }

      @Test
      @DisplayName("Supports time filter with default pattern")
      void supportsTimeFilterWithDefaultPattern() {
         final var html = engine.renderString(
               "|createdAt, time|",
               Map.of("createdAt", LocalTime.of(14, 30, 15)));

         assertEquals("14:30:15", html);
      }

      @Test
      @DisplayName("Supports time filter with custom pattern")
      void supportsTimeFilterWithCustomPattern() {
         final var html = engine.renderString(
               "|createdAt, time 'HH:mm'|",
               Map.of("createdAt", LocalTime.of(14, 30, 15)));

         assertEquals("14:30", html);
      }

      @Test
      @DisplayName("Supports datetime filter with default pattern")
      void supportsDateTimeFilterWithDefaultPattern() {
         final var html = engine.renderString(
               "|createdAt, datetime|",
               Map.of("createdAt", LocalDateTime.of(2026, 7, 8, 14, 30, 15)));

         assertEquals("2026-07-08 14:30:15", html);
      }

      @Test
      @DisplayName("Supports datetime filter with custom pattern")
      void supportsDateTimeFilterWithCustomPattern() {
         final var html = engine.renderString(
               "|createdAt, datetime 'yyyy/MM/dd HH:mm'|",
               Map.of("createdAt", LocalDateTime.of(2026, 7, 8, 14, 30, 15)));

         assertEquals("2026/07/08 14:30", html);
      }

      @Test
      @DisplayName("Supports ISO date text")
      void supportsIsoDateText() {
         final var html = engine.renderString(
               "|createdAt, date 'yyyy/MM/dd'|",
               Map.of("createdAt", "2026-07-08"));

         assertEquals("2026/07/08", html);
      }

      @Test
      @DisplayName("Date filters return empty string for null")
      void dateFiltersReturnEmptyStringForNull() {
         final var html = engine.renderString(
               "|missing, date|",
               Map.of());

         assertEquals("", html);
      }
   }

   @Nested
   @DisplayName("Filters with output modes")
   class FilterWithOutputModeTest {
      @Test
      @DisplayName("Applies filter before HTML escaping")
      void appliesFilterBeforeHtmlEscaping() {
         final var html = engine.renderString(
               "|name, upper|",
               Map.of("name", "<lemuel>"));

         assertEquals("&lt;LEMUEL&gt;", html);
      }

      @Test
      @DisplayName("Applies filter before attribute escaping")
      void appliesFilterBeforeAttributeEscaping() {
         final var html = engine.renderString(
               "|attr name, lower|",
               Map.of("name", "LEM\"UEL"));

         assertEquals("lem&quot;uel", html);
      }

      @Test
      @DisplayName("Applies filter before URL encoding")
      void appliesFilterBeforeUrlEncoding() {
         final var html = engine.renderString(
               "|url query, lower|",
               Map.of("query", "Rice Coffee"));

         assertEquals("rice+coffee", html);
      }

      @Test
      @DisplayName("Applies filter before trusted HTML output")
      void appliesFilterBeforeTrustedHtmlOutput() {
         final var html = engine.renderString(
               "|html content, trim|",
               Map.of("content", "   <strong>Hello</strong>   "));

         assertEquals("<strong>Hello</strong>", html);
      }
   }

   @Nested
   @DisplayName("Filters inside blocks")
   class FilterInsideBlockTest {
      @Test
      @DisplayName("Supports filters inside if block")
      void supportsFiltersInsideIfBlock() {
         final var html = engine.renderString(
               """
                     |if user|
                        Hello, |user.name, upper|
                     |/if|
                     """,
               Map.of(
                     "user", Map.of(
                           "name", "Lemuel")));

         assertTrue(compact(html).contains("Hello, LEMUEL"));
      }

      @Test
      @DisplayName("Supports filters inside each block")
      void supportsFiltersInsideEachBlock() {
         final var html = engine.renderString(
               """
                     |each product in products|
                        |product.name, upper|: |product.price, currency '₱'|
                     |/each|
                     """,
               Map.of(
                     "products", List.of(
                           Map.of(
                                 "name", "Rice",
                                 "price", 120),
                           Map.of(
                                 "name", "Coffee",
                                 "price", 150.5))));

         final var output = compact(html);

         assertTrue(output.contains("RICE: ₱120.00"));
         assertTrue(output.contains("COFFEE: ₱150.50"));
      }
   }

   @Nested
   @DisplayName("Filter syntax errors")
   class FilterSyntaxErrorTest {
      @Test
      @DisplayName("Throws when filter is unknown")
      void throwsWhenFilterIsUnknown() {
         assertThrows(
               IllegalArgumentException.class,
               () -> engine.renderString(
                     "|name, unknownFilter|",
                     Map.of("name", "Lemuel")));
      }

      @Test
      @DisplayName("Throws when filter expression has trailing comma")
      void throwsWhenFilterExpressionHasTrailingComma() {
         assertThrows(
               IllegalArgumentException.class,
               () -> engine.renderString(
                     "|name,|",
                     Map.of("name", "Lemuel")));
      }

      @Test
      @DisplayName("Throws when filter expression has empty middle filter")
      void throwsWhenFilterExpressionHasEmptyMiddleFilter() {
         assertThrows(
               IllegalArgumentException.class,
               () -> engine.renderString(
                     "|name, upper, , lower|",
                     Map.of("name", "Lemuel")));
      }
   }

   @Nested
   @DisplayName("Comma separation for map and array literals")
   class LiteralCommaSeparationTest {
      @Test
      @DisplayName("Does not split map literals on comma")
      void doesNotSplitMapLiteralsOnComma() {
         final var html = engine.renderString(
               "|{ text: status, status: status }|",
               Map.of("{ text: status, status: status }", "Success"));
         assertEquals("Success", html);
      }

      @Test
      @DisplayName("Does not split array literals on comma")
      void doesNotSplitArrayLiteralsOnComma() {
         final var html = engine.renderString(
               "|[a, b, c]|",
               Map.of("[a, b, c]", "ArrayValue"));
         assertEquals("ArrayValue", html);
      }
   }

   private static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}