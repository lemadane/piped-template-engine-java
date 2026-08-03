package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import io.lemadane.piped.template.engine.utils.JavaScriptStringEscaper;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public final class PWANode implements ASTNode {
    final String name;
    final String manifest;
    final String theme;
    final String icon;
    final String sw;
    final String statusColor;
    final String registrationScript;
    final String nonce;

    public PWANode(String name, String manifest, String theme, String icon, String sw, String statusColor, String registrationScript, String nonce) {
        this.name = name;
        this.manifest = manifest;
        this.theme = theme;
        this.icon = icon;
        this.sw = sw;
        this.statusColor = statusColor;
        this.registrationScript = registrationScript;
        this.nonce = nonce;
    }

    public PWANode(String name, String manifest, String theme, String icon, String sw, String statusColor) {
        this(name, manifest, theme, icon, sw, statusColor, null, null);
    }

    public String getName() { return name; }
    public String getManifest() { return manifest; }
    public String getTheme() { return theme; }
    public String getIcon() { return icon; }
    public String getSW() { return sw; }
    public String getStatusColor() { return statusColor; }
    public String getRegistrationScript() { return registrationScript; }
    public String getNonce() { return nonce; }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        String m = manifest == null || manifest.isEmpty() ? "/manifest.json" : manifest;
        String t = theme == null || theme.isEmpty() ? "#000000" : theme;
        String sColor = statusColor == null || statusColor.isEmpty() ? "default" : statusColor;

        List<String> tags = new ArrayList<>();
        tags.add("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">");
        tags.add(String.format("<meta name=\"theme-color\" content=\"%s\">", escapeHtml(t)));
        tags.add("<meta name=\"mobile-web-app-capable\" content=\"yes\">");
        tags.add("<meta name=\"apple-mobile-web-app-capable\" content=\"yes\">");
        tags.add(String.format("<meta name=\"apple-mobile-web-app-status-bar-style\" content=\"%s\">", escapeHtml(sColor)));

        if (name != null && !name.isEmpty()) {
            tags.add(String.format("<meta name=\"apple-mobile-web-app-title\" content=\"%s\">", escapeHtml(name)));
            tags.add(String.format("<meta name=\"application-name\" content=\"%s\">", escapeHtml(name)));
        }

        tags.add(String.format("<link rel=\"manifest\" href=\"%s\">", escapeHtml(m)));

        if (icon != null && !icon.isEmpty()) {
            tags.add(String.format("<link rel=\"apple-touch-icon\" href=\"%s\">", escapeHtml(icon)));
            tags.add(String.format("<link rel=\"icon\" href=\"%s\">", escapeHtml(icon)));
        }

        if (sw != null && !sw.isEmpty()) {
            if (registrationScript != null && !registrationScript.isEmpty()) {
                tags.add(String.format("<script src=\"%s\" data-pte-service-worker=\"%s\" defer></script>",
                        escapeHtml(registrationScript), escapeHtml(sw)));
            } else {
                String jsEscapedSw = JavaScriptStringEscaper.escapeJsString(sw);
                String nonceAttr = "";
                if (nonce != null && !nonce.isEmpty()) {
                    Object evaluatedNonce = context != null && context.get(nonce) != null ? context.get(nonce) : nonce;
                    nonceAttr = String.format(" nonce=\"%s\"", escapeHtml(String.valueOf(evaluatedNonce)));
                }
                tags.add(String.format("<script%s>if('serviceWorker' in navigator){if(document.readyState==='complete'){navigator.serviceWorker.register('%s');}else{window.addEventListener('load',function(){navigator.serviceWorker.register('%s');});}}</script>",
                        nonceAttr, jsEscapedSw, jsEscapedSw));
            }
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
