package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public final class HXAttrNode implements ASTNode {
    final String method;
    final String url;
    final String target;
    final String swap;
    final String indicator;
    final String trigger;

    public HXAttrNode(String method, String url, String target, String swap, String indicator, String trigger) {
        this.method = method;
        this.url = url;
        this.target = target;
        this.swap = swap;
        this.indicator = indicator;
        this.trigger = trigger;
    }

    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public String getTarget() { return target; }
    public String getSwap() { return swap; }
    public String getIndicator() { return indicator; }
    public String getTrigger() { return trigger; }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        List<String> attrs = new ArrayList<>();
        if (method != null && !method.isEmpty() && url != null && !url.isEmpty()) {
            attrs.add(String.format("hx-%s=\"%s\"", method, escapeHtml(url)));
        }
        if (target != null && !target.isEmpty()) {
            attrs.add(String.format("hx-target=\"%s\"", escapeHtml(target)));
        }
        if (swap != null && !swap.isEmpty()) {
            attrs.add(String.format("hx-swap=\"%s\"", escapeHtml(swap)));
        }
        if (indicator != null && !indicator.isEmpty()) {
            attrs.add(String.format("hx-indicator=\"%s\"", escapeHtml(indicator)));
        }
        if (trigger != null && !trigger.isEmpty()) {
            attrs.add(String.format("hx-trigger=\"%s\"", escapeHtml(trigger)));
        }

        writer.write(String.join(" ", attrs));
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
