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
                boolean foundEnd = false;
                while (cursor < len) {
                    char ch = input.charAt(cursor);
                    if (ch == '\'' && !inDouble) {
                        if (cursor == 0 || input.charAt(cursor - 1) != '\\') {
                            inSingle = !inSingle;
                        }
                    } else if (ch == '"' && !inSingle) {
                        if (cursor == 0 || input.charAt(cursor - 1) != '\\') {
                            inDouble = !inDouble;
                        }
                    } else if (!inSingle && !inDouble) {
                        if (ch == '[') {
                            bracketDepth++;
                        } else if (ch == ']') {
                            bracketDepth--;
                            if (bracketDepth == 0) {
                                cursor++;
                                foundEnd = true;
                                break;
                            }
                        }
                    }
                    cursor++;
                }

                if (!foundEnd || bracketDepth != 0 || inSingle || inDouble) {
                    throw new TemplateSyntaxException(String.format("Unclosed array for attribute '%s' in |%s|.", key, directiveName));
                }

                String arrayStr = input.substring(valStart, cursor).trim();
                value = parseArrayLiteral(directiveName, key, arrayStr);
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

    static Object parseArrayLiteral(String directiveName, String attributeKey, String arrayStr) {
        if (arrayStr == null || !arrayStr.startsWith("[") || !arrayStr.endsWith("]")) {
            throw new TemplateSyntaxException(String.format("Unclosed array for attribute '%s' in |%s|.", attributeKey, directiveName));
        }

        String inner = arrayStr.substring(1, arrayStr.length() - 1).trim();
        if (inner.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.List<Object> list = new java.util.ArrayList<>();
        int len = inner.length();
        int idx = 0;
        boolean expectingItem = true;

        while (idx < len) {
            while (idx < len && Character.isWhitespace(inner.charAt(idx))) {
                idx++;
            }
            if (idx >= len) {
                break;
            }

            char c = inner.charAt(idx);

            if (c == ',') {
                if (expectingItem) {
                    throw new TemplateSyntaxException(String.format("Invalid comma in array for attribute '%s' in |%s|.", attributeKey, directiveName));
                }
                expectingItem = true;
                idx++;
                continue;
            }

            if (!expectingItem) {
                throw new TemplateSyntaxException(String.format("Missing comma separator in array for attribute '%s' in |%s|.", attributeKey, directiveName));
            }

            if (c == '\'' || c == '"') {
                char quote = c;
                idx++;
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (idx < len) {
                    char ch = inner.charAt(idx);
                    if (ch == '\\' && idx + 1 < len) {
                        char next = inner.charAt(idx + 1);
                        if (next == quote || next == '\\') {
                            sb.append(next);
                            idx += 2;
                            continue;
                        }
                    }
                    if (ch == quote) {
                        closed = true;
                        idx++;
                        break;
                    }
                    sb.append(ch);
                    idx++;
                }
                if (!closed) {
                    throw new TemplateSyntaxException(String.format("Unclosed quote inside array for attribute '%s' in |%s|.", attributeKey, directiveName));
                }
                list.add(sb.toString());
                expectingItem = false;
            } else {
                int itemStart = idx;
                while (idx < len && inner.charAt(idx) != ',' && !Character.isWhitespace(inner.charAt(idx))) {
                    if (inner.charAt(idx) == '\'' || inner.charAt(idx) == '"') {
                        throw new TemplateSyntaxException(String.format("Malformed value in array for attribute '%s' in |%s|.", attributeKey, directiveName));
                    }
                    idx++;
                }
                String token = inner.substring(itemStart, idx).trim();
                if (token.isEmpty()) {
                    throw new TemplateSyntaxException(String.format("Invalid value in array for attribute '%s' in |%s|.", attributeKey, directiveName));
                }
                if ("true".equalsIgnoreCase(token)) {
                    list.add(Boolean.TRUE);
                } else if ("false".equalsIgnoreCase(token)) {
                    list.add(Boolean.FALSE);
                } else {
                    try {
                        if (token.contains(".")) {
                            list.add(Double.parseDouble(token));
                        } else {
                            try {
                                list.add(Integer.parseInt(token));
                            } catch (NumberFormatException nfe) {
                                list.add(Long.parseLong(token));
                            }
                        }
                    } catch (NumberFormatException nfe) {
                        list.add(token);
                    }
                }
                expectingItem = false;
            }
        }

        if (expectingItem) {
            throw new TemplateSyntaxException(String.format("Trailing comma in array for attribute '%s' in |%s|.", attributeKey, directiveName));
        }

        return list;
    }

    public static Map<String, Object> parseAttributes(String directiveName, String rawAttributes) {
        return parseAttributes(directiveName, rawAttributes, null);
    }
}
