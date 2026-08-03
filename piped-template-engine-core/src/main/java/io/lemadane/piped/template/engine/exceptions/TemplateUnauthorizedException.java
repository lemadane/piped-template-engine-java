package io.lemadane.piped.template.engine.exceptions;

public class TemplateUnauthorizedException extends TemplateRenderException {

    public TemplateUnauthorizedException(String message) {
        super(message);
    }

    public TemplateUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
