package io.lemadane.piped.template.engine.spring.routing;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

public final class RouteModelBuilder {

    static final Set<String> PROTECTED_FRAMEWORK_KEYS = Set.of(
            "page", "route", "query", "request", "session"
    );

    public static Map<String, Object> buildModel(HttpServletRequest request, Map<String, String> pathVariables, Map<String, Object> loaderData) {
        Map<String, Object> model = new LinkedHashMap<>();

        // Extract query parameters
        Map<String, Object> queryParameters = new LinkedHashMap<>();
        if (request != null && request.getParameterMap() != null) {
            request.getParameterMap().forEach((k, v) -> {
                if (v != null && v.length > 0) {
                    queryParameters.put(k, v.length == 1 ? v[0] : String.join(", ", v));
                }
            });
        }

        Map<String, String> safePathVars = pathVariables != null ? pathVariables : Map.of();

        // 1. Query parameters into root model
        queryParameters.forEach((k, v) -> {
            if (!PROTECTED_FRAMEWORK_KEYS.contains(k)) {
                model.put(k, v);
            }
        });

        // 2. Data loader values into root model (ignoring protected framework keys)
        if (loaderData != null) {
            loaderData.forEach((k, v) -> {
                if (!PROTECTED_FRAMEWORK_KEYS.contains(k)) {
                    model.put(k, v);
                } else if ("page".equals(k)) {
                    // Loader can explicitly supply a 'page' override model value if desired
                    model.put(k, v);
                }
            });
        }

        // 3. Path variables last into root model (path variables take precedence over query parameters and loader data)
        safePathVars.forEach((k, v) -> {
            if (!PROTECTED_FRAMEWORK_KEYS.contains(k)) {
                model.put(k, v);
            }
        });

        // Add explicit namespaces
        model.put("route", Collections.unmodifiableMap(new LinkedHashMap<>(safePathVars)));
        model.put("query", Collections.unmodifiableMap(new LinkedHashMap<>(queryParameters)));

        return model;
    }
}
