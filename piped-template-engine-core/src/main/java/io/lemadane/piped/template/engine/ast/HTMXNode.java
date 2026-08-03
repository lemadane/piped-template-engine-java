package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public final class HTMXNode implements ASTNode {
    final String src;
    final List<String> extensions;
    final String config;
    final boolean indicator;

    public HTMXNode(String src, List<String> extensions, String config, boolean indicator) {
        this.src = src;
        this.extensions = extensions == null ? List.of() : extensions;
        this.config = config;
        this.indicator = indicator;
    }

    public String getSrc() { return src; }
    public List<String> getExtensions() { return extensions; }
    public String getConfig() { return config; }
    public boolean isIndicator() { return indicator; }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        String htmxSrc = src == null || src.isEmpty() ? "https://unpkg.com/htmx.org@1.9.10" : src;

        List<String> tags = new ArrayList<>();
        if (config != null && !config.isEmpty()) {
            tags.add(String.format("<meta name=\"htmx-config\" content=\"%s\">", escapeHtml(config)));
        }

        tags.add(String.format("<script src=\"%s\"></script>", escapeHtml(htmxSrc)));

        for (String ext : extensions) {
            String extName = ext.trim();
            if (!extName.isEmpty()) {
                String extUrl = String.format("https://unpkg.com/htmx.org@1.9.10/dist/ext/%s.js", escapeHtml(extName));
                tags.add(String.format("<script src=\"%s\"></script>", extUrl));
            }
        }

        if (indicator) {
            tags.add("<style>.htmx-indicator{display:none;}.htmx-request .htmx-indicator,.htmx-request.htmx-indicator{display:inline-block;}</style>");
        }

        writer.write(String.join("\n", tags));
    }

    String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
