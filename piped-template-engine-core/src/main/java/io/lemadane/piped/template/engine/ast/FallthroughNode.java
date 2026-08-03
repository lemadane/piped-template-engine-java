package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class FallthroughNode implements ASTNode {
    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        // Fallthrough indicator - no direct output during rendering
    }
}
