package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class MacroNode implements ASTNode {
    final String name;
    final List<String> parameters;
    final ASTNode body;

    public MacroNode(String name, List<String> parameters, ASTNode body) {
        this.name = name;
        this.parameters = List.copyOf(parameters);
        this.body = body;
    }

    public String getName() {
        return name;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        context.pushLocal("_macro_" + name, this);
    }
}
