package io.lemadane.piped.template.engine.res;

import io.lemadane.piped.template.engine.exceptions.TemplateNotFoundException;

public interface TemplateSourceResolver {
    TemplateSource resolve(String normalizedTemplateName) throws TemplateNotFoundException;
}
