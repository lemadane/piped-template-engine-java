package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class BlockNode implements ASTNode {
    final List<ASTNode> children;

    public BlockNode(List<ASTNode> children) {
        this.children = List.copyOf(children);
    }

    public List<ASTNode> getChildren() {
        return children;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).render(context, writer);
        }
    }
}
