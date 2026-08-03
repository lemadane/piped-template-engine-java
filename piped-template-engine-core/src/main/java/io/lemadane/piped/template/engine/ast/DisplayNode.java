package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class DisplayNode implements ASTNode {
    final String propertyPath;
    final ExpressionEvaluator evaluator;

    public DisplayNode(String propertyPath, ExpressionEvaluator evaluator) {
        this.propertyPath = propertyPath;
        this.evaluator = evaluator;
    }

    public String getPropertyPath() {
        return propertyPath;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        Object val = evaluator.evaluate(propertyPath, context);
        if (val != null) {
            writer.write(val.toString());
        }
    }
}
