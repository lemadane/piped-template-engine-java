package io.lemadane.piped.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;

class ConditionalOutputTest {
    final TemplateEngine engine = new TemplateEngine();

    @Nested
    @DisplayName("Default conditional output")
    class DefaultConditionalOutputTest {
        @Test
        @DisplayName("Renders expression when condition is true")
        void rendersExpressionWhenConditionIsTrue() {
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
        @DisplayName("Renders empty string when condition is missing")
        void rendersEmptyStringWhenConditionIsMissing() {
            final var html = engine.renderString(
                    "|user.name if user.visible|",
                    Map.of(
                            "user", Map.of(
                                    "name", "Lemuel")));

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
        @DisplayName("Renders empty string when if not condition is false")
        void rendersEmptyStringWhenIfNotConditionIsFalse() {
            final var html = engine.renderString(
                    "|user.name if not user.deleted|",
                    Map.of(
                            "user", Map.of(
                                    "name", "Lemuel",
                                    "deleted", true)));

            assertEquals("", html);
        }
    }

    @Nested
    @DisplayName("Conditional output modes")
    class ConditionalOutputModeTest {
        @Test
        @DisplayName("Supports attr output when condition is true")
        void supportsAttrOutputWhenConditionIsTrue() {
            final var html = engine.renderString(
                    "|attr value if enabled|",
                    Map.of(
                            "value", "Rice \"Premium\"",
                            "enabled", true));

            assertEquals("Rice &quot;Premium&quot;", html);
        }

        @Test
        @DisplayName("Skips attr output when condition is false")
        void skipsAttrOutputWhenConditionIsFalse() {
            final var html = engine.renderString(
                    "|attr value if enabled|",
                    Map.of(
                            "value", "Rice \"Premium\"",
                            "enabled", false));

            assertEquals("", html);
        }

        @Test
        @DisplayName("Supports html output when condition is true")
        void supportsHtmlOutputWhenConditionIsTrue() {
            final var html = engine.renderString(
                    "|html content if published|",
                    Map.of(
                            "content", "<strong>Hello</strong>",
                            "published", true));

            assertEquals("<strong>Hello</strong>", html);
        }

        @Test
        @DisplayName("Skips html output when condition is false")
        void skipsHtmlOutputWhenConditionIsFalse() {
            final var html = engine.renderString(
                    "|html content if published|",
                    Map.of(
                            "content", "<strong>Hello</strong>",
                            "published", false));

            assertEquals("", html);
        }

        @Test
        @DisplayName("Supports url output when condition is true")
        void supportsUrlOutputWhenConditionIsTrue() {
            final var html = engine.renderString(
                    "|url query if query|",
                    Map.of("query", "rice coffee"));

            assertEquals("rice+coffee", html);
        }

        @Test
        @DisplayName("Skips url output when condition is false")
        void skipsUrlOutputWhenConditionIsFalse() {
            final var html = engine.renderString(
                    "|url query if query|",
                    Map.of("query", ""));

            assertEquals("", html);
        }

        @Test
        @DisplayName("Supports json output when condition is true")
        void supportsJsonOutputWhenConditionIsTrue() {
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
        @DisplayName("Skips json output when condition is false")
        void skipsJsonOutputWhenConditionIsFalse() {
            final var html = engine.renderString(
                    "|json product if product.visible|",
                    Map.of(
                            "product", Map.of(
                                    "name", "Rice",
                                    "visible", false)));

            assertEquals("", html);
        }
    }

    @Nested
    @DisplayName("Conditional output with if not")
    class ConditionalOutputIfNotTest {
        @Test
        @DisplayName("Supports attr output with if not")
        void supportsAttrOutputWithIfNot() {
            final var html = engine.renderString(
                    "<button |attr disabled if not disabled|>Save</button>",
                    Map.of("disabled", true));
            assertEquals("<button>Save</button>", html);
        }

        @Test
        @DisplayName("Supports html output with if not")
        void supportsHtmlOutputWithIfNot() {
            final var html = engine.renderString(
                    "|html fallbackHtml if not content|",
                    Map.of(
                            "fallbackHtml", "<p>No content</p>",
                            "content", ""));

            assertEquals("<p>No content</p>", html);
        }

        @Test
        @DisplayName("Supports url output with if not")
        void supportsUrlOutputWithIfNot() {
            final var html = engine.renderString(
                    "|url fallbackQuery if not query|",
                    Map.of(
                            "fallbackQuery", "all products",
                            "query", ""));

            assertEquals("all+products", html);
        }

        @Test
        @DisplayName("Supports json output with if not")
        void supportsJsonOutputWithIfNot() {
            final var html = engine.renderString(
                    "|json emptyState if not products|",
                    Map.of(
                            "emptyState", Map.of(
                                    "empty", true),
                            "products", List.of()));

            assertEquals("{\"empty\":true}", html);
        }
    }

    @Nested
    @DisplayName("Conditional output expressions")
    class ConditionalOutputExpressionTest {
        @Test
        @DisplayName("Supports null coalescing in rendered expression")
        void supportsNullCoalescingInRenderedExpression() {
            final var html = engine.renderString(
                    "|user.nickname ?? 'Guest' if user.visible|",
                    Map.of(
                            "user", Map.of(
                                    "visible", true)));

            assertEquals("Guest", html);
        }

        @Test
        @DisplayName("Supports ternary in rendered expression")
        void supportsTernaryInRenderedExpression() {
            final var html = engine.renderString(
                    "|user.active ? 'Active' : 'Inactive' if user.visible|",
                    Map.of(
                            "user", Map.of(
                                    "active", true,
                                    "visible", true)));

            assertEquals("Active", html);
        }

        @Test
        @DisplayName("Supports optional chaining in rendered expression")
        void supportsOptionalChainingInRenderedExpression() {
            final var html = engine.renderString(
                    "|user?.profile?.displayName ?? 'Guest' if user.visible|",
                    Map.of(
                            "user", Map.of(
                                    "visible", true)));

            assertEquals("Guest", html);
        }

        @Test
        @DisplayName("Supports complex condition with and")
        void supportsComplexConditionWithAnd() {
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
        @DisplayName("Supports complex condition with or")
        void supportsComplexConditionWithOr() {
            final var html = engine.renderString(
                    "|user.name if user.admin or user.manager|",
                    Map.of(
                            "user", Map.of(
                                    "name", "Lemuel",
                                    "admin", false,
                                    "manager", true)));

            assertEquals("Lemuel", html);
        }

        @Test
        @DisplayName("Supports optional chaining in condition")
        void supportsOptionalChainingInCondition() {
            final var html = engine.renderString(
                    "|user.name if user?.permissions?.canView|",
                    Map.of(
                            "user", Map.of(
                                    "name", "Lemuel",
                                    "permissions", Map.of(
                                            "canView", true))));

            assertEquals("Lemuel", html);
        }

        @Test
        @DisplayName("Supports comparison in condition")
        void supportsComparisonInCondition() {
            final var html = engine.renderString(
                    "|product.name if product.stock > 0|",
                    Map.of(
                            "product", Map.of(
                                    "name", "Rice",
                                    "stock", 10)));

            assertEquals("Rice", html);
        }
    }

    @Nested
    @DisplayName("Conditional output with filters")
    class ConditionalOutputFilterTest {
        @Test
        @DisplayName("Applies filters before default output")
        void appliesFiltersBeforeDefaultOutput() {
            final var html = engine.renderString(
                    "|user.name, trim, upper if user.active|",
                    Map.of(
                            "user", Map.of(
                                    "name", "   lemuel   ",
                                    "active", true)));

            assertEquals("LEMUEL", html);
        }

        @Test
        @DisplayName("Applies filters before attr output")
        void appliesFiltersBeforeAttrOutput() {
            final var html = engine.renderString(
                    "|attr value, trim if enabled|",
                    Map.of(
                            "value", "   Rice \"Premium\"   ",
                            "enabled", true));

            assertEquals("Rice &quot;Premium&quot;", html);
        }

        @Test
        @DisplayName("Applies filters before html output")
        void appliesFiltersBeforeHtmlOutput() {
            final var html = engine.renderString(
                    "|html content, trim if published|",
                    Map.of(
                            "content", "   <strong>Hello</strong>   ",
                            "published", true));

            assertEquals("<strong>Hello</strong>", html);
        }

        @Test
        @DisplayName("Applies filters before url output")
        void appliesFiltersBeforeUrlOutput() {
            final var html = engine.renderString(
                    "|url query, trim, lower if query|",
                    Map.of("query", "   RICE COFFEE   "));

            assertEquals("rice+coffee", html);
        }

        @Test
        @DisplayName("Applies filters before json output")
        void appliesFiltersBeforeJsonOutput() {
            final var html = engine.renderString(
                    "|json name, upper if visible|",
                    Map.of(
                            "name", "rice",
                            "visible", true));

            assertEquals("\"RICE\"", html);
        }

        @Test
        @DisplayName("Does not evaluate output filters when condition is false")
        void doesNotEvaluateOutputFiltersWhenConditionIsFalse() {
            final var html = engine.renderString(
                    "|price, number if visible|",
                    Map.of(
                            "price", "not-a-number",
                            "visible", false));

            assertEquals("", html);
        }
    }

    @Nested
    @DisplayName("Conditional output inside markup")
    class ConditionalOutputInsideMarkupTest {
        @Test
        @DisplayName("Renders boolean attribute when condition is true")
        void rendersBooleanAttributeWhenConditionIsTrue() {
            final var html = engine.renderString(
                    "<button |attr 'disabled' if not enabled|>Save</button>",
                    Map.of("enabled", false));

            assertEquals("<button disabled>Save</button>", html);
        }

        @Test
        @DisplayName("Skips boolean attribute when condition is false")
        void skipsBooleanAttributeWhenConditionIsFalse() {
            final var html = engine.renderString(
                    "<button |attr disabled if not enabled|>Save</button>",
                    Map.of("enabled", true));

            assertEquals("<button>Save</button>", html);
        }

        @Test
        @DisplayName("Renders conditional class value")
        void rendersConditionalClassValue() {
            final var html = engine.renderString(
                    "<li class=\"product |attr 'is-active' if product.active|\">Rice</li>",
                    Map.of(
                            "product", Map.of(
                                    "active", true)));

            assertEquals("<li class=\"product is-active\">Rice</li>", html);
        }

        @Test
        @DisplayName("Skips conditional class value")
        void skipsConditionalClassValue() {
            final var html = engine.renderString(
                    "<li class=\"product |attr 'is-active' if product.active|\">Rice</li>",
                    Map.of(
                            "product", Map.of(
                                    "active", false)));

            assertEquals("<li class=\"product\">Rice</li>", html);
        }

        @Test
        @DisplayName("Renders conditional href query value")
        void rendersConditionalHrefQueryValue() {
            final var html = engine.renderString(
                    "<a href=\"/search?q=|url query if query|\">Search</a>",
                    Map.of("query", "rice coffee"));

            assertEquals("<a href=\"/search?q=rice+coffee\">Search</a>", html);
        }

        @Test
        @DisplayName("Skips conditional href query value")
        void skipsConditionalHrefQueryValue() {
            final var html = engine.renderString(
                    "<a href=\"/search?q=|url query if query|\">Search</a>",
                    Map.of("query", ""));

            assertEquals("<a href=\"/search?q=\">Search</a>", html);
        }
    }

    @Nested
    @DisplayName("Conditional output inside blocks")
    class ConditionalOutputInsideBlockTest {
        @Test
        @DisplayName("Works inside if block")
        void worksInsideIfBlock() {
            final var html = engine.renderString(
                    """
                            |if user|
                               Hello, |user.name if user.active|
                            |/if|
                            """,
                    Map.of(
                            "user", Map.of(
                                    "name", "Lemuel",
                                    "active", true)));

            assertEquals("Hello, Lemuel", compact(html));
        }

        @Test
        @DisplayName("Works inside each block")
        void worksInsideEachBlock() {
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
                                            "available", false),
                                    Map.of(
                                            "name", "Sugar",
                                            "available", true))));

            final var output = compact(html);

            assertTrue(output.contains("Rice"));
            assertTrue(output.contains("Sugar"));
            assertTrue(!output.contains("Coffee"));
        }

        @Test
        @DisplayName("Works inside switch case")
        void worksInsideSwitchCase() {
            final var html = engine.renderString(
                    """
                            |switch status|
                               |case 'paid'|
                                  |message if visible|
                               |default|
                                  Hidden
                            |/switch|
                            """,
                    Map.of(
                            "status", "paid",
                            "message", "Payment complete",
                            "visible", true));

            assertEquals("Payment complete", compact(html));
        }
    }

    @Nested
    @DisplayName("Conditional output parser safety")
    class ConditionalOutputParserSafetyTest {
        @Test
        @DisplayName("Does not treat if inside quoted filter argument as conditional output")
        void doesNotTreatIfInsideQuotedFilterArgumentAsConditionalOutput() {
            final var html = engine.renderString(
                    "|name, default 'show if empty'|",
                    Map.of());

            assertEquals("show if empty", html);
        }

        @Test
        @DisplayName("Does not treat if inside double quoted filter argument as conditional output")
        void doesNotTreatIfInsideDoubleQuotedFilterArgumentAsConditionalOutput() {
            final var html = engine.renderString(
                    "|name, default \"show if empty\"|",
                    Map.of());

            assertEquals("show if empty", html);
        }

        @Test
        @DisplayName("Does not treat if inside parentheses as conditional output")
        void doesNotTreatIfInsideParenthesesAsConditionalOutput() {
            final var html = engine.renderString(
                    "|user.name ?? ('Guest if missing')|",
                    Map.of(
                            "user", Map.of()));

            assertEquals("Guest if missing", html);
        }

        @Test
        @DisplayName("Treats top-level if after parentheses as conditional output")
        void treatsTopLevelIfAfterParenthesesAsConditionalOutput() {
            final var html = engine.renderString(
                    "|(user.name ?? 'Guest') if user.visible|",
                    Map.of(
                            "user", Map.of(
                                    "name", "Lemuel",
                                    "visible", true)));

            assertEquals("Lemuel", html);
        }
    }

    @Nested
    @DisplayName("Conditional output syntax errors")
    class ConditionalOutputSyntaxErrorTest {
        @Test
        @DisplayName("Throws when expression before if is empty")
        void throwsWhenExpressionBeforeIfIsEmpty() {
            assertThrows(
                    TemplateSyntaxException.class,
                    () -> engine.renderString(
                            "| if user.active|",
                            Map.of(
                                    "user", Map.of(
                                            "active", true))));
        }

        @Test
        @DisplayName("Throws when condition after if is empty")
        void throwsWhenConditionAfterIfIsEmpty() {
            assertThrows(
                    TemplateSyntaxException.class,
                    () -> engine.renderString(
                            "|user.name if |",
                            Map.of(
                                    "user", Map.of(
                                            "name", "Lemuel"))));
        }

        @Test
        @DisplayName("Throws when attr condition after if is empty")
        void throwsWhenAttrConditionAfterIfIsEmpty() {
            assertThrows(
                    TemplateSyntaxException.class,
                    () -> engine.renderString(
                            "|attr value if |",
                            Map.of("value", "Rice")));
        }

        @Test
        @DisplayName("Throws when html condition after if is empty")
        void throwsWhenHtmlConditionAfterIfIsEmpty() {
            assertThrows(
                    TemplateSyntaxException.class,
                    () -> engine.renderString(
                            "|html value if |",
                            Map.of("value", "<p>Hello</p>")));
        }

        @Test
        @DisplayName("Throws when url condition after if is empty")
        void throwsWhenUrlConditionAfterIfIsEmpty() {
            assertThrows(
                    TemplateSyntaxException.class,
                    () -> engine.renderString(
                            "|url value if |",
                            Map.of("value", "rice")));
        }

        @Test
        @DisplayName("Throws when json condition after if is empty")
        void throwsWhenJsonConditionAfterIfIsEmpty() {
            assertThrows(
                    TemplateSyntaxException.class,
                    () -> engine.renderString(
                            "|json value if |",
                            Map.of("value", "rice")));
        }
    }

    @Nested
    class ConditionalAttributeTest {

        @TempDir
        Path templateRoot;

        @Test
        @DisplayName("Renders disabled attribute when saving is true")
        void rendersDisabledAttributeWhenSavingIsTrue() throws IOException {
            String html = render("button-disabled", """
                    <button |attr disabled if not saving|>
                        Save
                    </button>
                    """, Map.of("saving", true));

            assertTrue(html.contains("<button>"));
            assertTrue(html.contains("Save"));
        }

        @Test
        @DisplayName("Omits disabled attribute when readOnly is false")
        void omitsDisabledAttributeWhenReadOnlyIsFalse() throws IOException {
            String html = render("button-disabled", """
                    <button |attr disabled if readOnly|>
                        Save
                    </button>
                    """, Map.of("readOnly", false));

            assertFalse(html.contains("disabled"));
            assertTrue(html.contains("<button>"));
            assertTrue(html.contains("Save"));
        }

        @Test
        @DisplayName("Renders checked attribute when user is subscribed")
        void rendersCheckedAttributeWhenUserIsSubscribed() throws IOException {
            String html = render("checkbox-checked", """
                    <input type="checkbox" |attr checked if user.subscribed|>
                    """, Map.of("user", new User(true, "STAFF")));

            assertTrue(html.contains("type=\"checkbox\""));
            assertTrue(html.contains("checked"));
        }

        @Test
        @DisplayName("Omits checked attribute when user is not subscribed")
        void omitsCheckedAttributeWhenUserIsNotSubscribed() throws IOException {
            String html = render("checkbox-checked", """
                    <input type="checkbox" |attr checked if user.subscribed|>
                    """, Map.of("user", new User(false, "STAFF")));

            assertTrue(html.contains("type=\"checkbox\""));
            assertFalse(html.contains("checked"));
        }

        @Test
        @DisplayName("Renders selected attribute when user role is admin")
        void rendersSelectedAttributeWhenUserRoleIsAdmin() throws IOException {
            String html = render("option-selected", """
                    <option value="ADMIN" |attr selected if user.role == "ADMIN"|>
                        Admin
                    </option>
                    """, Map.of("user", new User(false, "ADMIN")));

            assertTrue(html.contains("value=\"ADMIN\""));
            assertTrue(html.contains("selected"));
            assertTrue(html.contains("Admin"));
        }

        @Test
        @DisplayName("Omits selected attribute when user role is not admin")
        void omitsSelectedAttributeWhenUserRoleIsNotAdmin() throws IOException {
            String html = render("option-selected", """
                    <option value="ADMIN" |attr selected if user.role == "ADMIN"|>
                        Admin
                    </option>
                    """, Map.of("user", new User(false, "STAFF")));

            assertTrue(html.contains("value=\"ADMIN\""));
            assertFalse(html.contains("selected"));
            assertTrue(html.contains("Admin"));
        }

        @Test
        @DisplayName("Renders aria-current attribute when route is dashboard")
        void rendersAriaCurrentAttributeWhenRouteIsDashboard() {
            final var html = engine.renderString(
                    "<a |attr aria-current='page' if route == 'dashboard'|>Dashboard</a>",
                    Map.of("route", "dashboard"));

            assertEquals(
                    "<a aria-current=\"page\">Dashboard</a>",
                    html);
        }

        @Test
        @DisplayName("Skips aria-current attribute when route is not dashboard")
        void skipsAriaCurrentAttributeWhenRouteIsNotDashboard() {
            final var html = engine.renderString(
                    "<a |attr aria-current='page' if route == 'dashboard'|>Dashboard</a>",
                    Map.of("route", "products"));

            assertEquals(
                    "<a>Dashboard</a>",
                    html);
        }

        @Test
        @DisplayName("Omits aria-current attribute when route is not dashboard")
        void omitsAriaCurrentAttributeWhenRouteIsNotDashboard() throws IOException {
            String html = render("aria-current", """
                    <a href="/dashboard" |attr aria-current="page" if activeRoute == "/dashboard"|>
                        Dashboard
                    </a>
                    """, Map.of("activeRoute", "/settings"));

            assertTrue(html.contains("href=\"/dashboard\""));
            assertFalse(html.contains("aria-current"));
            assertTrue(html.contains("Dashboard"));
        }

        String render(String templateName, String templateContent, Map<String, Object> model)
                throws IOException {
            Files.writeString(templateRoot.resolve(templateName + ".pte"), templateContent);

            TemplateEngine engine = new TemplateEngine(templateRoot);

            return engine.render(templateName, model);
        }

        record User(
                boolean subscribed,
                String role) {
        }

    }

    @Nested
    @DisplayName("Conditional attribute literal coverage")
    class ConditionalAttributeLiteralCoverageTest {
        static final List<String> ATTRIBUTE_LITERALS = List.of(
                "allowfullscreen",
                "async",
                "autofocus",
                "autoplay",
                "checked",
                "controls",
                "default",
                "defer",
                "disabled",
                "formnovalidate",
                "hidden",
                "inert",
                "ismap",
                "itemscope",
                "loop",
                "multiple",
                "muted",
                "nomodule",
                "novalidate",
                "open",
                "playsinline",
                "readonly",
                "required",
                "reversed",
                "selected",
                "aria-current");

        @TestFactory
        @DisplayName("Renders every unquoted conditional attribute literal when condition is true")
        List<DynamicTest> rendersEveryUnquotedConditionalAttributeLiteralWhenConditionIsTrue() {
            return ATTRIBUTE_LITERALS.stream()
                    .map(attributeName -> dynamicTest(
                            "renders " + attributeName,
                            () -> {
                                final var html = engine.renderString(
                                        "<input |attr " + attributeName + " if enabled|>",
                                        Map.of("enabled", true));

                                assertEquals("<input " + attributeName + ">", html);
                            }))
                    .toList();
        }

        @TestFactory
        @DisplayName("Skips every unquoted conditional attribute literal when condition is false")
        List<DynamicTest> skipsEveryUnquotedConditionalAttributeLiteralWhenConditionIsFalse() {
            return ATTRIBUTE_LITERALS.stream()
                    .map(attributeName -> dynamicTest(
                            "skips " + attributeName,
                            () -> {
                                final var html = engine.renderString(
                                        "<input |attr " + attributeName + " if enabled|>",
                                        Map.of("enabled", false));

                                assertEquals("<input>", html);
                            }))
                    .toList();
        }

        @TestFactory
        @DisplayName("Renders every unquoted conditional attribute literal with if not")
        List<DynamicTest> rendersEveryUnquotedConditionalAttributeLiteralWithIfNot() {
            return ATTRIBUTE_LITERALS.stream()
                    .map(attributeName -> dynamicTest(
                            "renders " + attributeName + " with if not",
                            () -> {
                                final var html = engine.renderString(
                                        "<input |attr " + attributeName + " if not enabled|>",
                                        Map.of("enabled", false));

                                assertEquals("<input " + attributeName + ">", html);
                            }))
                    .toList();
        }

        @TestFactory
        @DisplayName("Skips every unquoted conditional attribute literal with if not")
        List<DynamicTest> skipsEveryUnquotedConditionalAttributeLiteralWithIfNot() {
            return ATTRIBUTE_LITERALS.stream()
                    .map(attributeName -> dynamicTest(
                            "skips " + attributeName + " with if not",
                            () -> {
                                final var html = engine.renderString(
                                        "<input |attr " + attributeName + " if not enabled|>",
                                        Map.of("enabled", true));

                                assertEquals("<input>", html);
                            }))
                    .toList();
        }

        @TestFactory
        @DisplayName("Renders every quoted conditional attribute literal when condition is true")
        List<DynamicTest> rendersEveryQuotedConditionalAttributeLiteralWhenConditionIsTrue() {
            return ATTRIBUTE_LITERALS.stream()
                    .map(attributeName -> dynamicTest(
                            "renders quoted " + attributeName,
                            () -> {
                                final var html = engine.renderString(
                                        "<input |attr '" + attributeName + "' if enabled|>",
                                        Map.of("enabled", true));

                                assertEquals("<input " + attributeName + ">", html);
                            }))
                    .toList();
        }

        @TestFactory
        @DisplayName("Skips every quoted conditional attribute literal when condition is false")
        List<DynamicTest> skipsEveryQuotedConditionalAttributeLiteralWhenConditionIsFalse() {
            return ATTRIBUTE_LITERALS.stream()
                    .map(attributeName -> dynamicTest(
                            "skips quoted " + attributeName,
                            () -> {
                                final var html = engine.renderString(
                                        "<input |attr '" + attributeName + "' if enabled|>",
                                        Map.of("enabled", false));

                                assertEquals("<input>", html);
                            }))
                    .toList();
        }

        @Test
        @DisplayName("Supports aria-current with explicit value assignment")
        void supportsAriaCurrentWithExplicitValueAssignment() {
            final var html = engine.renderString(
                    "<a |attr aria-current='page' if route == 'dashboard'|>Dashboard</a>",
                    Map.of("route", "dashboard"));

            assertEquals("<a aria-current=\"page\">Dashboard</a>", html);
        }

        @Test
        @DisplayName("Skips aria-current value assignment cleanly")
        void skipsAriaCurrentValueAssignmentCleanly() {
            final var html = engine.renderString(
                    "<a |attr aria-current='page' if route == 'dashboard'|>Dashboard</a>",
                    Map.of("route", "products"));

            assertEquals("<a>Dashboard</a>", html);
        }
    }

    static String compact(String value) {
        return value
                .replaceAll("\\s+", " ")
                .trim();
    }
}