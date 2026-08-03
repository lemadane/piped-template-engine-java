package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class EditorNode implements ASTNode {
    final String propertyPath;
    final ExpressionEvaluator evaluator;

    public EditorNode(String propertyPath, ExpressionEvaluator evaluator) {
        this.propertyPath = propertyPath;
        this.evaluator = evaluator;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        int lastDot = propertyPath.lastIndexOf('.');
        String name = lastDot == -1 ? propertyPath : propertyPath.substring(lastDot + 1);
        Object rawVal = evaluator.evaluate(propertyPath, context);
        String valStr = rawVal == null ? "" : String.valueOf(rawVal);

        String inputHtml = "<input type=\"text\" name=\"" + name + "\" id=\"" + name + "\" value=\"" + valStr + "\" class=\"input\">";
        writer.write(inputHtml);
    }
}
