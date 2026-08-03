package io.lemadane.piped.template.engine.options;

public record PwaRenderOptions(
    RegistrationMode mode,
    String registrationScript,
    boolean requireNonceForInline
) {
    public enum RegistrationMode {
        EXTERNAL,
        INLINE
    }

    public static final PwaRenderOptions DEFAULT = new PwaRenderOptions(
        RegistrationMode.EXTERNAL,
        "/pte-assets/pwa-register.js",
        false
    );
}
