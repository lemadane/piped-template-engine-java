package io.lemadane.piped.template.engine.exceptions;

public class TemplateSyntaxException extends TemplateRenderException {
   public TemplateSyntaxException(String message) {
      super(message);
   }

   public TemplateSyntaxException(String message, Throwable cause) {
      super(message, cause);
   }
}