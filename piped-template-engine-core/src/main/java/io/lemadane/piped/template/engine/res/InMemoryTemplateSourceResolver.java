package io.lemadane.piped.template.engine.res;

import io.lemadane.piped.template.engine.exceptions.TemplateNotFoundException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTemplateSourceResolver implements TemplateSourceResolver {
    final Map<String, String> templates = new ConcurrentHashMap<>();

    public InMemoryTemplateSourceResolver() {}

    public InMemoryTemplateSourceResolver(Map<String, String> initialTemplates) {
        if (initialTemplates != null) {
            templates.putAll(initialTemplates);
        }
    }

    public void register(String name, String content) {
        templates.put(name, content);
    }

    @Override
    public TemplateSource resolve(String name) throws TemplateNotFoundException {
        String content = templates.get(name);
        if (content == null && name.endsWith(".pte")) {
            content = templates.get(name.substring(0, name.length() - 4));
        }
        if (content == null && !name.endsWith(".pte")) {
            content = templates.get(name + ".pte");
        }
        if (content == null) {
            throw new TemplateNotFoundException("In-memory template not found: " + name);
        }
        return new TemplateSource(name, content, System.currentTimeMillis());
    }
}
