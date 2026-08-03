package io.lemadane.piped.template.engine.exceptions;

public class TemplateCircularDependencyException extends TemplateRenderException {

    public TemplateCircularDependencyException(String message) {
        super(message);
    }
}
