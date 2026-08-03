package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import io.lemadane.piped.template.engine.options.PwaRenderOptions;
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
    final String mode;

    public PWANode(String name, String manifest, String theme, String icon, String sw, String statusColor, String registrationScript, String nonce, String mode) {
        this.name = name;
        this.manifest = manifest;
        this.theme = theme;
        this.icon = icon;
        this.sw = sw;
        this.statusColor = statusColor;
        this.registrationScript = registrationScript;
        this.nonce = nonce;
        this.mode = mode;
    }

    public PWANode(String name, String manifest, String theme, String icon, String sw, String statusColor, String registrationScript, String nonce) {
        this(name, manifest, theme, icon, sw, statusColor, registrationScript, nonce, null);
    }

    public PWANode(String name, String manifest, String theme, String icon, String sw, String statusColor) {
        this(name, manifest, theme, icon, sw, statusColor, null, null, null);
    }

    public String getName() { return name; }
    public String getManifest() { return manifest; }
    public String getTheme() { return theme; }
    public String getIcon() { return icon; }
    public String getSW() { return sw; }
    public String getStatusColor() { return statusColor; }
    public String getRegistrationScript() { return registrationScript; }
    public String getNonce() { return nonce; }
    public String getMode() { return mode; }

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
            PwaRenderOptions ctxPwa = context != null && context.getPwaRenderOptions() != null
                    ? context.getPwaRenderOptions()
                    : PwaRenderOptions.DEFAULT;

            PwaRenderOptions.RegistrationMode effectiveMode;
            if (mode != null && !mode.isEmpty()) {
                if ("inline".equalsIgnoreCase(mode)) {
                    effectiveMode = PwaRenderOptions.RegistrationMode.INLINE;
                } else if ("external".equalsIgnoreCase(mode)) {
                    effectiveMode = PwaRenderOptions.RegistrationMode.EXTERNAL;
                } else {
                    throw new TemplateSyntaxException("Invalid PWA registration mode: " + mode);
                }
            } else if (nonce != null && !nonce.isEmpty()) {
                effectiveMode = PwaRenderOptions.RegistrationMode.INLINE;
            } else if (registrationScript != null && !registrationScript.isEmpty()) {
                effectiveMode = PwaRenderOptions.RegistrationMode.EXTERNAL;
            } else {
                effectiveMode = ctxPwa.mode() != null ? ctxPwa.mode() : PwaRenderOptions.RegistrationMode.EXTERNAL;
            }

            String effectiveRegScript = (registrationScript != null && !registrationScript.isEmpty())
                    ? registrationScript
                    : (ctxPwa.registrationScript() != null ? ctxPwa.registrationScript() : "/pte-assets/pwa-register.js");

            boolean requireNonce = ctxPwa.requireNonceForInline();

            if (effectiveMode == PwaRenderOptions.RegistrationMode.EXTERNAL) {
                tags.add(String.format("<script src=\"%s\" data-pte-service-worker=\"%s\" defer></script>",
                        escapeHtml(effectiveRegScript), escapeHtml(sw)));
            } else {
                String nonceVal = null;
                if (nonce != null && !nonce.isEmpty()) {
                    Object eval = context != null ? context.get(nonce) : null;
                    nonceVal = eval != null ? String.valueOf(eval) : nonce;
                }

                if ((nonceVal == null || nonceVal.isEmpty()) && requireNonce) {
                    throw new TemplateSyntaxException("PWA inline registration requires a nonce when requireNonceForInline is enabled.");
                }

                String jsEscapedSw = JavaScriptStringEscaper.escapeJsString(sw);
                String nonceAttr = (nonceVal != null && !nonceVal.isEmpty())
                        ? String.format(" nonce=\"%s\"", escapeHtml(nonceVal))
                        : "";
                tags.add(String.format("<script%s>if('serviceWorker' in navigator){if(document.readyState==='complete'){navigator.serviceWorker.register('%s');}else{window.addEventListener('load',function(){navigator.serviceWorker.register('%s');});}}</script>",
                        nonceAttr, jsEscapedSw, jsEscapedSw));
            }
        }

        writer.write(String.join("\n", tags));
    }

    String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
