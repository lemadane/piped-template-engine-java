package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallMacroNode implements ASTNode {
    static final int MAX_RECURSION_DEPTH = 100;
    final String macroName;
    final List<String> argumentExpressions;
    final ExpressionEvaluator evaluator;

    public CallMacroNode(String macroName, List<String> argumentExpressions, ExpressionEvaluator evaluator) {
        this.macroName = macroName;
        this.argumentExpressions = List.copyOf(argumentExpressions);
        this.evaluator = evaluator;
    }

    public String getMacroName() {
        return macroName;
    }

    public List<String> getArgumentExpressions() {
        return argumentExpressions;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        Object macroObj = context.get("_macro_" + macroName);
        if (!(macroObj instanceof MacroNode macroNode)) {
            throw new TemplateRenderException("Undefined macro '" + macroName + "'.");
        }

        // Recursion tracking
        String depthKey = "_macro_depth_" + macroName;
        Object depthObj = context.get(depthKey);
        int currentDepth = depthObj instanceof Integer d ? d : 0;
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            throw new TemplateRenderException("Maximum recursion depth exceeded for macro '" + macroName + "'.");
        }

        Map<String, Object> macroScope = new HashMap<>();
        List<String> params = macroNode.getParameters();

        for (int i = 0; i < params.size(); i++) {
            String paramName = params.get(i);
            Object argVal = null;
            if (i < argumentExpressions.size()) {
                argVal = evaluator.evaluate(argumentExpressions.get(i), context);
            }
            macroScope.put(paramName, argVal == null ? "" : argVal);
        }

        macroScope.put(depthKey, currentDepth + 1);
        TemplateContext subContext = context.subContext(macroScope);
        macroNode.getBody().render(subContext, writer);
    }
}
