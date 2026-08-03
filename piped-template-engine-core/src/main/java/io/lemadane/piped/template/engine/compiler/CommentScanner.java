package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;

public final class CommentScanner {

    public record CommentSpan(int startIndex, int endIndex, boolean multiline) {}

    public static CommentSpan scan(String template, int start) {
        if (template == null || start < 0 || !template.startsWith("|#", start)) {
            throw new IllegalArgumentException("Not a comment start at index " + start);
        }

        int contentStart = start + 2;
        boolean multiline = false;

        for (int index = contentStart; index < template.length(); index++) {
            char current = template.charAt(index);

            if (multiline) {
                if (current == '#' && index + 1 < template.length() && template.charAt(index + 1) == '|') {
                    return new CommentSpan(start, index + 2, true);
                }
                continue;
            }

            if (current == '#' && index + 1 < template.length() && template.charAt(index + 1) == '|') {
                return new CommentSpan(start, index + 2, true);
            }

            if (current == '\n' || current == '\r') {
                multiline = true;
                continue;
            }

            if (current == '|') {
                return new CommentSpan(start, index + 1, false);
            }
        }

        int line = 1;
        int col = 1;
        for (int i = 0; i < start && i < template.length(); i++) {
            if (template.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        throw new TemplateSyntaxException("Unclosed comment at line " + line + ", column " + col + ".");
    }
}
