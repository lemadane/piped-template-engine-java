package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.exceptions.LoopBreakException;
import io.lemadane.piped.template.engine.exceptions.LoopContinueException;
import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public final class ForNode implements ASTNode {
    final String varName;
    final String startExpression;
    final String endExpression;
    final String stepExpression;
    final ASTNode bodyBlock;
    final ASTNode elseBlock;
    final ExpressionEvaluator evaluator;

    public ForNode(
            String varName,
            String startExpression,
            String endExpression,
            String stepExpression,
            ASTNode bodyBlock,
            ASTNode elseBlock,
            ExpressionEvaluator evaluator) {
        this.varName = varName;
        this.startExpression = startExpression;
        this.endExpression = endExpression;
        this.stepExpression = stepExpression;
        this.bodyBlock = bodyBlock;
        this.elseBlock = elseBlock;
        this.evaluator = evaluator;
    }

    public String getVarName() {
        return varName;
    }

    public String getStartExpression() {
        return startExpression;
    }

    public String getEndExpression() {
        return endExpression;
    }

    public String getStepExpression() {
        return stepExpression;
    }

    public ASTNode getBodyBlock() {
        return bodyBlock;
    }

    public ASTNode getElseBlock() {
        return elseBlock;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        Object rawStart = evaluator.evaluate(startExpression, context);
        int start = toInt(rawStart, startExpression);

        Object rawEnd = evaluator.evaluate(endExpression, context);
        int end = toInt(rawEnd, endExpression);

        int step = 1;
        if (stepExpression != null && !stepExpression.isEmpty()) {
            Object rawStep = evaluator.evaluate(stepExpression, context);
            step = toInt(rawStep, stepExpression);
        }

        if (step <= 0) {
            throw new TemplateRenderException("Step must be a positive integer, got: " + step);
        }

        boolean executedAtLeastOnce = false;

        if (start < end) {
            for (int current = start; current <= end; current += step) {
                executedAtLeastOnce = true;
                Map<String, Object> scope = new HashMap<>();
                scope.put(varName, current);
                TemplateContext subContext = context.subContext(scope);
                try {
                    bodyBlock.render(subContext, writer);
                } catch (LoopContinueException e) {
                    // Continue to next iteration
                } catch (LoopBreakException e) {
                    break;
                }
            }
        } else if (start > end) {
            for (int current = start; current >= end; current -= step) {
                executedAtLeastOnce = true;
                Map<String, Object> scope = new HashMap<>();
                scope.put(varName, current);
                TemplateContext subContext = context.subContext(scope);
                try {
                    bodyBlock.render(subContext, writer);
                } catch (LoopContinueException e) {
                    // Continue to next iteration
                } catch (LoopBreakException e) {
                    break;
                }
            }
        } else {
            // start == end
            executedAtLeastOnce = true;
            Map<String, Object> scope = new HashMap<>();
            scope.put(varName, start);
            TemplateContext subContext = context.subContext(scope);
            try {
                bodyBlock.render(subContext, writer);
            } catch (LoopContinueException e) {
                // Continue
            } catch (LoopBreakException e) {
                // Break
            }
        }

        if (!executedAtLeastOnce && elseBlock != null) {
            elseBlock.render(context, writer);
        }
    }

    int toInt(Object val, String expr) {
        if (val == null) {
            throw new TemplateRenderException("Expression '" + expr + "' evaluated to null");
        }
        if (val instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            throw new TemplateRenderException("Invalid integer value for expression '" + expr + "': " + val);
        }
    }
}
