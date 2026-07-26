package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class AlpineAttrNode implements ASTNode {
    private final String directive;
    private final String value;

    public AlpineAttrNode(String directive, String value) {
        this.directive = directive;
        this.value = value;
    }

    public String getDirective() { return directive; }
    public String getValue() { return value; }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        String dir = directive;
        if (dir.startsWith("alpine-")) {
            dir = dir.substring("alpine-".length());
        }

        if (value == null || value.isEmpty()) {
            writer.write(String.format("x-%s", dir));
        } else {
            writer.write(String.format("x-%s=\"%s\"", dir, escapeHtml(value)));
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
