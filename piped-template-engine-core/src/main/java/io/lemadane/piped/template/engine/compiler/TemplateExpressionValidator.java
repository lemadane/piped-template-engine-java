package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import java.util.Set;

final class TemplateExpressionValidator {

    static final Set<String> KNOWN_DIRECTIVES = Set.of(
            "if", "else", "else if", "/if",
            "each", "/each", "for", "/for",
            "switch", "/switch", "case", "default", "fallthrough",
            "include", "layout", "section", "/section", "yield",
            "component", "/component", "slot", "/slot",
            "macro", "/macro", "call", "fragment", "/fragment",
            "minify", "/minify", "page", "attempt", "/attempt", "recover",
            "pwa", "htmx", "htmx-get", "htmx-post", "htmx-put", "htmx-delete", "htmx-patch",
            "alpine", "alpine-data", "continue", "break", "separator", "/separator",
            "model", "field", "display", "editor"
    );

    static final Set<String> NO_ARG_DIRECTIVES = Set.of(
            "else", "fallthrough", "default", "continue", "break"
    );

    static void validatePipeContent(String content, int line, int column) {
        if (content == null || content.isBlank()) {
            throw new TemplateSyntaxException(String.format("Empty pipe directive at line %d, column %d.", line, column));
        }

        String trimmed = content.trim();
        String firstWord = getFirstWord(trimmed);

        if ("else-if".equals(firstWord) || "elseif".equals(firstWord) || trimmed.startsWith("else-if ") || trimmed.startsWith("elseif ")) {
            throw new TemplateSyntaxException(String.format(
                    "Unknown directive '|%s|' at line %d, column %d. Did you mean '|else if|'?",
                    trimmed, line, column));
        }

        if (firstWord.startsWith("/") && !KNOWN_DIRECTIVES.contains(firstWord)) {
            String suggestion = findClosestDirective(firstWord);
            if (suggestion != null) {
                throw new TemplateSyntaxException(String.format(
                        "Unknown directive '|%s|' at line %d, column %d. Did you mean '|%s|'?",
                        trimmed, line, column, suggestion));
            } else {
                throw new TemplateSyntaxException(String.format(
                        "Unknown directive '|%s|' at line %d, column %d.",
                        trimmed, line, column));
            }
        }

        if (isDirectiveTypoCandidate(firstWord, trimmed) && !isKnownDirectivePrefix(trimmed)) {
            String suggestion = findClosestDirective(firstWord);
            if (suggestion != null) {
                throw new TemplateSyntaxException(String.format(
                        "Unknown directive '|%s|' at line %d, column %d. Did you mean '|%s|'?",
                        trimmed, line, column, suggestion));
            }
        }

        if (!isValidExpressionOrDirective(trimmed)) {
            String suggestion = findClosestDirective(firstWord);
            if (suggestion != null) {
                throw new TemplateSyntaxException(String.format(
                        "Unknown directive '|%s|' at line %d, column %d. Did you mean '|%s|'?",
                        trimmed, line, column, suggestion));
            }
            throw new TemplateSyntaxException(String.format(
                    "Invalid syntax or unknown directive '|%s|' at line %d, column %d.",
                    trimmed, line, column));
        }
    }

    static boolean isKnownDirectivePrefix(String trimmed) {
        for (String dir : KNOWN_DIRECTIVES) {
            if (trimmed.equals(dir) || trimmed.startsWith(dir + " ")) {
                return true;
            }
        }
        return false;
    }

    static boolean isDirectiveTypoCandidate(String word, String fullContent) {
        if (word == null || word.length() <= 1) {
            return false;
        }

        if (Character.isDigit(word.charAt(0)) || Set.of("html", "attr", "json", "url").contains(word.toLowerCase())) {
            return false;
        }

        if (word.contains(".") || word.contains("?") || word.contains(":") || word.contains("*") || word.contains("+") || word.contains("-") || word.contains("/")) {
            return false;
        }

        boolean hasMultipleTokens = fullContent.contains(" ");
        boolean isClosingTagCandidate = word.startsWith("/");

        if (!hasMultipleTokens && !isClosingTagCandidate) {
            for (String dir : NO_ARG_DIRECTIVES) {
                int dist = computeLevenshteinDistance(word.toLowerCase(), dir);
                if (dist > 0 && dist <= 2) {
                    return true;
                }
            }
            return false;
        }

        String[] tokens = fullContent.split("\\s+");
        if (tokens.length >= 2 && isOperatorToken(tokens[1])) {
            return false;
        }

        for (String dir : KNOWN_DIRECTIVES) {
            int dist = computeLevenshteinDistance(word.toLowerCase(), dir.toLowerCase());
            if (dist > 0 && dist <= 2) {
                return true;
            }
        }
        return false;
    }

    static String findClosestDirective(String word) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;
        String cleanWord = word.toLowerCase();

        for (String dir : KNOWN_DIRECTIVES) {
            int dist = computeLevenshteinDistance(cleanWord, dir);
            if (dist < minDistance && dist <= 2) {
                minDistance = dist;
                bestMatch = dir;
            }
        }
        return bestMatch;
    }

    static String getFirstWord(String text) {
        int spaceIndex = text.indexOf(' ');
        return spaceIndex == -1 ? text : text.substring(0, spaceIndex);
    }

    static boolean isValidExpressionOrDirective(String text) {
        if (isKnownDirectivePrefix(text)) {
            return true;
        }

        String firstWord = getFirstWord(text);
        if (firstWord.equals("html") || firstWord.equals("attr") || firstWord.equals("json") || firstWord.equals("url")) {
            String rest = text.substring(firstWord.length()).trim();
            return !rest.isEmpty() && isValidExpressionSyntax(rest);
        }

        return isValidExpressionSyntax(text);
    }

    static boolean isValidExpressionSyntax(String text) {
        int len = text.length();
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);

            if (ch == '\'' && !inDoubleQuote) {
                if (i == 0 || text.charAt(i - 1) != '\\') {
                    inSingleQuote = !inSingleQuote;
                }
            } else if (ch == '"' && !inSingleQuote) {
                if (i == 0 || text.charAt(i - 1) != '\\') {
                    inDoubleQuote = !inDoubleQuote;
                }
            } else if (!inSingleQuote && !inDoubleQuote) {
                switch (ch) {
                    case '(' -> parenDepth++;
                    case ')' -> parenDepth--;
                    case '[' -> bracketDepth++;
                    case ']' -> bracketDepth--;
                    case '{' -> braceDepth++;
                    case '}' -> braceDepth--;
                }
                if (parenDepth < 0 || bracketDepth < 0 || braceDepth < 0) {
                    return false;
                }
            }
        }

        if (inSingleQuote || inDoubleQuote || parenDepth != 0 || bracketDepth != 0 || braceDepth != 0) {
            return false;
        }

        String[] tokens = text.split("\\s+");
        if (tokens.length >= 2) {
            boolean previousWasIdentifier = isIdentifierToken(tokens[0]);
            boolean previousEndedWithComma = tokens[0].endsWith(",");

            for (int i = 1; i < tokens.length; i++) {
                boolean currentIsIdentifier = isIdentifierToken(tokens[i]);
                if (previousWasIdentifier && currentIsIdentifier && !previousEndedWithComma && !isOperatorToken(tokens[i - 1]) && !isOperatorToken(tokens[i])) {
                    return false;
                }
                previousWasIdentifier = currentIsIdentifier;
                previousEndedWithComma = tokens[i].endsWith(",");
            }
        }

        return true;
    }

    static boolean isIdentifierToken(String token) {
        String clean = token.endsWith(",") ? token.substring(0, token.length() - 1) : token;
        return clean.matches("^[A-Za-z_][A-Za-z0-9_.]*$");
    }

    static boolean isOperatorToken(String token) {
        String clean = token.endsWith(",") ? token.substring(0, token.length() - 1) : token;
        return Set.of(
                "+", "-", "*", "/", "%", "==", "!=", ">", "<", ">=", "<=", "??", "?", ":",
                "and", "or", "not", "nor", "nand", "if", "in", "is",
                "default", "lower", "upper", "trim", "slug", "capitalize", "date", "time", "datetime", "number", "currency", "length"
        ).contains(clean);
    }

    static int computeLevenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }
}
