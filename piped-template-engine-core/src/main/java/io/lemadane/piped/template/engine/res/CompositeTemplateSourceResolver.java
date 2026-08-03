package io.lemadane.piped.template.engine.res;

import io.lemadane.piped.template.engine.exceptions.TemplateNotFoundException;
import java.util.ArrayList;
import java.util.List;

public final class CompositeTemplateSourceResolver implements TemplateSourceResolver {
    final List<TemplateSourceResolver> resolvers = new ArrayList<>();

    public CompositeTemplateSourceResolver(List<TemplateSourceResolver> resolvers) {
        if (resolvers != null) {
            this.resolvers.addAll(resolvers);
        }
    }

    public CompositeTemplateSourceResolver(TemplateSourceResolver... resolvers) {
        if (resolvers != null) {
            for (TemplateSourceResolver r : resolvers) {
                if (r != null) {
                    this.resolvers.add(r);
                }
            }
        }
    }

    public void addResolver(TemplateSourceResolver resolver) {
        if (resolver != null) {
            resolvers.add(resolver);
        }
    }

    @Override
    public TemplateSource resolve(String name) throws TemplateNotFoundException {
        for (TemplateSourceResolver resolver : resolvers) {
            try {
                TemplateSource source = resolver.resolve(name);
                if (source != null) {
                    return source;
                }
            } catch (TemplateNotFoundException ignored) {
                // Try next resolver
            }
        }
        throw new TemplateNotFoundException("Template not found across composite resolvers: " + name);
    }
}
