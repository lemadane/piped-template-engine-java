package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DirectiveAttributeParser {

    public static Map<String, Object> parseAttributes(String directiveName, String rawAttributes, Set<String> allowedAttributes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (rawAttributes == null || rawAttributes.isBlank()) {
            return result;
        }

        String input = rawAttributes.trim();
        int len = input.length();
        int cursor = 0;

        while (cursor < len) {
            while (cursor < len && Character.isWhitespace(input.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= len) {
                break;
            }

            if (input.charAt(cursor) == '=') {
                throw new TemplateSyntaxException(String.format("Invalid attribute syntax in |%s|: unexpected '=' at position %d.", directiveName, cursor));
            }

            int keyStart = cursor;
            while (cursor < len && !Character.isWhitespace(input.charAt(cursor)) && input.charAt(cursor) != '=') {
                cursor++;
            }
            String key = input.substring(keyStart, cursor).trim();
            if (key.isBlank()) {
                throw new TemplateSyntaxException(String.format("Invalid attribute name in |%s|.", directiveName));
            }

            if (allowedAttributes != null && !allowedAttributes.isEmpty() && !allowedAttributes.contains(key)) {
                throw new TemplateSyntaxException(String.format("Unknown attribute '%s' in |%s|.", key, directiveName));
            }

            if (result.containsKey(key)) {
                throw new TemplateSyntaxException(String.format("Duplicate attribute '%s' in |%s|.", key, directiveName));
            }

            while (cursor < len && Character.isWhitespace(input.charAt(cursor))) {
                cursor++;
            }

            if (cursor >= len || input.charAt(cursor) != '=') {
                result.put(key, Boolean.TRUE);
                continue;
            }

            cursor++; // Consume '='
            if (cursor < len && input.charAt(cursor) == '=') {
                throw new TemplateSyntaxException(String.format("Invalid attribute operator '==' for attribute '%s' in |%s|.", key, directiveName));
            }

            while (cursor < len && Character.isWhitespace(input.charAt(cursor))) {
                cursor++;
            }

            if (cursor >= len) {
                throw new TemplateSyntaxException(String.format("Missing value for attribute '%s' in |%s|.", key, directiveName));
            }

            char firstChar = input.charAt(cursor);
            Object value;

            if (firstChar == '[') {
                int bracketDepth = 0;
                int valStart = cursor;
                boolean inSingle = false;
                boolean inDouble = false;
                while (cursor < len) {
                    char ch = input.charAt(cursor);
                    if (ch == '\'' && !inDouble) inSingle = !inSingle;
                    else if (ch == '"' && !inSingle) inDouble = !inDouble;
                    else if (!inSingle && !inDouble) {
                        if (ch == '[') bracketDepth++;
                        else if (ch == ']') {
                            bracketDepth--;
                            if (bracketDepth == 0) {
                                cursor++;
                                break;
                            }
                        }
                    }
                    cursor++;
                }
                String arrayStr = input.substring(valStart, cursor).trim();
                value = parseArrayLiteral(arrayStr);
            } else if (firstChar == '\'' || firstChar == '"') {
                char quoteChar = firstChar;
                cursor++;
                StringBuilder valBuilder = new StringBuilder();
                boolean closed = false;

                while (cursor < len) {
                    char ch = input.charAt(cursor);
                    if (ch == '\\' && cursor + 1 < len) {
                        char nextChar = input.charAt(cursor + 1);
                        if (nextChar == quoteChar || nextChar == '\\') {
                            valBuilder.append(nextChar);
                            cursor += 2;
                            continue;
                        }
                    }
                    if (ch == quoteChar) {
                        closed = true;
                        cursor++;
                        break;
                    }
                    valBuilder.append(ch);
                    cursor++;
                }

                if (!closed) {
                    throw new TemplateSyntaxException(String.format("Unclosed quote for attribute '%s' in |%s|.", key, directiveName));
                }
                value = valBuilder.toString();
            } else {
                int valStart = cursor;
                while (cursor < len && !Character.isWhitespace(input.charAt(cursor))) {
                    cursor++;
                }
                String unquoted = input.substring(valStart, cursor).trim();
                if (unquoted.isEmpty()) {
                    throw new TemplateSyntaxException(String.format("Missing value for attribute '%s' in |%s|.", key, directiveName));
                }

                if ("true".equalsIgnoreCase(unquoted)) {
                    value = Boolean.TRUE;
                } else if ("false".equalsIgnoreCase(unquoted)) {
                    value = Boolean.FALSE;
                } else {
                    try {
                        if (unquoted.contains(".")) {
                            value = Double.parseDouble(unquoted);
                        } else {
                            value = Long.parseLong(unquoted);
                        }
                    } catch (NumberFormatException e) {
                        value = unquoted;
                    }
                }
            }

            result.put(key, value);
        }

        return result;
    }

    static Object parseArrayLiteral(String arrayStr) {
        if (arrayStr == null || arrayStr.length() < 2) {
            return java.util.Collections.emptyList();
        }
        String content = arrayStr.substring(1, arrayStr.length() - 1).trim();
        if (content.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Object> list = new java.util.ArrayList<>();
        for (String item : content.split(",")) {
            String trimmed = item.trim();
            if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                list.add(trimmed.substring(1, trimmed.length() - 1));
            } else if ("true".equalsIgnoreCase(trimmed)) {
                list.add(Boolean.TRUE);
            } else if ("false".equalsIgnoreCase(trimmed)) {
                list.add(Boolean.FALSE);
            } else {
                try {
                    list.add(Integer.parseInt(trimmed));
                } catch (Exception e) {
                    list.add(trimmed);
                }
            }
        }
        return list;
    }

    public static Map<String, Object> parseAttributes(String directiveName, String rawAttributes) {
        return parseAttributes(directiveName, rawAttributes, null);
    }
}
