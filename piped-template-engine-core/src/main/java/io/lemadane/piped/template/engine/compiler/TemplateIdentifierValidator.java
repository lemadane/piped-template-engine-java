package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import java.util.regex.Pattern;

final class TemplateIdentifierValidator {

    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]*$");

    static void validateIdentifier(String directiveName, String name, int line, int column) {
        if (name == null || name.isBlank()) {
            throw new TemplateSyntaxException(String.format(
                    "Invalid |%s| name at line %d, column %d: name cannot be empty. Expected format: [A-Za-z_][A-Za-z0-9_-]*.",
                    directiveName, line, column));
        }

        String trimmed = name.trim();
        if (!IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new TemplateSyntaxException(String.format(
                    "Invalid |%s| name at line %d, column %d: '%s' is invalid. Expected format: [A-Za-z_][A-Za-z0-9_-]*.",
                    directiveName, line, column, trimmed));
        }
    }

    static void validateTemplatePath(String directiveName, String path, int line, int column) {
        if (path == null || path.isBlank()) {
            throw new TemplateSyntaxException(String.format(
                    "Invalid |%s| template path at line %d, column %d: path cannot be empty.",
                    directiveName, line, column));
        }

        String trimmed = path.trim();

        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            throw new TemplateSyntaxException(String.format(
                    "Invalid |%s| template path at line %d, column %d: '%s' must not be absolute.",
                    directiveName, line, column, trimmed));
        }

        if (trimmed.contains("..")) {
            throw new TemplateSyntaxException(String.format(
                    "Invalid |%s| template path at line %d, column %d: '%s' contains relative path traversal '..'.",
                    directiveName, line, column, trimmed));
        }

        if (trimmed.contains("//") || trimmed.contains("\\\\")) {
            throw new TemplateSyntaxException(String.format(
                    "Invalid |%s| template path at line %d, column %d: '%s' contains empty path segments.",
                    directiveName, line, column, trimmed));
        }

        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new TemplateSyntaxException(String.format(
                        "Invalid |%s| template path at line %d, column %d: contains invalid control character.",
                        directiveName, line, column));
            }
        }
    }
}
