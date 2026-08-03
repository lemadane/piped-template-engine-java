package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public final class JsNode implements ASTNode {
    final String expression;
    final ASTNode body;
    final ExpressionEvaluator evaluator;

    public JsNode(String expression, ExpressionEvaluator evaluator) {
        this.expression = expression;
        this.body = null;
        this.evaluator = evaluator;
    }

    public JsNode(ASTNode body) {
        this.expression = null;
        this.body = body;
        this.evaluator = null;
    }

    public JsNode(String expression, ASTNode body, ExpressionEvaluator evaluator) {
        this.expression = expression;
        this.body = body;
        this.evaluator = evaluator;
    }

    public String getExpression() {
        return expression;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        if (body != null) {
            StringWriter sw = new StringWriter();
            body.render(context, sw);
            writer.write("<script>");
            writer.write(sw.toString());
            writer.write("</script>");
        } else if (expression != null) {
            String exprStr = expression.trim();
            ConditionalExpression condExpr = parseConditionalExpression(exprStr);
            if (condExpr.condition() != null && evaluator != null
                    && !evaluator.evaluateBoolean(condExpr.condition(), context)) {
                return;
            }
            String content = condExpr.outputExpr();
            String evaluatedStr = null;
            if (evaluator != null) {
                Object val = evaluator.evaluate(content, context);
                if (val != null) {
                    evaluatedStr = String.valueOf(val);
                }
            }
            if (evaluatedStr == null) {
                evaluatedStr = content;
            }
            writer.write("<script>");
            writer.write(evaluatedStr);
            writer.write("</script>");
        }
    }

    ConditionalExpression parseConditionalExpression(String source) {
        int ifIndex = findOutputIfIndex(source);
        if (ifIndex == -1) {
            return new ConditionalExpression(source, null);
        }
        String outputSource = source.substring(0, ifIndex).trim();
        String conditionExpression = source.substring(ifIndex + "if".length()).trim();
        if (outputSource.isBlank()) {
            throw new TemplateSyntaxException("Conditional JS expression must not be empty.");
        }
        if (conditionExpression.isBlank()) {
            throw new TemplateSyntaxException("Conditional JS condition must not be empty.");
        }
        return new ConditionalExpression(outputSource, conditionExpression);
    }

    int findOutputIfIndex(String source) {
        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        int parenthesisDepth = 0;

        for (int index = 0; index <= source.length() - "if".length(); index++) {
            char character = source.charAt(index);
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
            int beforeIndex = index - 1;
            boolean beforeIsBoundary = beforeIndex < 0 || Character.isWhitespace(source.charAt(beforeIndex));
            int afterIndex = index + "if".length();
            boolean afterIsBoundary = afterIndex >= source.length() || Character.isWhitespace(source.charAt(afterIndex));

            if (beforeIsBoundary && afterIsBoundary) {
                return index;
            }
        }
        return -1;
    }

    record ConditionalExpression(String outputExpr, String condition) {}
}
