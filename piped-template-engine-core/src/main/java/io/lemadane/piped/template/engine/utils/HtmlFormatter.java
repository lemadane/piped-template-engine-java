package io.lemadane.piped.template.engine.utils;

public final class HtmlFormatter {

    public static String minifyHtml(String html) {
        if (html == null) {
            return "";
        }
        String result = html.replaceAll("<!--[\\s\\S]*?-->", "");
        return result.replaceAll("\\s+", " ")
                     .replaceAll(">\\s+<", "><")
                     .trim();
    }

    public static String prettifyHtml(String html) {
        if (html == null) {
            return "";
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<[^>]+>|[^<]+");
        java.util.regex.Matcher m = p.matcher(html);
        java.util.List<String> tokens = new java.util.ArrayList<>();
        while (m.find()) {
            String token = m.group().trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        int indent = 0;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("</")) {
                indent = Math.max(0, indent - 1);
                if (!lines.isEmpty()) {
                    String lastLine = lines.get(lines.size() - 1);
                    String tagName = token.substring(2, token.length() - 1).trim();
                    if (lastLine.contains("<" + tagName) && !lastLine.contains("</" + tagName + ">")) {
                        lines.set(lines.size() - 1, lastLine + token);
                        continue;
                    }
                }
                lines.add("  ".repeat(indent) + token);
            } else if (token.startsWith("<") && !token.startsWith("<!") && !token.endsWith("/>") && !token.startsWith("<?")) {
                lines.add("  ".repeat(indent) + token);
                indent++;
            } else {
                if (!lines.isEmpty() && lines.get(lines.size() - 1).trim().startsWith("<") && !lines.get(lines.size() - 1).trim().startsWith("</")) {
                    lines.set(lines.size() - 1, lines.get(lines.size() - 1) + token);
                } else {
                    lines.add("  ".repeat(indent) + token);
                }
            }
        }
        return String.join("\n", lines).trim();
    }
}
