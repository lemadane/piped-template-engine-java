package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class ModelNode implements ASTNode {
    final String modelClassName;

    public ModelNode(String modelClassName) {
        this.modelClassName = modelClassName;
    }

    public String getModelClassName() {
        return modelClassName;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        // Model directive is a type contract declaration and outputs no text directly
    }
}
