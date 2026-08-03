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

    @Override
    public TemplateSource resolve(String name) throws TemplateNotFoundException {
        if (name == null || name.isEmpty()) {
            throw new TemplateNotFoundException("Template name cannot be empty");
        }

        String path = name;
        if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
        }

        if (path.contains("..")) {
            throw new TemplateNotFoundException("Directory traversal forbidden: " + name);
        }

        String fullPath;
        if (path.startsWith("/")) {
            fullPath = path;
        } else {
            fullPath = prefix + path;
        }

        if (!suffix.isEmpty() && !fullPath.endsWith(suffix) && !fullPath.endsWith(".html") && !fullPath.endsWith(".pte")) {
            fullPath = fullPath + suffix;
        }

        // Clean leading slash for ClassLoader resource lookup if needed
        String resourcePath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;

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
