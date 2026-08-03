package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public final class LayoutNode implements ASTNode {
    final String layoutPath;
    final ASTNode body;

    public LayoutNode(String layoutPath, ASTNode body) {
        this.layoutPath = layoutPath;
        this.body = body;
    }

    public String getLayoutPath() {
        return layoutPath;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        Map<String, String> sectionMap = new HashMap<>();
        TemplateContext childContext = context.withSections(sectionMap);

        // Render body to capture sections
        StringWriter bodyWriter = new StringWriter();
        body.render(childContext, bodyWriter);
        String defaultBody = bodyWriter.toString().trim();
        if (!defaultBody.isEmpty()) {
            sectionMap.putIfAbsent("content", defaultBody);
            sectionMap.putIfAbsent("", defaultBody);
        }

        if (context.getResolver() != null && context.getEngine() != null) {
            String layoutContent = context.getResolver().resolve(layoutPath).getContent();
            String rendered = context.getEngine().renderStringWithContext(layoutContent, childContext);
            writer.write(rendered);
        } else {
            throw new io.lemadane.piped.template.engine.exceptions.TemplateRenderException("Unable to resolve layout template: " + layoutPath);
        }
    }
}
