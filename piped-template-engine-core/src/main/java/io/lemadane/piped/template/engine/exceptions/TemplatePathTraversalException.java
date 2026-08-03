package io.lemadane.piped.template.engine.exceptions;

public class TemplatePathTraversalException extends TemplateNotFoundException {

    public TemplatePathTraversalException(String message) {
        super(message);
    }
}
