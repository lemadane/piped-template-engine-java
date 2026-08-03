package io.lemadane.piped.template.engine.res;

import io.lemadane.piped.template.engine.exceptions.TemplateNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ClasspathTemplateSourceResolver implements TemplateSourceResolver {
    final String prefix;
    final String suffix;
    final ClassLoader classLoader;

    public ClasspathTemplateSourceResolver() {
        this("classpath:/pte-templates/", ".pte", Thread.currentThread().getContextClassLoader());
    }

    public ClasspathTemplateSourceResolver(String prefix, String suffix) {
        this(prefix, suffix, Thread.currentThread().getContextClassLoader());
    }

    public ClasspathTemplateSourceResolver(String prefix, String suffix, ClassLoader classLoader) {
        String p = prefix != null ? prefix : "";
        if (p.startsWith("classpath:")) {
            p = p.substring("classpath:".length());
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        this.prefix = p;
        this.suffix = suffix != null ? suffix : "";
        this.classLoader = classLoader != null ? classLoader : ClasspathTemplateSourceResolver.class.getClassLoader();
    }

    static String decodeAndNormalize(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String path = name;
        if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
        }
        try {
            path = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return path.replace('\\', '/');
    }

    @Override
    public TemplateSource resolve(String name) throws TemplateNotFoundException {
        if (name == null || name.isEmpty()) {
            throw new TemplateNotFoundException("Template name cannot be empty");
        }

        String path = decodeAndNormalize(name);
        if (path.contains("..")) {
            throw new TemplateNotFoundException("Directory traversal forbidden: " + name);
        }

        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        String fullPath = prefix + path;
        if (!suffix.isEmpty() && !fullPath.endsWith(suffix) && !fullPath.endsWith(".html") && !fullPath.endsWith(".pte")) {
            fullPath = fullPath + suffix;
        }

        String resourcePath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
        String prefixClean = prefix.startsWith("/") ? prefix.substring(1) : prefix;
        if (!resourcePath.startsWith(prefixClean)) {
            throw new TemplateNotFoundException("Access outside template namespace forbidden: " + name);
        }

        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                // Try fallback to class loading via system or current class
                InputStream sysIs = ClasspathTemplateSourceResolver.class.getResourceAsStream(fullPath);
                if (sysIs != null) {
                    byte[] bytes = sysIs.readAllBytes();
                    sysIs.close();
                    return new TemplateSource(name, new String(bytes, StandardCharsets.UTF_8), System.currentTimeMillis());
                }
                throw new TemplateNotFoundException("Classpath template resource not found: " + name);
            }
            byte[] bytes = is.readAllBytes();
            return new TemplateSource(name, new String(bytes, StandardCharsets.UTF_8), System.currentTimeMillis());
        } catch (Exception e) {
            if (e instanceof TemplateNotFoundException tnfe) {
                throw tnfe;
            }
            throw new TemplateNotFoundException("Failed to read classpath template: " + name, e);
        }
    }
}
