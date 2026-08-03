package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.escapers.AttributeEscaper;
import io.lemadane.piped.template.engine.escapers.HtmlEscaper;
import io.lemadane.piped.template.engine.escapers.JsonEscaper;
import io.lemadane.piped.template.engine.escapers.UrlEscaper;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.OutputExpression;
import io.lemadane.piped.template.engine.expression.OutputMode;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import io.lemadane.piped.template.engine.parsers.OutputExpressionParser;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;
import java.util.Set;

public final class ExpressionNode implements ASTNode {
    static final HtmlEscaper htmlEscaper = new HtmlEscaper();
    static final AttributeEscaper attributeEscaper = new AttributeEscaper();
    static final JsonEscaper jsonEscaper = new JsonEscaper();
    static final UrlEscaper urlEscaper = new UrlEscaper();
    static final OutputExpressionParser outputExpressionParser = new OutputExpressionParser();

    static final Set<String> CONDITIONAL_ATTRIBUTE_LITERALS = Set.of(
            "allowfullscreen", "async", "autofocus", "autoplay", "checked",
            "controls", "default", "defer", "disabled", "formnovalidate",
            "hidden", "inert", "ismap", "itemscope", "loop", "multiple",
            "muted", "nomodule", "novalidate", "open", "playsinline",
            "readonly", "required", "reversed", "selected", "aria-current");

    final OutputExpression outputExpression;
    final ExpressionEvaluator evaluator;

    public ExpressionNode(OutputExpression outputExpression, ExpressionEvaluator evaluator) {
        this.outputExpression = outputExpression;
        this.evaluator = evaluator;
    }

    public OutputExpression getOutputExpression() {
        return outputExpression;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        var conditionalExpr = parseConditionalOutputExpression(outputExpression.expression());
        var parsedOutputExpr = outputExpressionParser.parse(conditionalExpr.outputSource());
        OutputMode mode = outputExpression.mode() != OutputMode.HTML_ESCAPED ? outputExpression.mode() : parsedOutputExpr.mode();

        if (conditionalExpr.conditionExpression() != null
                && !evaluator.evaluateBoolean(conditionalExpr.conditionExpression(), context)) {
            if (mode == OutputMode.ATTRIBUTE_ESCAPED) {
                removeTrailingAttributeWhitespace(writer);
            }
            return;
        }

        if (conditionalExpr.conditionExpression() != null && mode == OutputMode.ATTRIBUTE_ESCAPED) {
            String attrOutput = renderConditionalAttributeOutput(parsedOutputExpr.expression(), context);
            if (attrOutput != null) {
                ensureLeadingSpaceIfNeeded(writer);
                writer.write(attrOutput);
                return;
            }
        }

        Object value = evaluator.evaluate(parsedOutputExpr.expression(), context);
        String formatted = formatValue(mode, value);
        writer.write(formatted);
    }

    void removeTrailingAttributeWhitespace(Writer writer) {
        if (writer instanceof StringWriter sw) {
            StringBuffer sb = sw.getBuffer();
            while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                sb.deleteCharAt(sb.length() - 1);
            }
        }
    }

    void ensureLeadingSpaceIfNeeded(Writer writer) {
        if (writer instanceof StringWriter sw) {
            StringBuffer sb = sw.getBuffer();
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ' && sb.charAt(sb.length() - 1) != '<') {
                sb.append(' ');
            }
        }
    }

    ConditionalOutputExpression parseConditionalOutputExpression(String source) {
        final var ifIndex = findOutputIfIndex(source);
        if (ifIndex == -1) {
            return new ConditionalOutputExpression(source, null);
        }
        final var outputSource = source.substring(0, ifIndex).trim();
        final var conditionExpression = source.substring(ifIndex + "if".length()).trim();
        if (outputSource.isBlank()) {
            throw new TemplateSyntaxException("Conditional output expression must not be empty.");
        }
        if (conditionExpression.isBlank()) {
            throw new TemplateSyntaxException("Conditional output condition must not be empty.");
        }
        return new ConditionalOutputExpression(outputSource, conditionExpression);
    }

    int findOutputIfIndex(String source) {
        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        int parenthesisDepth = 0;

        for (int index = 0; index <= source.length() - "if".length(); index++) {
            final var character = source.charAt(index);
            if (character == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote;
                continue;
            }
            if (character == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote;
                continue;
            }
            if (insideSingleQuote || insideDoubleQuote) {
                continue;
            }
            if (character == '(') {
                parenthesisDepth++;
                continue;
            }
            if (character == ')') {
                if (parenthesisDepth > 0) {
                    parenthesisDepth--;
                }
                continue;
            }
            if (parenthesisDepth > 0) {
                continue;
            }
            if (!source.startsWith("if", index)) {
                continue;
            }
            final var beforeIndex = index - 1;
            final var beforeIsBoundary = beforeIndex < 0 || Character.isWhitespace(source.charAt(beforeIndex));
            final var afterIndex = index + "if".length();
            final var afterIsBoundary = afterIndex >= source.length() || Character.isWhitespace(source.charAt(afterIndex));

            if (beforeIsBoundary && afterIsBoundary) {
                return index;
            }
        }
        return -1;
    }

    String renderConditionalAttributeOutput(String expression, TemplateContext context) {
        final var trimmedExpression = expression.trim();
        if (isConditionalAttributeLiteral(trimmedExpression)) {
            return attributeEscaper.escape(trimmedExpression);
        }
        final var equalsIndex = findTopLevelEqualsIndex(trimmedExpression);
        if (equalsIndex == -1) {
            return null;
        }
        final var attributeName = trimmedExpression.substring(0, equalsIndex).trim();
        final var valueExpression = trimmedExpression.substring(equalsIndex + 1).trim();
        if (!isValidAttributeName(attributeName)) {
            return null;
        }
        if (valueExpression.isBlank()) {
            throw new TemplateSyntaxException("Conditional attribute value must not be empty.");
        }
        final var value = evaluator.evaluate(valueExpression, context);
        return attributeName + "=\"" + attributeEscaper.escape(value) + "\"";
    }

    boolean isConditionalAttributeLiteral(String expression) {
        final var attributeName = stripQuotes(expression.trim());
        if (attributeName.isBlank()) return false;
        if (!attributeName.matches("[A-Za-z_:][A-Za-z0-9_:.\\-]*")) return false;
        return CONDITIONAL_ATTRIBUTE_LITERALS.contains(attributeName.toLowerCase(Locale.ROOT));
    }

    String stripQuotes(String str) {
        if ((str.startsWith("'") && str.endsWith("'")) || (str.startsWith("\"") && str.endsWith("\""))) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    boolean isValidAttributeName(String name) {
        return name.matches("[A-Za-z_:][A-Za-z0-9_:.\\-]*");
    }

    int findTopLevelEqualsIndex(String expression) {
        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        int parenthesisDepth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote;
            } else if (c == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote;
            } else if (!insideSingleQuote && !insideDoubleQuote) {
                if (c == '(') parenthesisDepth++;
                else if (c == ')') { if (parenthesisDepth > 0) parenthesisDepth--; }
                else if (c == '=' && parenthesisDepth == 0) return i;
            }
        }
        return -1;
    }

    record ConditionalOutputExpression(String outputSource, String conditionExpression) {}

    String formatValue(OutputMode mode, Object value) {
        return switch (mode) {
            case HTML_ESCAPED -> htmlEscaper.escape(value);
            case TRUSTED_HTML -> value == null ? "" : String.valueOf(value);
            case ATTRIBUTE_ESCAPED -> attributeEscaper.escape(value);
            case JSON_ENCODED -> jsonEscaper.escape(value);
            case URL_ENCODED -> urlEscaper.escape(value);
        };
    }
}
