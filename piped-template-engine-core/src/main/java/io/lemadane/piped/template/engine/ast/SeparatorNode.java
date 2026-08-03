package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class SeparatorNode implements ASTNode {
    final ASTNode body;

    public SeparatorNode(ASTNode body) {
        this.body = body;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        body.render(context, writer);
    }
}
