package io.lemadane.piped.template.engine.escapers;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonEscaper {
   public String escape(Object value) {
      return toJson(value);
   }

   String toJson(Object value) {
      if (value == null) {
         return "null";
      }

      if (value instanceof Boolean booleanValue) {
         return String.valueOf(booleanValue);
      }

      if (value instanceof Number numberValue) {
         return numberToJson(numberValue);
      }

      if (value instanceof CharSequence textValue) {
         return stringToJson(String.valueOf(textValue));
      }

      if (value instanceof Map<?, ?> mapValue) {
         return mapToJson(mapValue);
      }

      if (value instanceof Collection<?> collectionValue) {
         return collectionToJson(collectionValue);
      }

      if (value.getClass().isArray()) {
         return arrayToJson(value);
      }

      return mapToJson(objectToMap(value));
   }

   String numberToJson(Number numberValue) {
      final var text = String.valueOf(numberValue);

      if ("NaN".equals(text) || "Infinity".equals(text) || "-Infinity".equals(text)) {
         return "null";
      }

      return text;
   }

   String stringToJson(String value) {
      final var escaped = new StringBuilder(value.length() + 2);
      escaped.append('"');

      for (int index = 0; index < value.length(); index++) {
         final var current = value.charAt(index);

         switch (current) {
            case '"' -> escaped.append("\\\"");
            case '\\' -> escaped.append("\\\\");
            case '\b' -> escaped.append("\\b");
            case '\f' -> escaped.append("\\f");
            case '\n' -> escaped.append("\\n");
            case '\r' -> escaped.append("\\r");
            case '\t' -> escaped.append("\\t");
            case '<' -> escaped.append("\\u003C");
            case '>' -> escaped.append("\\u003E");
            case '&' -> escaped.append("\\u0026");
            case '\'' -> escaped.append("\\u0027");
            default -> {
               if (current < 0x20) {
                  escaped.append(String.format("\\u%04x", (int) current));
               } else {
                  escaped.append(current);
               }
            }
         }
      }

      escaped.append('"');

      return escaped.toString();
   }

   String mapToJson(Map<?, ?> mapValue) {
      final var json = new StringBuilder();
      json.append('{');

      boolean first = true;

      for (final var entry : mapValue.entrySet()) {
         if (!first) {
            json.append(',');
         }

         json.append(stringToJson(String.valueOf(entry.getKey())));
         json.append(':');
         json.append(toJson(entry.getValue()));

         first = false;
      }

      json.append('}');

      return json.toString();
   }

   String collectionToJson(Collection<?> collectionValue) {
      final var json = new StringBuilder();
      json.append('[');

      boolean first = true;

      for (final var item : collectionValue) {
         if (!first) {
            json.append(',');
         }

         json.append(toJson(item));

         first = false;
      }

      json.append(']');

      return json.toString();
   }

   String arrayToJson(Object arrayValue) {
      final var json = new StringBuilder();
      final var length = Array.getLength(arrayValue);

      json.append('[');

      for (int index = 0; index < length; index++) {
         if (index > 0) {
            json.append(',');
         }

         json.append(toJson(Array.get(arrayValue, index)));
      }

      json.append(']');

      return json.toString();
   }

   Map<String, Object> objectToMap(Object value) {
      final var values = new LinkedHashMap<String, Object>();
      final var type = value.getClass();

      for (final var method : type.getMethods()) {
         if (method.getParameterCount() != 0) {
            continue;
         }

         if ("getClass".equals(method.getName())) {
            continue;
         }

         final var propertyName = propertyNameFromMethod(method);

         if (propertyName == null) {
            continue;
         }

         values.put(propertyName, invokeMethod(value, method));
      }

      for (final var field : type.getFields()) {
         values.put(field.getName(), readField(value, field));
      }

      return values;
   }

   String propertyNameFromMethod(Method method) {
      final var methodName = method.getName();

      if (methodName.startsWith("get") && methodName.length() > 3) {
         return decapitalize(methodName.substring(3));
      }

      if (methodName.startsWith("is") && methodName.length() > 2) {
         return decapitalize(methodName.substring(2));
      }

      if (method.getDeclaringClass().isRecord()) {
         return methodName;
      }

      return null;
   }

   Object invokeMethod(Object source, Method method) {
      try {
         return method.invoke(source);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Failed to serialize object to JSON.", exception);
      }
   }

   Object readField(Object source, Field field) {
      try {
         return field.get(source);
      } catch (IllegalAccessException exception) {
         throw new IllegalStateException("Failed to serialize object field to JSON.", exception);
      }
   }

   String decapitalize(String value) {
      if (value == null || value.isBlank()) {
         return value;
      }

      if (value.length() == 1) {
         return value.toLowerCase();
      }

      return Character.toLowerCase(value.charAt(0)) + value.substring(1);
   }
}