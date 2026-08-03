package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.ast.ASTNode;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

public final class CompiledTemplate {
    final ASTNode rootNode;
    final Map<String, Object> metadata;

    public CompiledTemplate(ASTNode rootNode) {
        this(rootNode, Map.of());
    }

    public CompiledTemplate(ASTNode rootNode, Map<String, Object> metadata) {
        this.rootNode = rootNode;
        this.metadata = Map.copyOf(metadata);
    }

    public ASTNode getRootNode() {
        return rootNode;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void render(TemplateContext context, Writer writer) throws IOException {
        rootNode.render(context, writer);
    }

    public String renderToString(TemplateContext context) {
        StringWriter writer = new StringWriter();
        try {
            render(context, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
