package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.exceptions.LoopContinueException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class ContinueNode implements ASTNode {
    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        throw new LoopContinueException();
    }
}
