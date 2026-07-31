package io.lemadane.piped.template.engine.expression;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;

public final class PropertyReader {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle NULL_HANDLE = null;

    private final Map<String, MethodHandle> handleCache = new ConcurrentHashMap<>();
    private final Map<String, Path> pathCache = new ConcurrentHashMap<>();

    public Object readPath(String expression, TemplateContext context) {
        final var path = pathCache.computeIfAbsent(expression, this::parsePath);

        if (path.rootName().isBlank()) {
            return null;
        }

        Object current = context.get(path.rootName());

        for (final var segment : path.segments()) {
            if (current == null) {
                return null;
            }

            current = readProperty(
                    current,
                    segment.name(),
                    segment.optional());
        }

        return current;
    }

    private Object readProperty(Object source, String propertyName, boolean optional) {
        String cleanPropName = propertyName.endsWith("()") ? propertyName.substring(0, propertyName.length() - 2) : propertyName;

        if (source instanceof Map<?, ?> map) {
            if (map.containsKey(cleanPropName)) {
                return map.get(cleanPropName);
            }
            if ("size".equals(cleanPropName)) {
                return map.size();
            }
            return map.get(propertyName);
        }

        if (source instanceof java.util.Collection<?> col && "size".equals(cleanPropName)) {
            return col.size();
        }

        if (source.getClass().isArray() && ("size".equals(cleanPropName) || "length".equals(cleanPropName))) {
            return java.lang.reflect.Array.getLength(source);
        }

        if (source instanceof Map.Entry<?, ?> entry) {
            return readMapEntryProperty(
                    entry,
                    cleanPropName,
                    optional);
        }

        final var sourceType = source.getClass();
        final var cacheKey = sourceType.getName() + ":" + cleanPropName;

        MethodHandle handle = handleCache.get(cacheKey);
        if (handle != null) {
            try {
                return handle.invoke(source);
            } catch (Throwable e) {
                throw new TemplateRenderException("Failed to read property '" + propertyName + "'.", e);
            }
        }

        handle = findAndCreateHandle(sourceType, cleanPropName);

        if (handle != null) {
            handleCache.put(cacheKey, handle);
            try {
                return handle.invoke(source);
            } catch (Throwable e) {
                throw new TemplateRenderException("Failed to read property '" + propertyName + "'.", e);
            }
        }

        if (optional) {
            return null;
        }

        throw new TemplateRenderException(
                "Unknown property '" + propertyName + "' on " + sourceType.getName() + ".");
    }

    private MethodHandle findAndCreateHandle(Class<?> sourceType, String propertyName) {
        try {
            final var recordAccessor = findZeroArgumentMethod(sourceType, propertyName);
            if (recordAccessor != null) {
                recordAccessor.setAccessible(true);
                return LOOKUP.unreflect(recordAccessor);
            }

            final var getter = findZeroArgumentMethod(sourceType, "get" + capitalize(propertyName));
            if (getter != null) {
                getter.setAccessible(true);
                return LOOKUP.unreflect(getter);
            }

            final var booleanGetter = findZeroArgumentMethod(sourceType, "is" + capitalize(propertyName));
            if (booleanGetter != null) {
                booleanGetter.setAccessible(true);
                return LOOKUP.unreflect(booleanGetter);
            }

            final var field = findPublicField(sourceType, propertyName);
            if (field != null) {
                field.setAccessible(true);
                return LOOKUP.unreflectGetter(field);
            }
        } catch (IllegalAccessException e) {
            // Fallback
        }
        return null;
    }

    private Object readMapEntryProperty(
            Map.Entry<?, ?> entry,
            String propertyName,
            boolean optional) {
        return switch (propertyName) {
            case "key" -> entry.getKey();
            case "value" -> entry.getValue();
            default -> {
                if (optional) {
                    yield null;
                }

                throw new TemplateRenderException(
                        "Unknown property '" + propertyName + "' on Map.Entry.");
            }
        };
    }

    private Path parsePath(String expression) {
        final var trimmedExpression = expression.trim();

        if (trimmedExpression.isBlank()) {
            return new Path(
                    "",
                    java.util.List.of());
        }

        final var segments = new ArrayList<PathSegment>();
        final var current = new StringBuilder();

        String rootName = null;
        boolean nextSegmentOptional = false;
        int index = 0;

        while (index < trimmedExpression.length()) {
            final var currentCharacter = trimmedExpression.charAt(index);

            if (currentCharacter == '.') {
                if (current.isEmpty()) {
                    throw new TemplateSyntaxException("Invalid property path: " + expression);
                }

                if (rootName == null) {
                    rootName = current.toString();
                } else {
                    segments.add(new PathSegment(
                            current.toString(),
                            nextSegmentOptional));
                }

                current.setLength(0);
                nextSegmentOptional = false;
                index++;
                continue;
            }

            if (currentCharacter == '?'
                    && index + 1 < trimmedExpression.length()
                    && trimmedExpression.charAt(index + 1) == '.') {
                if (current.isEmpty()) {
                    throw new TemplateSyntaxException("Invalid optional property path: " + expression);
                }

                if (rootName == null) {
                    rootName = current.toString();
                } else {
                    segments.add(new PathSegment(
                            current.toString(),
                            nextSegmentOptional));
                }

                current.setLength(0);
                nextSegmentOptional = true;
                index += 2;
                continue;
            }

            current.append(currentCharacter);
            index++;
        }

        if (current.isEmpty()) {
            throw new TemplateSyntaxException("Invalid property path: " + expression);
        }

        if (rootName == null) {
            rootName = current.toString();
        } else {
            segments.add(new PathSegment(
                    current.toString(),
                    nextSegmentOptional));
        }

        return new Path(
                rootName,
                java.util.List.copyOf(segments));
    }

    private Method findZeroArgumentMethod(Class<?> sourceType, String methodName) {
        try {
            final var method = sourceType.getMethod(methodName);

            if (method.getParameterCount() == 0) {
                return method;
            }

            return null;
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private Field findPublicField(Class<?> sourceType, String fieldName) {
        try {
            return sourceType.getField(fieldName);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        if (value.length() == 1) {
            return value.toUpperCase();
        }

        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record Path(
            String rootName,
            java.util.List<PathSegment> segments) {
    }

    private record PathSegment(
            String name,
            boolean optional) {
    }
}