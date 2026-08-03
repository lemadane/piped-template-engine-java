package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class YieldNode implements ASTNode {
    final String sectionName;

    public YieldNode(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getSectionName() {
        return sectionName;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        if (context.getSections() == null) {
            throw new io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException("|yield| directive is only allowed inside layout templates.");
        }
        String content = context.getSections().get(sectionName);
        if (content != null) {
            writer.write(content);
        }
    }
}
