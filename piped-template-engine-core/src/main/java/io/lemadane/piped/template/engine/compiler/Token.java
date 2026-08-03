package io.lemadane.piped.template.engine.compiler;

public record Token(
    TokenType type,
    String value,
    int position,
    int line,
    int column
) {
    public Token(TokenType type, String value, int position) {
        this(type, value, position, 1, 1);
    }
}
