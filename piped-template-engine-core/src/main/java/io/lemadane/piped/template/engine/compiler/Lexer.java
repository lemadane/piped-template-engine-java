package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import java.util.ArrayList;
import java.util.List;

public final class Lexer {

    public List<Token> tokenize(String template) {
        List<Token> tokens = new ArrayList<>();
        if (template == null || template.isEmpty()) {
            return tokens;
        }

        int length = template.length();
        int cursor = 0;

        while (cursor < length) {
            int pipeIndex = template.indexOf('|', cursor);

            if (pipeIndex == -1) {
                tokens.add(createToken(template, TokenType.TEXT, template.substring(cursor), cursor));
                break;
            }

            if (pipeIndex > cursor) {
                tokens.add(createToken(template, TokenType.TEXT, template.substring(cursor, pipeIndex), cursor));
            }

            if (template.startsWith("|--", pipeIndex)) {
                int[] location = calculateLineAndColumn(template, pipeIndex);
                throw new TemplateSyntaxException(
                        "Removed comment syntax '|-- ... --|' at line "
                                + location[0]
                                + ", column "
                                + location[1]
                                + ". Use '|# comment|' or '|# ... #|'.");
            }

            // Check for comment |# ... | or |# ... #|
            if (template.startsWith("|#", pipeIndex)) {
                CommentScanner.CommentSpan span = CommentScanner.scan(template, pipeIndex);
                int contentEnd = span.multiline() ? span.endIndex() - 2 : span.endIndex() - 1;
                tokens.add(createToken(template, TokenType.COMMENT, template.substring(pipeIndex + 2, contentEnd), pipeIndex));
                cursor = span.endIndex();
                continue;
            }

            // Standard expression or directive pipe
            int closingPipe = template.indexOf('|', pipeIndex + 1);
            if (closingPipe == -1) {
                int[] lc = calculateLineAndColumn(template, pipeIndex);
                throw new TemplateSyntaxException("Missing closing pipe for expression at line " + lc[0] + ", column " + lc[1] + ".");
            }

            String content = template.substring(pipeIndex + 1, closingPipe).trim();
            int[] lc = calculateLineAndColumn(template, pipeIndex);
            TemplateExpressionValidator.validatePipeContent(content, lc[0], lc[1]);
            TokenType type = classifyToken(content);

            tokens.add(createToken(template, type, content, pipeIndex));
            cursor = closingPipe + 1;
            if (type == TokenType.PAGE || type == TokenType.MODEL) {
                if (cursor < template.length() && template.charAt(cursor) == '\r') {
                    cursor++;
                }
                if (cursor < template.length() && template.charAt(cursor) == '\n') {
                    cursor++;
                }
            }
        }

        return tokens;
    }

    Token createToken(String template, TokenType type, String value, int position) {
        int[] lc = calculateLineAndColumn(template, position);
        return new Token(type, value, position, lc[0], lc[1]);
    }

    int[] calculateLineAndColumn(String text, int targetIndex) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < targetIndex && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }

    TokenType classifyToken(String content) {
        if (content.startsWith("if ")) {
            return TokenType.IF;
        } else if (content.startsWith("else if ")) {
            return TokenType.ELSE_IF;
        } else if ("else".equals(content)) {
            return TokenType.ELSE;
        } else if ("/if".equals(content)) {
            return TokenType.END_IF;
        } else if (content.startsWith("each ")) {
            return TokenType.EACH;
        } else if ("/each".equals(content)) {
            return TokenType.END_EACH;
        } else if (content.startsWith("for ")) {
            return TokenType.FOR;
        } else if ("/for".equals(content)) {
            return TokenType.END_FOR;
        } else if ("continue".equals(content)) {
            return TokenType.CONTINUE;
        } else if ("break".equals(content)) {
            return TokenType.BREAK;
        } else if (content.startsWith("switch ")) {
            return TokenType.SWITCH;
        } else if (content.startsWith("case ")) {
            return TokenType.CASE;
        } else if ("default".equals(content)) {
            return TokenType.DEFAULT;
        } else if ("fallthrough".equals(content)) {
            return TokenType.FALLTHROUGH;
        } else if ("/switch".equals(content)) {
            return TokenType.END_SWITCH;
        } else if (content.equals("include") || content.startsWith("include ")) {
            return TokenType.INCLUDE;
        } else if (content.equals("layout") || content.startsWith("layout ")) {
            return TokenType.LAYOUT;
        } else if (content.equals("section") || content.startsWith("section ")) {
            return TokenType.SECTION;
        } else if ("/section".equals(content)) {
            return TokenType.END_SECTION;
        } else if (content.equals("yield") || content.startsWith("yield ")) {
            return TokenType.YIELD;
        } else if (content.equals("component") || content.startsWith("component ")) {
            return TokenType.COMPONENT;
        } else if ("/component".equals(content)) {
            return TokenType.END_COMPONENT;
        } else if (content.equals("slot") || content.startsWith("slot ")) {
            return TokenType.SLOT;
        } else if ("/slot".equals(content)) {
            return TokenType.END_SLOT;
        } else if (content.startsWith("model ")) {
            return TokenType.MODEL;
        } else if (content.startsWith("field ")) {
            return TokenType.FIELD;
        } else if (content.startsWith("display ")) {
            return TokenType.DISPLAY;
        } else if (content.startsWith("editor ")) {
            return TokenType.EDITOR;
        } else if (content.equals("macro") || content.startsWith("macro ")) {
            return TokenType.MACRO;
        } else if ("/macro".equals(content)) {
            return TokenType.END_MACRO;
        } else if (content.equals("call") || content.startsWith("call ")) {
            return TokenType.CALL;
        } else if ("separator".equals(content)) {
            return TokenType.SEPARATOR;
        } else if ("/separator".equals(content)) {
            return TokenType.END_SEPARATOR;
        } else if (content.equals("fragment") || content.startsWith("fragment ")) {
            return TokenType.FRAGMENT;
        } else if ("/fragment".equals(content)) {
            return TokenType.END_FRAGMENT;
        } else if ("minify".equals(content)) {
            return TokenType.MINIFY;
        } else if ("/minify".equals(content)) {
            return TokenType.END_MINIFY;
        } else if (content.startsWith("page ")) {
            return TokenType.PAGE;
        } else if ("attempt".equals(content)) {
            return TokenType.ATTEMPT;
        } else if (content.equals("recover") || content.startsWith("recover")) {
            return TokenType.RECOVER;
        } else if ("/attempt".equals(content)) {
            return TokenType.END_ATTEMPT;
        } else if ("pwa".equals(content) || content.startsWith("pwa ")) {
            return TokenType.PWA;
        } else if ("htmx".equals(content) || content.startsWith("htmx ")) {
            return TokenType.HTMX;
        } else if (content.startsWith("htmx-get ") || content.startsWith("htmx-post ") || content.startsWith("htmx-put ") || content.startsWith("htmx-delete ") || content.startsWith("htmx-patch ")) {
            return TokenType.HX_ATTR;
        } else if ("alpine".equals(content) || content.startsWith("alpine ")) {
            return TokenType.ALPINE;
        } else if ("alpine-data".equals(content) || content.startsWith("alpine-data ")) {
            return TokenType.STATE;
        } else if (content.startsWith("alpine-")) {
            return TokenType.ALPINE_ATTR;
        } else if ("js".equals(content) || content.startsWith("js ")) {
            return TokenType.JS;
        } else if ("/js".equals(content)) {
            return TokenType.END_JS;
        } else if ("css".equals(content) || content.startsWith("css ")) {
            return TokenType.CSS;
        } else if ("/css".equals(content)) {
            return TokenType.END_CSS;
        }
        return TokenType.EXPRESSION;
    }
}
