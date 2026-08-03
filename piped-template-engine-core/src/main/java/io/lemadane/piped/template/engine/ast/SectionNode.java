package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public final class SectionNode implements ASTNode {
    final String sectionName;
    final ASTNode body;

    public SectionNode(String sectionName, ASTNode body) {
        this.sectionName = sectionName;
        this.body = body;
    }

    public String getSectionName() {
        return sectionName;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        StringWriter sw = new StringWriter();
        body.render(context, sw);
        if (context.getSections() != null) {
            context.getSections().put(sectionName, sw.toString());
        }
    }
}
