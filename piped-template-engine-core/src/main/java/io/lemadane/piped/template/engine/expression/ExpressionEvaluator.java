package io.lemadane.piped.template.engine.expression;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ExpressionEvaluator {
      private final PropertyReader propertyReader;

      public ExpressionEvaluator() {
            this.propertyReader = new PropertyReader();
      }

      public Object evaluate(String expression, TemplateContext context) {
            final var trimmedExpression = expression.trim();

            if (trimmedExpression.isEmpty()) {
                  return null;
            }

            return evaluateValue(trimmedExpression, context);
      }

      public boolean evaluateBoolean(String expression, TemplateContext context) {
            return toBoolean(evaluateCondition(expression.trim(), context));
      }

      public boolean valuesEqual(Object left, Object right) {
            if (left == null || right == null) {
                  return left == right;
            }

            if (left instanceof Number || right instanceof Number) {
                  final var leftNumber = new BigDecimal(String.valueOf(left));
                  final var rightNumber = new BigDecimal(String.valueOf(right));

                  return leftNumber.compareTo(rightNumber) == 0;
            }

            return Objects.equals(left, right);
      }

      private Object evaluateCondition(String expression, TemplateContext context) {
            final var trimmedExpression = expression.trim();

            final var norIndex = findWordOperator(trimmedExpression, "nor");

            if (norIndex != -1) {
                  final var left = trimmedExpression.substring(0, norIndex);
                  final var right = trimmedExpression.substring(norIndex + "nor".length());

                  return !(evaluateBoolean(left, context) || evaluateBoolean(right, context));
            }

            final var orIndex = findWordOperator(trimmedExpression, "or");

            if (orIndex != -1) {
                  final var left = trimmedExpression.substring(0, orIndex);
                  final var right = trimmedExpression.substring(orIndex + "or".length());

                  return evaluateBoolean(left, context) || evaluateBoolean(right, context);
            }

            final var nandIndex = findWordOperator(trimmedExpression, "nand");

            if (nandIndex != -1) {
                  final var left = trimmedExpression.substring(0, nandIndex);
                  final var right = trimmedExpression.substring(nandIndex + "nand".length());

                  return !(evaluateBoolean(left, context) && evaluateBoolean(right, context));
            }

            final var andIndex = findWordOperator(trimmedExpression, "and");

            if (andIndex != -1) {
                  final var left = trimmedExpression.substring(0, andIndex);
                  final var right = trimmedExpression.substring(andIndex + "and".length());

                  return evaluateBoolean(left, context) && evaluateBoolean(right, context);
            }

            if (startsWithWord(trimmedExpression, "not")) {
                  final var value = trimmedExpression.substring("not".length()).trim();

                  return !evaluateBoolean(value, context);
            }

            final var comparison = findComparison(trimmedExpression);

            if (comparison != null) {
                  final var left = evaluateValue(comparison.left(), context);
                  final var right = evaluateValue(comparison.right(), context);

                  return compare(left, right, comparison.operator());
            }

            return evaluateValue(trimmedExpression, context);
      }

      private Object evaluateValue(String expression, TemplateContext context) {
            final var trimmedExpression = removeWrappingParentheses(expression.trim());

            if (trimmedExpression.isEmpty()) {
                  return null;
            }

            final var filteredExpression = parseFilteredExpression(trimmedExpression);

            if (filteredExpression != null) {
                  return evaluateFilteredExpression(filteredExpression, context);
            }

            final var ternaryExpression = findTernaryExpression(trimmedExpression);

            if (ternaryExpression != null) {
                  if (evaluateBoolean(ternaryExpression.condition(), context)) {
                        return evaluateValue(ternaryExpression.trueExpression(), context);
                  }

                  return evaluateValue(ternaryExpression.falseExpression(), context);
            }

            final var fallbackIndex = findNullCoalescingOperator(trimmedExpression);

            if (fallbackIndex != -1) {
                  final var leftExpression = trimmedExpression.substring(0, fallbackIndex).trim();
                  final var rightExpression = trimmedExpression.substring(fallbackIndex + "??".length()).trim();

                  final var leftValue = evaluateValue(leftExpression, context);

                  if (leftValue != null) {
                        return leftValue;
                  }

                  return evaluateValue(rightExpression, context);
            }

            if (isQuotedString(trimmedExpression)) {
                  return trimmedExpression.substring(1, trimmedExpression.length() - 1);
            }

            if ("true".equals(trimmedExpression)) {
                  return true;
            }

            if ("false".equals(trimmedExpression)) {
                  return false;
            }

            if ("null".equals(trimmedExpression)) {
                  return null;
            }

            if (isNumber(trimmedExpression)) {
                  return new BigDecimal(trimmedExpression);
            }

            final var plusIndex = findTopLevelChar(trimmedExpression, '+');
            if (plusIndex != -1) {
                  final var leftExpr = trimmedExpression.substring(0, plusIndex).trim();
                  if (!leftExpr.isEmpty()) {
                        final var leftVal = evaluateValue(leftExpr, context);
                        final var rightVal = evaluateValue(trimmedExpression.substring(plusIndex + 1).trim(), context);
                        if (leftVal instanceof String || rightVal instanceof String) {
                              return String.valueOf(leftVal) + String.valueOf(rightVal);
                        }
                        return toBigDecimal(leftVal).add(toBigDecimal(rightVal));
                  }
            }

            final var minusIndex = findTopLevelChar(trimmedExpression, '-');
            if (minusIndex != -1) {
                  final var leftExpr = trimmedExpression.substring(0, minusIndex).trim();
                  if (!leftExpr.isEmpty()) {
                        final var leftVal = evaluateValue(leftExpr, context);
                        final var rightVal = evaluateValue(trimmedExpression.substring(minusIndex + 1).trim(), context);
                        return toBigDecimal(leftVal).subtract(toBigDecimal(rightVal));
                  }
            }

            final var starIndex = findTopLevelChar(trimmedExpression, '*');
            if (starIndex != -1) {
                  final var leftExpr = trimmedExpression.substring(0, starIndex).trim();
                  if (!leftExpr.isEmpty()) {
                        final var leftVal = evaluateValue(leftExpr, context);
                        final var rightVal = evaluateValue(trimmedExpression.substring(starIndex + 1).trim(), context);
                        return toBigDecimal(leftVal).multiply(toBigDecimal(rightVal));
                  }
            }

            final var slashIndex = findTopLevelSlash(trimmedExpression);
            if (slashIndex != -1) {
                  final var leftExpr = trimmedExpression.substring(0, slashIndex).trim();
                  if (!leftExpr.isEmpty()) {
                        final var leftVal = evaluateValue(leftExpr, context);
                        final var rightVal = evaluateValue(trimmedExpression.substring(slashIndex + 1).trim(), context);
                        return toBigDecimal(leftVal).divide(toBigDecimal(rightVal), java.math.MathContext.DECIMAL128);
                  }
            }

            return propertyReader.readPath(trimmedExpression, context);
      }

      private boolean compare(Object left, Object right, String operator) {
            return switch (operator) {
                  case "==" -> valuesEqual(left, right);
                  case "!=" -> !valuesEqual(left, right);
                  case ">" -> compareOrdered(left, right) > 0;
                  case ">=" -> compareOrdered(left, right) >= 0;
                  case "<" -> compareOrdered(left, right) < 0;
                  case "<=" -> compareOrdered(left, right) <= 0;
                  default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
            };
      }

      private int compareOrdered(Object left, Object right) {
            if (left == null || right == null) {
                  throw new IllegalArgumentException("Cannot compare null values.");
            }

            if (left instanceof Number || right instanceof Number) {
                  final var leftNumber = new BigDecimal(String.valueOf(left));
                  final var rightNumber = new BigDecimal(String.valueOf(right));

                  return leftNumber.compareTo(rightNumber);
            }

            return String.valueOf(left).compareTo(String.valueOf(right));
      }

      private boolean toBoolean(Object value) {
            if (value == null) {
                  return false;
            }

            if (value instanceof Optional<?> optionalValue) {
                  return optionalValue.isPresent()
                              && toBoolean(optionalValue.get());
            }

            if (value instanceof Boolean booleanValue) {
                  return booleanValue;
            }

            if (value instanceof Number numberValue) {
                  return BigDecimal.ZERO.compareTo(new BigDecimal(String.valueOf(numberValue))) != 0;
            }

            if (value instanceof CharSequence textValue) {
                  return !textValue.toString().isBlank();
            }

            if (value instanceof Collection<?> collectionValue) {
                  return !collectionValue.isEmpty();
            }

            if (value instanceof Map<?, ?> mapValue) {
                  return !mapValue.isEmpty();
            }

            if (value.getClass().isArray()) {
                  return Array.getLength(value) > 0;
            }

            return true;
      }

      private boolean isQuotedString(String value) {
            return value.length() >= 2
                        && (value.startsWith("\"") && value.endsWith("\"")
                                    || value.startsWith("'") && value.endsWith("'"));
      }

      private boolean isNumber(String value) {
            return value.matches("-?\\d+(\\.\\d+)?");
      }

      private boolean startsWithWord(String expression, String word) {
            return expression.equals(word)
                        || expression.startsWith(word + " ");
      }

      private TernaryExpression findTernaryExpression(String expression) {
            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;
            int questionIndex = -1;

            for (int index = 0; index < expression.length(); index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == '(') {
                        parenthesisDepth++;
                        continue;
                  }

                  if (current == ')') {
                        parenthesisDepth--;
                        continue;
                  }

                  if (parenthesisDepth != 0) {
                        continue;
                  }

                  if (current != '?') {
                        continue;
                  }

                  if (isOptionalChainingQuestionMark(expression, index)) {
                        continue;
                  }

                  if (isNullCoalescingQuestionMark(expression, index)) {
                        continue;
                  }

                  questionIndex = index;
                  break;
            }

            if (questionIndex == -1) {
                  return null;
            }

            final var colonIndex = findTernaryColon(expression, questionIndex + 1);

            if (colonIndex == -1) {
                  throw new IllegalArgumentException("Missing ':' in ternary expression: " + expression);
            }

            final var condition = expression.substring(0, questionIndex).trim();
            final var trueExpression = expression.substring(questionIndex + 1, colonIndex).trim();
            final var falseExpression = expression.substring(colonIndex + 1).trim();

            if (condition.isBlank() || trueExpression.isBlank() || falseExpression.isBlank()) {
                  throw new IllegalArgumentException("Invalid ternary expression: " + expression);
            }

            return new TernaryExpression(
                        condition,
                        trueExpression,
                        falseExpression);
      }

      private int findTernaryColon(String expression, int startIndex) {
            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;
            int nestedTernaryDepth = 0;

            for (int index = startIndex; index < expression.length(); index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == '(') {
                        parenthesisDepth++;
                        continue;
                  }

                  if (current == ')') {
                        parenthesisDepth--;
                        continue;
                  }

                  if (parenthesisDepth != 0) {
                        continue;
                  }

                  if (current == '?') {
                        if (isOptionalChainingQuestionMark(expression, index)) {
                              continue;
                        }

                        if (isNullCoalescingQuestionMark(expression, index)) {
                              continue;
                        }

                        nestedTernaryDepth++;
                        continue;
                  }

                  if (current == ':') {
                        if (nestedTernaryDepth == 0) {
                              return index;
                        }

                        nestedTernaryDepth--;
                  }
            }

            return -1;
      }

      private boolean isOptionalChainingQuestionMark(String expression, int index) {
            return index + 1 < expression.length()
                        && expression.charAt(index + 1) == '.';
      }

      private boolean isNullCoalescingQuestionMark(String expression, int index) {
            final var previousIsQuestionMark = index > 0
                        && expression.charAt(index - 1) == '?';

            final var nextIsQuestionMark = index + 1 < expression.length()
                        && expression.charAt(index + 1) == '?';

            return previousIsQuestionMark || nextIsQuestionMark;
      }

      private int findNullCoalescingOperator(String expression) {
            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;

            for (int index = 0; index < expression.length() - 1; index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == '(') {
                        parenthesisDepth++;
                        continue;
                  }

                  if (current == ')') {
                        parenthesisDepth--;
                        continue;
                  }

                  if (parenthesisDepth == 0 && expression.startsWith("??", index)) {
                        return index;
                  }
            }

            return -1;
      }

      private int findWordOperator(String expression, String operator) {
            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;

            for (int index = 0; index <= expression.length() - operator.length(); index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == '(') {
                        parenthesisDepth++;
                        continue;
                  }

                  if (current == ')') {
                        parenthesisDepth--;
                        continue;
                  }

                  if (parenthesisDepth != 0) {
                        continue;
                  }

                  if (!expression.startsWith(operator, index)) {
                        continue;
                  }

                  final var beforeIsBoundary = index == 0
                              || Character.isWhitespace(expression.charAt(index - 1));

                  final var afterIndex = index + operator.length();
                  final var afterIsBoundary = afterIndex >= expression.length()
                              || Character.isWhitespace(expression.charAt(afterIndex));

                  if (beforeIsBoundary && afterIsBoundary) {
                        return index;
                  }
            }

            return -1;
      }

      private Comparison findComparison(String expression) {
            final var operators = new String[] { "==", "!=", ">=", "<=", ">", "<" };

            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;

            for (int index = 0; index < expression.length(); index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == '(') {
                        parenthesisDepth++;
                        continue;
                  }

                  if (current == ')') {
                        parenthesisDepth--;
                        continue;
                  }

                  if (parenthesisDepth != 0) {
                        continue;
                  }

                  for (final var operator : operators) {
                        if (expression.startsWith(operator, index)) {
                              return new Comparison(
                                          expression.substring(0, index).trim(),
                                          operator,
                                          expression.substring(index + operator.length()).trim());
                        }
                  }
            }

            return null;
      }

      private String removeWrappingParentheses(String expression) {
            var result = expression;

            while (result.startsWith("(") && result.endsWith(")") && wrapsWholeExpression(result)) {
                  result = result.substring(1, result.length() - 1).trim();
            }

            return result;
      }

      private boolean wrapsWholeExpression(String expression) {
            int depth = 0;
            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;

            for (int index = 0; index < expression.length(); index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == '(') {
                        depth++;
                  }

                  if (current == ')') {
                        depth--;
                  }

                  if (depth == 0 && index < expression.length() - 1) {
                        return false;
                  }
            }

            return depth == 0;
      }

      private FilteredExpression parseFilteredExpression(String expression) {
            final var parts = splitByTopLevelComma(expression);

            if (parts.size() == 1) {
                  return null;
            }

            return new FilteredExpression(
                        parts.get(0),
                        List.copyOf(parts.subList(1, parts.size())));
      }

      public List<String> splitByTopLevelComma(String expression) {
            final var parts = new ArrayList<String>();
            final var currentPart = new StringBuilder();

            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;
            int bracketDepth = 0;
            int braceDepth = 0;

            for (int index = 0; index < expression.length(); index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        currentPart.append(current);
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        currentPart.append(current);
                        continue;
                  }

                  if (!insideSingleQuote && !insideDoubleQuote) {
                        if (current == '(') {
                              parenthesisDepth++;
                              currentPart.append(current);
                              continue;
                        }

                        if (current == ')') {
                              parenthesisDepth--;
                              currentPart.append(current);
                              continue;
                        }

                        if (current == '[') {
                              bracketDepth++;
                              currentPart.append(current);
                              continue;
                        }

                        if (current == ']') {
                              bracketDepth--;
                              currentPart.append(current);
                              continue;
                        }

                        if (current == '{') {
                              braceDepth++;
                              currentPart.append(current);
                              continue;
                        }

                        if (current == '}') {
                              braceDepth--;
                              currentPart.append(current);
                              continue;
                        }

                        if (parenthesisDepth == 0 && bracketDepth == 0 && braceDepth == 0 && current == ',') {
                              final var part = currentPart.toString().trim();

                              if (part.isBlank()) {
                                    throw new IllegalArgumentException("Invalid filter expression: " + expression);
                              }

                              parts.add(part);
                              currentPart.setLength(0);
                              continue;
                        }
                  }

                  currentPart.append(current);
            }

            final var lastPart = currentPart.toString().trim();

            if (lastPart.isBlank()) {
                  throw new IllegalArgumentException("Invalid filter expression: " + expression);
            }

            parts.add(lastPart);

            return parts;
      }

      private Object evaluateFilteredExpression(FilteredExpression filteredExpression, TemplateContext context) {
            Object value = evaluateValue(filteredExpression.valueExpression(), context);

            for (final var filterSource : filteredExpression.filters()) {
                  value = applyFilter(value, filterSource, context);
            }

            return value;
      }

      private Object applyFilter(Object value, String filterSource, TemplateContext context) {
            final var filterCall = parseFilterCall(filterSource);

            return switch (filterCall.name()) {
                  case "upper" -> stringValue(value).toUpperCase(Locale.ROOT);
                  case "lower" -> stringValue(value).toLowerCase(Locale.ROOT);
                  case "trim" -> stringValue(value).trim();
                  case "capitalize" -> capitalizeText(stringValue(value));
                  case "slug" -> slugify(stringValue(value));
                  case "length" -> lengthOf(value);
                  case "default" -> defaultValue(value, filterCall.argumentExpression(), context);
                  case "currency" -> currencyValue(value, filterCall.argumentExpression(), context);
                  case "number" -> numberValue(value, filterCall.argumentExpression(), context);
                  case "date" -> dateValue(value, filterCall.argumentExpression(), context);
                  case "time" -> timeValue(value, filterCall.argumentExpression(), context);
                  case "datetime" -> dateTimeValue(value, filterCall.argumentExpression(), context);
                  default -> throw new IllegalArgumentException("Unknown filter: " + filterCall.name());
            };
      }

      private FilterCall parseFilterCall(String filterSource) {
            final var trimmedFilterSource = filterSource.trim();

            if (trimmedFilterSource.isBlank()) {
                  throw new IllegalArgumentException("Filter name must not be empty.");
            }

            for (int index = 0; index < trimmedFilterSource.length(); index++) {
                  if (Character.isWhitespace(trimmedFilterSource.charAt(index))) {
                        return new FilterCall(
                                    trimmedFilterSource.substring(0, index).trim(),
                                    trimmedFilterSource.substring(index + 1).trim());
                  }
            }

            return new FilterCall(
                        trimmedFilterSource,
                        "");
      }

      private String stringValue(Object value) {
            if (value == null) {
                  return "";
            }

            return String.valueOf(value);
      }

      private String capitalizeText(String value) {
            if (value.isEmpty()) {
                  return value;
            }

            if (value.length() == 1) {
                  return value.toUpperCase(Locale.ROOT);
            }

            return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
      }

      private String slugify(String value) {
            if (value == null || value.isBlank()) {
                  return "";
            }
            String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
            return normalized.replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9\\s-]", "")
                        .trim()
                        .replaceAll("[\\s_]+", "-")
                        .replaceAll("-+", "-");
      }

      private int lengthOf(Object value) {
            if (value == null) {
                  return 0;
            }

            if (value instanceof CharSequence textValue) {
                  return textValue.length();
            }

            if (value instanceof Collection<?> collectionValue) {
                  return collectionValue.size();
            }

            if (value instanceof Map<?, ?> mapValue) {
                  return mapValue.size();
            }

            if (value.getClass().isArray()) {
                  return Array.getLength(value);
            }

            return String.valueOf(value).length();
      }

      private Object defaultValue(
                  Object value,
                  String argumentExpression,
                  TemplateContext context) {
            if (argumentExpression.isBlank()) {
                  throw new IllegalArgumentException("default filter requires an argument.");
            }

            if (toBoolean(value)) {
                  return value;
            }

            return evaluateValue(argumentExpression, context);
      }

      private String currencyValue(
                  Object value,
                  String argumentExpression,
                  TemplateContext context) {
            if (value == null) {
                  return "";
            }

            final var symbol = argumentExpression.isBlank()
                        ? ""
                        : String.valueOf(evaluateValue(argumentExpression, context));

            final var formatter = new DecimalFormat("#,##0.00");

            return symbol + formatter.format(toBigDecimal(value));
      }

      private String numberValue(
                  Object value,
                  String argumentExpression,
                  TemplateContext context) {
            if (value == null) {
                  return "";
            }

            final var pattern = argumentExpression.isBlank()
                        ? "#,##0.##"
                        : String.valueOf(evaluateValue(argumentExpression, context));

            final var formatter = new DecimalFormat(pattern);

            return formatter.format(toBigDecimal(value));
      }

      private BigDecimal toBigDecimal(Object value) {
            if (value instanceof Number numberValue) {
                  return new BigDecimal(String.valueOf(numberValue));
            }

            if (value instanceof CharSequence textValue) {
                  return new BigDecimal(textValue.toString());
            }

            throw new IllegalArgumentException("Value is not numeric: " + value);
      }

      private String dateValue(
                  Object value,
                  String argumentExpression,
                  TemplateContext context) {
            return formatTemporalValue(
                        value,
                        argumentExpression,
                        context,
                        "yyyy-MM-dd");
      }

      private String timeValue(
                  Object value,
                  String argumentExpression,
                  TemplateContext context) {
            return formatTemporalValue(
                        value,
                        argumentExpression,
                        context,
                        "HH:mm:ss");
      }

      private String dateTimeValue(
                  Object value,
                  String argumentExpression,
                  TemplateContext context) {
            return formatTemporalValue(
                        value,
                        argumentExpression,
                        context,
                        "yyyy-MM-dd HH:mm:ss");
      }

      private String formatTemporalValue(
                  Object value,
                  String argumentExpression,
                  TemplateContext context,
                  String defaultPattern) {
            if (value == null) {
                  return "";
            }

            final var pattern = argumentExpression.isBlank()
                        ? defaultPattern
                        : String.valueOf(evaluateValue(argumentExpression, context));

            final var formatter = DateTimeFormatter.ofPattern(pattern);
            final var zoneId = ZoneId.systemDefault();

            if (value instanceof Instant instantValue) {
                  return formatter.withZone(zoneId).format(instantValue);
            }

            if (value instanceof Date dateValue) {
                  return formatter.withZone(zoneId).format(dateValue.toInstant());
            }

            if (value instanceof TemporalAccessor temporalValue) {
                  return formatter.format(temporalValue);
            }

            if (value instanceof CharSequence textValue) {
                  return formatTemporalString(
                              textValue.toString(),
                              formatter,
                              zoneId);
            }

            throw new IllegalArgumentException(
                        "Value is not a date/time value: " + value.getClass().getName());
      }

      private String formatTemporalString(
                  String value,
                  DateTimeFormatter formatter,
                  ZoneId zoneId) {
            final var text = value.trim();

            if (text.isBlank()) {
                  return "";
            }

            try {
                  return formatter.withZone(zoneId).format(Instant.parse(text));
            } catch (DateTimeParseException ignored) {
                  // Try next format.
            }

            try {
                  return formatter.format(LocalDateTime.parse(text));
            } catch (DateTimeParseException ignored) {
                  // Try next format.
            }

            try {
                  return formatter.format(LocalDate.parse(text));
            } catch (DateTimeParseException ignored) {
                  // Try next format.
            }

            try {
                  return formatter.format(LocalTime.parse(text));
            } catch (DateTimeParseException ignored) {
                  // Throw clean error below.
            }

            throw new IllegalArgumentException("Unsupported date/time text: " + value);
      }

      private record FilteredExpression(
                  String valueExpression,
                  List<String> filters) {
      }

      private record FilterCall(
                  String name,
                  String argumentExpression) {
      }

      private record TernaryExpression(
                  String condition,
                  String trueExpression,
                  String falseExpression) {
      }

      private record Comparison(
                  String left,
                  String operator,
                  String right) {
      }

      private int findTopLevelSlash(String expression) {
            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;

            for (int index = 0; index < expression.length(); index++) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == '(') {
                        parenthesisDepth++;
                        continue;
                  }

                  if (current == ')') {
                        parenthesisDepth--;
                        continue;
                  }

                  if (parenthesisDepth == 0 && current == '/') {
                        return index;
                  }
            }

            return -1;
      }

      private int findTopLevelChar(String expression, char targetChar) {
            boolean insideSingleQuote = false;
            boolean insideDoubleQuote = false;
            int parenthesisDepth = 0;

            for (int index = expression.length() - 1; index >= 0; index--) {
                  final var current = expression.charAt(index);

                  if (current == '\'' && !insideDoubleQuote) {
                        insideSingleQuote = !insideSingleQuote;
                        continue;
                  }

                  if (current == '"' && !insideSingleQuote) {
                        insideDoubleQuote = !insideDoubleQuote;
                        continue;
                  }

                  if (insideSingleQuote || insideDoubleQuote) {
                        continue;
                  }

                  if (current == ')') {
                        parenthesisDepth++;
                        continue;
                  }

                  if (current == '(') {
                        parenthesisDepth--;
                        continue;
                  }

                  if (parenthesisDepth == 0 && current == targetChar) {
                        return index;
                  }
            }

            return -1;
      }
}