package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class TextNode implements ASTNode {
    final char[] textChars;

    public TextNode(String text) {
        this.textChars = text.toCharArray();
    }

    public String getText() {
        return new String(textChars);
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        writer.write(textChars);
    }
}
