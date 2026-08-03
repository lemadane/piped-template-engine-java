package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public final class AlpineNode implements ASTNode {
    final String src;
    final List<String> plugins;
    final boolean cloak;

    public AlpineNode(String src, List<String> plugins, boolean cloak) {
        this.src = src;
        this.plugins = plugins == null ? List.of() : plugins;
        this.cloak = cloak;
    }

    public String getSrc() { return src; }
    public List<String> getPlugins() { return plugins; }
    public boolean isCloak() { return cloak; }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        String alpineSrc = src == null || src.isEmpty() ? "https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js" : src;

        List<String> tags = new ArrayList<>();
        for (String plugin : plugins) {
            String pluginName = plugin.trim();
            if (!pluginName.isEmpty()) {
                String pluginUrl = String.format("https://cdn.jsdelivr.net/npm/@alpinejs/%s@3.x.x/dist/cdn.min.js", escapeHtml(pluginName));
                tags.add(String.format("<script defer src=\"%s\"></script>", pluginUrl));
            }
        }

        tags.add(String.format("<script defer src=\"%s\"></script>", escapeHtml(alpineSrc)));

        if (cloak) {
            tags.add("<style>[x-cloak]{display:none !important;}</style>");
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
