package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.options.PwaRenderOptions;

public record RenderOptions(
    boolean minify,
    boolean prettify,
    PwaRenderOptions pwaOptions
) {
    public static final RenderOptions DEFAULT = new RenderOptions(false, false, PwaRenderOptions.DEFAULT);

    public RenderOptions(boolean minify, boolean prettify) {
        this(minify, prettify, PwaRenderOptions.DEFAULT);
    }
}
