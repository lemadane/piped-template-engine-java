package io.lemadane.piped.template.engine.exceptions;

public class TemplateForbiddenException extends TemplateRenderException {

    public TemplateForbiddenException(String message) {
        super(message);
    }

    public TemplateForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
