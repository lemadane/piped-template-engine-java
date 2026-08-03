package io.lemadane.piped.template.engine.exceptions;

public class TemplateNotFoundException extends TemplateRenderException {
    public TemplateNotFoundException(String message) {
        super(message);
    }

    public TemplateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
