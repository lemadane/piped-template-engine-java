package io.lemadane.piped.template.engine.compiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class TemplateCache {
    final Map<String, CompiledTemplate> cache = new ConcurrentHashMap<>();

    public CompiledTemplate get(String key) {
        return cache.get(key);
    }

    public CompiledTemplate computeIfAbsent(String key, Function<String, CompiledTemplate> mappingFunction) {
        return cache.computeIfAbsent(key, mappingFunction);
    }

    public void put(String key, CompiledTemplate template) {
        cache.put(key, template);
    }

    public void clear() {
        cache.clear();
    }
}
