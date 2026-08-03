package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public final class ComponentNode implements ASTNode {
    final String componentPath;
    final String modelExpression;
    final ASTNode body;
    final ExpressionEvaluator evaluator;

    public ComponentNode(String componentPath, String modelExpression, ASTNode body, ExpressionEvaluator evaluator) {
        this.componentPath = componentPath;
        this.modelExpression = modelExpression;
        this.body = body;
        this.evaluator = evaluator;
    }

    public String getComponentPath() {
        return componentPath;
    }

    public String getModelExpression() {
        return modelExpression;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        TemplateContext compContext = context;
        if (modelExpression != null && !modelExpression.isEmpty()) {
            Object subModel = evaluator.evaluate(modelExpression, context);
            if (subModel instanceof Map<?, ?> mapModel) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringMap = (Map<String, Object>) mapModel;
                compContext = context.withModel(stringMap);
            } else if (subModel != null) {
                compContext = context.with("it", subModel);
            }
        }

        Map<String, String> slotMap = new HashMap<>();
        TemplateContext slotContext = compContext.withSlots(slotMap);

        StringWriter bodyWriter = new StringWriter();
        body.render(slotContext, bodyWriter);
        String defaultBody = bodyWriter.toString().trim();
        if (!defaultBody.isEmpty()) {
            slotMap.putIfAbsent("default", defaultBody);
            slotMap.putIfAbsent("", defaultBody);
        }

        if (context.getResolver() != null && context.getEngine() != null) {
            String compContent = context.getResolver().resolve(componentPath).getContent();
            String rendered = context.getEngine().renderComponentTemplate(compContent, slotContext);
            writer.write(rendered);
        } else {
            throw new io.lemadane.piped.template.engine.exceptions.TemplateRenderException("Unable to resolve component template: " + componentPath);
        }
    }
}
