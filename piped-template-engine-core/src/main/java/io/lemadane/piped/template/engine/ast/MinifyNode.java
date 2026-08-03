package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import io.lemadane.piped.template.engine.utils.HtmlFormatter;
import java.io.IOException;
import java.io.Writer;
import java.io.StringWriter;

public final class MinifyNode implements ASTNode {
    final ASTNode body;

    public MinifyNode(ASTNode body) {
        this.body = body;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        StringWriter sw = new StringWriter();
        body.render(context, sw);
        writer.write(HtmlFormatter.minifyHtml(sw.toString()));
    }
}
