package io.lemadane.piped.template.engine.utils;

public final class JavaScriptStringEscaper {

    public static String escapeJsString(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '\'' -> sb.append("\\'");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\u2028' -> sb.append("\\u2028");
                case '\u2029' -> sb.append("\\u2029");
                case '<' -> sb.append("\\u003C");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }
}
