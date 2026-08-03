package io.lemadane.piped.template.engine.expression;

import io.lemadane.piped.template.engine.res.TemplateSourceResolver;
import java.util.HashMap;
import java.util.Map;

public final class TemplateContext {
    final Map<String, Object> values;
    final Map<String, Object> localValues = new HashMap<>();
    TemplateSourceResolver resolver;
    Object engine;
    Map<String, String> sections;
    Map<String, String> slots;

    public TemplateContext(Map<String, Object> values) {
        this.values = values != null ? new HashMap<>(values) : new HashMap<>();
    }

    TemplateContext(Map<String, Object> parentValues, Map<String, Object> childValues) {
        this.values = new HashMap<>(parentValues);
        if (childValues != null) {
            this.values.putAll(childValues);
        }
    }

    public Object get(String name) {
        if (localValues.containsKey(name)) {
            return localValues.get(name);
        }
        return values.get(name);
    }

    public Map<String, Object> getValues() {
        Map<String, Object> all = new HashMap<>(values);
        all.putAll(localValues);
        return all;
    }

    public TemplateContext with(String name, Object value) {
        final var childValues = new HashMap<String, Object>();
        childValues.put(name, value);
        TemplateContext next = copyMetadata(new TemplateContext(values, childValues));
        next.localValues.putAll(this.localValues);
        return next;
    }

    public TemplateContext withAll(Map<String, Object> childValues) {
        TemplateContext next = copyMetadata(new TemplateContext(values, childValues));
        next.localValues.putAll(this.localValues);
        return next;
    }

    public TemplateContext withModel(Map<String, Object> model) {
        TemplateContext next = copyMetadata(new TemplateContext(model));
        next.localValues.putAll(this.localValues);
        return next;
    }

    public TemplateContext subContext(Map<String, Object> childValues) {
        return withAll(childValues);
    }

    public void pushLocal(String name, Object value) {
        localValues.put(name, value);
    }

    public TemplateSourceResolver getResolver() {
        return resolver;
    }

    public void setResolver(TemplateSourceResolver resolver) {
        this.resolver = resolver;
    }

    public TemplateContext withResolver(TemplateSourceResolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public EngineRenderDelegate getEngine() {
        if (engine instanceof EngineRenderDelegate delegate) {
            return delegate;
        }
        return null;
    }

    public void setEngine(Object engine) {
        this.engine = engine;
    }

    public TemplateContext withEngine(Object engine) {
        this.engine = engine;
        return this;
    }

    public Map<String, String> getSections() {
        return sections;
    }

    public void setSections(Map<String, String> sections) {
        this.sections = sections;
    }

    public TemplateContext withSections(Map<String, String> sections) {
        TemplateContext next = copyMetadata(new TemplateContext(values));
        next.localValues.putAll(this.localValues);
        next.sections = sections;
        return next;
    }

    public Map<String, String> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, String> slots) {
        this.slots = slots;
    }

    public TemplateContext withSlots(Map<String, String> slots) {
        TemplateContext next = copyMetadata(new TemplateContext(values));
        next.localValues.putAll(this.localValues);
        next.slots = slots;
        return next;
    }

    java.util.Set<String> activeIncludes = java.util.Set.of();

    public java.util.Set<String> getActiveIncludes() {
        return activeIncludes;
    }

    public TemplateContext withInclude(String templatePath) {
        java.util.Set<String> newIncludes = new java.util.HashSet<>(activeIncludes);
        newIncludes.add(templatePath);
        TemplateContext next = copyMetadata(new TemplateContext(values));
        next.localValues.putAll(this.localValues);
        next.activeIncludes = java.util.Collections.unmodifiableSet(newIncludes);
        return next;
    }

    TemplateContext copyMetadata(TemplateContext target) {
        target.resolver = this.resolver;
        target.engine = this.engine;
        target.sections = this.sections;
        target.slots = this.slots;
        target.activeIncludes = this.activeIncludes;
        return target;
    }

    @FunctionalInterface
    public interface EngineRenderDelegate {
        String renderStringWithContext(String templateContent, TemplateContext context);
        default String renderComponentTemplate(String templateContent, TemplateContext context) {
            return renderStringWithContext(templateContent, context);
        }
    }
}