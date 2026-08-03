package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public final class FieldNode implements ASTNode {
    final String propertyPath;
    final ExpressionEvaluator evaluator;

    public FieldNode(String propertyPath, ExpressionEvaluator evaluator) {
        this.propertyPath = propertyPath;
        this.evaluator = evaluator;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        String name = deriveName(propertyPath);
        Object rawVal = evaluator.evaluate(propertyPath, context);
        String valStr = rawVal == null ? "" : String.valueOf(rawVal);

        StringBuilder output = new StringBuilder();
        output.append("name=\"").append(name).append("\" ")
              .append("id=\"").append(name).append("\" ")
              .append("value=\"").append(valStr).append("\"");

        Object errorsObj = context.get("errors");
        if (errorsObj instanceof Map<?, ?> errorsMap && errorsMap.containsKey(name)) {
            output.append(" class=\"input is-danger\"");
        }

        writer.write(output.toString());
    }

    String deriveName(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot == -1 ? path : path.substring(lastDot + 1);
    }
}
