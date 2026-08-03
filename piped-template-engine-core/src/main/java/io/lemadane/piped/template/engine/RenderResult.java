package io.lemadane.piped.template.engine;

import java.util.Map;

public record RenderResult(
    String html,
    Map<String, Object> metadata,
    TemplateEngine.ExecutionMode executionMode
) {}
