package io.lemadane.piped.template.engine.parsers;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.statements.IncludeStatement;

public final class IncludeStatementParser {
   
   public IncludeStatement parse(String source) {
      if (source == null || source.isBlank()) {
         throw new TemplateSyntaxException("Include statement must not be empty.");
      }

      final var trimmedSource = source.trim();

      if (!trimmedSource.startsWith("include ")) {
         throw new TemplateSyntaxException("Include statement must start with 'include'.");
      }

      final var body = trimmedSource.substring("include ".length()).trim();

      if (body.isBlank()) {
         throw new TemplateSyntaxException("Include template name must not be empty.");
      }

      final var withIndex = findWithIndex(body);

      if (withIndex == -1) {
         return new IncludeStatement(
               body,
               null,
               false);
      }

      final var templateName = body.substring(0, withIndex).trim();
      final var contextExpression = body.substring(withIndex + " with ".length()).trim();

      if (templateName.isBlank()) {
         throw new TemplateSyntaxException("Include template name must not be empty.");
      }

      if (contextExpression.isBlank()) {
         throw new TemplateSyntaxException("Include context expression must not be empty.");
      }

      return new IncludeStatement(
            templateName,
            contextExpression,
            true);
   }

   int findWithIndex(String body) {
      boolean insideSingleQuote = false;
      boolean insideDoubleQuote = false;
      int parenthesisDepth = 0;

      for (int index = 0; index <= body.length() - " with ".length(); index++) {
         final var current = body.charAt(index);

         if (current == '\'' && !insideDoubleQuote) {
            insideSingleQuote = !insideSingleQuote;
            continue;
         }

         if (current == '"' && !insideSingleQuote) {
            insideDoubleQuote = !insideDoubleQuote;
            continue;
         }

         if (insideSingleQuote || insideDoubleQuote) {
            continue;
         }

         if (current == '(') {
            parenthesisDepth++;
            continue;
         }

         if (current == ')') {
            parenthesisDepth--;
            continue;
         }

         if (parenthesisDepth == 0 && body.startsWith(" with ", index)) {
            return index;
         }
      }

      return -1;
   }
}