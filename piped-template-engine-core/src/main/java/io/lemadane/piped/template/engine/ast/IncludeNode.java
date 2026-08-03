package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import io.lemadane.piped.template.engine.res.TemplateSourceResolver;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public final class IncludeNode implements ASTNode {
    final String templatePath;
    final String modelExpression;
    final ExpressionEvaluator evaluator;

    public IncludeNode(String templatePath, String modelExpression, ExpressionEvaluator evaluator) {
        this.templatePath = templatePath;
        this.modelExpression = modelExpression;
        this.evaluator = evaluator;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public String getModelExpression() {
        return modelExpression;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        TemplateContext nextContext = context;
        if (modelExpression != null && !modelExpression.isEmpty()) {
            Object subModel = evaluator.evaluate(modelExpression, context);
            if (subModel instanceof Map<?, ?> mapModel) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringMap = (Map<String, Object>) mapModel;
                nextContext = new TemplateContext(stringMap);
            } else if (subModel != null) {
                nextContext = context.with("it", subModel);
            }
        }

        TemplateSourceResolver resolver = context.getResolver();
        if (resolver != null && context.getEngine() != null) {
            String rendered = context.getEngine().renderNamedTemplate(templatePath, nextContext);
            writer.write(rendered);
            return;
        }
        throw new io.lemadane.piped.template.engine.exceptions.TemplateRenderException("Unable to resolve include template: " + templatePath);
    }
}
