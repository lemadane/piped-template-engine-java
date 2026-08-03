package io.lemadane.piped.template.engine;

public record RenderOptions(
    boolean minify,
    boolean prettify
) {
    public static final RenderOptions DEFAULT = new RenderOptions(false, false);
}
