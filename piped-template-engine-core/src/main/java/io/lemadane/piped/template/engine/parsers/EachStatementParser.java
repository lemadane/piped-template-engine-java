package io.lemadane.piped.template.engine.parsers;

import java.util.regex.Pattern;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.statements.EachStatement;

public final class EachStatementParser {
   static final Pattern VARIABLE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

   public EachStatement parse(String source) {
      if (source == null || source.isBlank()) {
         throw new TemplateSyntaxException(
               "Each statement must not be empty.");
      }

      final var trimmedSource = source.trim();

      if (!trimmedSource.startsWith("each ")) {
         throw new TemplateSyntaxException("Each statement must start with 'each'.");
      }

      final var body = trimmedSource.substring("each ".length()).trim();
      final var inIndex = findInIndex(body);

      if (inIndex == -1) {
         throw new TemplateSyntaxException(
               "Invalid each statement. Expected syntax: |each item in items|.");
      }

      final var leftSide = body.substring(0, inIndex).trim();
      final var collectionExpression = body.substring(inIndex + " in ".length()).trim();

      if (leftSide.isBlank()) {
         throw new TemplateSyntaxException(
               "Each item variable must not be empty.");
      }

      if (collectionExpression.isBlank()) {
         throw new TemplateSyntaxException(
               "Each collection expression must not be empty.");
      }

      if (leftSide.contains(",")) {
         final var parts = leftSide.split(",", 2);
         final var keyName = parts[0].trim();
         final var valueName = parts[1].trim();

         validateVariableName(keyName);
         validateVariableName(valueName);

         return new EachStatement(
               null,
               keyName,
               valueName,
               collectionExpression,
               true);
      }

      validateVariableName(leftSide);

      return new EachStatement(
            leftSide,
            null,
            null,
            collectionExpression,
            false);
   }

   int findInIndex(String body) {
      return body.indexOf(" in ");
   }

   void validateVariableName(String name) {
      if (!VARIABLE_NAME.matcher(name).matches()) {
         throw new TemplateSyntaxException(
               "Invalid variable name: " + name);
      }
   }
}