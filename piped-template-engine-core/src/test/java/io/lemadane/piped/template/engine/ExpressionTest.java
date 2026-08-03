package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExpressionTest {
   final TemplateEngine engine = new TemplateEngine();

   @Nested
   @DisplayName("Property access")
   class PropertyAccessTest {
      @Test
      @DisplayName("Reads value from map")
      void readsValueFromMap() {
         final var html = engine.renderString(
               "|title|",
               Map.of("title", "Dashboard"));
         assertEquals(
               "Dashboard",
               html);
      }

      @Test
      @DisplayName("Reads nested value from map")
      void readsNestedValueFromMap() {
         final var html = engine.renderString(
               "|user.profile.displayName|",
               Map.of(
                     "user",
                     Map.of(
                           "profile",
                           Map.of(
                                 "displayName",
                                 "Lemuel"))));
         assertEquals(
               "Lemuel",
               html);
      }

      @Test
      @DisplayName("Missing value renders empty string")
      void missingValueRendersEmptyString() {
         final var html = engine.renderString(
               "|missing|",
               Map.of());
         assertEquals(
               "",
               html);
      }

      record UserRecord(String name, int age) {}

      @Test
      @DisplayName("Reads values from Java record classes")
      void readsValuesFromJavaRecord() {
         final var html = engine.renderString(
               "|user.name| is |user.age| years old.",
               Map.of("user", new UserRecord("Lemuel", 30)));
         assertEquals(
               "Lemuel is 30 years old.",
               html);
      }
   }

   @Nested
   @DisplayName("Optional chaining")
   class OptionalChainingTest {
      @Test
      @DisplayName("Optional chaining returns value when path exists")
      void optionalChainingReturnsValueWhenPathExists() {
         final var html = engine.renderString(
               "|user?.profile?.displayName|",
               Map.of(
                     "user",
                     Map.of(
                           "profile",
                           Map.of(
                                 "displayName",
                                 "Lemuel"))));
         assertEquals(
               "Lemuel",
               html);
      }

      @Test
      @DisplayName("Optional chaining returns empty string when path is missing")
      void optionalChainingReturnsEmptyStringWhenPathIsMissing() {
         final var html = engine.renderString(
               "|user?.profile?.displayName|",
               Map.of());
         assertEquals(
               "",
               html);
      }

      @Test
      @DisplayName("Optional chaining works with fallback")
      void optionalChainingWorksWithFallback() {
         final var html = engine.renderString(
               "|user?.profile?.displayName ?? 'Guest'|",
               Map.of());
         assertEquals(
               "Guest",
               html);
      }
   }

   @Nested
   @DisplayName("Null coalescing")
   class NullCoalescingTest {
      @Test
      @DisplayName("Returns left value when present")
      void returnsLeftValueWhenPresent() {
         final var html = engine.renderString(
               "|name ?? 'Guest'|",
               Map.of(
                     "name",
                     "Lemuel"));
         assertEquals(
               "Lemuel",
               html);
      }

      @Test
      @DisplayName("Returns fallback when value is missing")
      void returnsFallbackWhenValueIsMissing() {
         final var html = engine.renderString(
               "|name ?? 'Guest'|",
               Map.of());
         assertEquals(
               "Guest",
               html);
      }

      @Test
      @DisplayName("Supports chained fallbacks")
      void supportsChainedFallbacks() {
         final var html = engine.renderString(
               """
                     |
                        invoice?.customer?.companyName
                           ?? invoice?.customer?.fullName
                           ?? 'Walk-in Customer'
                     |
                     """,
               Map.of(
                     "invoice",
                     Map.of(
                           "customer",
                           Map.of(
                                 "fullName",
                                 "Juan Dela Cruz"))));
         assertEquals(
               "Juan Dela Cruz",
               html.trim());
      }
   }

   @Nested
   @DisplayName("Ternary")
   class TernaryTest {
      @Test
      @DisplayName("Returns true branch")
      void returnsTrueBranch() {
         final var html = engine.renderString(
               "|user.active ? 'Active' : 'Inactive'|",
               Map.of(
                     "user",
                     Map.of(
                           "active",
                           true)));
         assertEquals(
               "Active",
               html);
      }

      @Test
      @DisplayName("Returns false branch")
      void returnsFalseBranch() {
         final var html = engine.renderString(
               "|user.active ? 'Active' : 'Inactive'|",
               Map.of(
                     "user", Map.of(
                           "active", false)));

         assertEquals("Inactive", html);
      }

      @Test
      @DisplayName("Works with optional chaining and null coalescing")
      void worksWithOptionalChainingAndNullCoalescing() {
         final var html = engine.renderString(
               """
                     |user?.profile?.displayName
                           ?? (user?.active
                           ? 'Active User'
                           : 'Guest')|
                           """,
               Map.of(
                     "user",
                     Map.of(
                           "active",
                           true)));
         assertEquals("Active User", html.trim());
      }
   }

   @Nested
   @DisplayName("Boolean operators")
   class BooleanOperatorTest {
      @Test
      @DisplayName("Supports not")
      void supportsNot() {
         final var html = engine.renderString(
               """
                     |if not user|
                        Guest
                     |else|
                        User
                     |/if|
                     """,
               Map.of());
         assertTrue(
               compact(html).contains("Guest"));
      }

      @Test
      @DisplayName("Supports and")
      void supportsAnd() {
         final var html = engine.renderString(
               """
                     |if user.active and user.verified|
                        Valid
                     |else|
                        Invalid
                     |/if|
                     """,
               Map.of(
                     "user", Map.of(
                           "active", true,
                           "verified", true)));
         assertTrue(
               compact(html)
                     .contains("Valid"));
      }

      @Test
      @DisplayName("Supports or")
      void supportsOr() {
         final var html = engine.renderString(
               """
                     |if user.admin or user.manager|
                        Allowed
                     |else|
                        Denied
                     |/if|
                     """,
               Map.of(
                     "user", Map.of(
                           "admin", false,
                           "manager", true)));
         assertTrue(
               compact(html)
                     .contains("Allowed"));
      }

      @Test
      @DisplayName("Supports nand")
      void supportsNand() {
         final var html = engine.renderString(
               """
                     |if user.active nand user.verified|
                        Not both
                     |else|
                        Both
                     |/if|
                     """,
               Map.of(
                     "user", Map.of(
                           "active", true,
                           "verified", false)));
         assertTrue(
               compact(html)
                     .contains("Not both"));
      }

      @Test
      @DisplayName("Supports nor")
      void supportsNor() {
         final var html = engine.renderString(
               """
                     |if user.admin nor user.manager|
                        Regular
                     |else|
                        Elevated
                     |/if|
                     """,
               Map.of(
                     "user", Map.of(
                           "admin", false,
                           "manager", false)));
         assertTrue(
               compact(html)
                     .contains("Regular"));
      }
   }

   @Nested
   @DisplayName("Comparison operators")
   class ComparisonOperatorTest {
      @Test
      @DisplayName("Supports equality")
      void supportsEquality() {
         final var html = engine.renderString(
               """
                     |if role == 'admin'|
                        Admin
                     |else|
                        User
                     |/if|
                     """,
               Map.of("role", "admin"));
         assertTrue(
               compact(html)
                     .contains("Admin"));
      }

      @Test
      @DisplayName("Supports inequality")
      void supportsInequality() {
         final var html = engine.renderString(
               """
                     |if role != 'admin'|
                        Not admin
                     |else|
                        Admin
                     |/if|
                     """,
               Map.of("role", "user"));
         assertTrue(
               compact(html)
                     .contains("Not admin"));
      }

      @Test
      @DisplayName("Supports greater than")
      void supportsGreaterThan() {
         final var html = engine.renderString(
               """
                     |if total > 0|
                        Has balance
                     |else|
                        No balance
                     |/if|
                     """,
               Map.of("total", 150));
         assertTrue(
               compact(html)
                     .contains("Has balance"));
      }

      @Test
      @DisplayName("Supports greater than or equal")
      void supportsGreaterThanOrEqual() {
         final var html = engine.renderString(
               """
                     |if stock >= 10|
                        Enough stock
                     |else|
                        Low stock
                     |/if|
                     """,
               Map.of("stock", 10));
         assertTrue(
               compact(html)
                     .contains("Enough stock"));
      }

      @Test
      @DisplayName("Supports less than")
      void supportsLessThan() {
         final var html = engine.renderString(
               """
                     |if stock < 10|
                        Low stock
                     |else|
                        Enough stock
                     |/if|
                     """,
               Map.of("stock", 5));
         assertTrue(
               compact(html)
                     .contains("Low stock"));
      }

      @Test
      @DisplayName("Supports less than or equal")
      void supportsLessThanOrEqual() {
         final var html = engine.renderString(
               """
                     |if stock <= 10|
                        Low or exact stock
                     |else|
                        High stock
                     |/if|
                     """,
               Map.of("stock", 10));
         assertTrue(
               compact(html)
                     .contains("Low or exact stock"));
      }
   }

   @Nested
   @DisplayName("Truthy and falsy values")
   class TruthyFalsyTest {
      @Test
      @DisplayName("Empty string is falsy")
      void emptyStringIsFalsy() {
         final var html = engine.renderString(
               """
                     |if name|
                        Has name
                     |else|
                        Missing name
                     |/if|
                     """,
               Map.of("name", ""));
         assertTrue(
               compact(html)
                     .contains("Missing name"));
      }

      @Test
      @DisplayName("Blank string is falsy")
      void blankStringIsFalsy() {
         final var html = engine.renderString(
               """
                     |if name|
                        Has name
                     |else|
                        Missing name
                     |/if|
                     """,
               Map.of("name", "     "));
         assertTrue(
               compact(html)
                     .contains(
                           "Missing name"));
      }

      @Test
      @DisplayName("Zero is falsy")
      void zeroIsFalsy() {
         final var html = engine.renderString(
               """
                     |if total|
                        Has total
                     |else|
                        No total
                     |/if|
                     """,
               Map.of("total", 0));
         assertTrue(
               compact(html)
                     .contains("No total"));
      }

      @Test
      @DisplayName("Non-zero number is truthy")
      void nonZeroNumberIsTruthy() {
         final var html = engine.renderString(
               """
                     |if total|
                        Has total
                     |else|
                        No total
                     |/if|
                     """,
               Map.of(
                     "total",
                     25));
         assertTrue(compact(html).contains("Has total"));
      }

      @Test
      @DisplayName("Empty list is falsy")
      void emptyListIsFalsy() {
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

      @Test
      @DisplayName("Non-empty list is truthy")
      void nonEmptyListIsTruthy() {
         final var html = engine.renderString(
               """
                     |if products|
                        Has products
                     |else|
                        No products
                     |/if|
                     """,
               Map.of("products", List.of("Rice")));
         assertTrue(compact(html).contains("Has products"));
      }

      @Test
      @DisplayName("Empty map is falsy")
      void emptyMapIsFalsy() {
         final var html = engine.renderString(
               """
                     |if settings|
                        Has settings
                     |else|
                        No settings
                     |/if|
                     """,
               Map.of("settings", Map.of()));
         assertTrue(
               compact(html)
                     .contains("No settings"));
      }

      @Test
      @DisplayName("Non-empty map is truthy")
      void nonEmptyMapIsTruthy() {
         final var html = engine.renderString(
               """
                     |if settings|
                        Has settings
                     |else|
                        No settings
                     |/if|
                     """,
               Map.of(
                     "settings", Map.of(
                           "theme", "dark")));
         assertTrue(
               compact(html)
                     .contains("Has settings"));
      }

      @Test
      @DisplayName("Optional empty is falsy")
      void optionalEmptyIsFalsy() {
         final var html = engine.renderString(
               """
                     |if value|
                        Has value
                     |else|
                        No value
                     |/if|
                     """,
               Map.of(
                     "value",
                     Optional.empty()));
         assertTrue(
               compact(html)
                     .contains("No value"));
      }

      @Test
      @DisplayName("Optional value is truthy")
      void optionalValueIsTruthy() {
         final var html = engine.renderString(
               """
                     |if value|
                        Has value
                     |else|
                        No value
                     |/if|
                     """,
               Map.of(
                     "value",
                     Optional.of("PTE")));
         assertTrue(
               compact(html)
                     .contains("Has value"));
      }
   }

   static String compact(String value) {
      return value
            .replaceAll("\\s+", " ")
            .trim();
   }
}
