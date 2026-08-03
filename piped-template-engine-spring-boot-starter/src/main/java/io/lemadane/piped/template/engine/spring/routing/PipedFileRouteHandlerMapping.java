package io.lemadane.piped.template.engine.spring.routing;

import io.lemadane.piped.template.engine.RenderResult;
import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.compiler.CompiledTemplate;
import io.lemadane.piped.template.engine.spring.PipedPageContext;
import io.lemadane.piped.template.engine.spring.PipedResponseMetadataApplicator;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;

import java.util.*;

public class PipedFileRouteHandlerMapping extends AbstractUrlHandlerMapping {

    final TemplateEngine templateEngine;
    final Map<String, PageDataLoader> dataLoaders = new HashMap<>();
    final PipedResponseMetadataApplicator metadataApplicator = new PipedResponseMetadataApplicator();

    public PipedFileRouteHandlerMapping(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        setOrder(0);
    }

    public void registerPageDataLoader(String routePath, PageDataLoader loader) {
        dataLoaders.put(routePath, loader);
    }

    @Override
    protected void initApplicationContext() {
        super.initApplicationContext();
        discoverAndRegisterRoutes();
    }

    void discoverAndRegisterRoutes() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:pte-routes/**/+page.pte");

            for (Resource resource : resources) {
                String uri = resource.getURI().toString();
                int routesIndex = uri.indexOf("pte-routes/");
                if (routesIndex != -1) {
                    String relativePath = uri.substring(routesIndex + "pte-routes/".length());
                    String urlPattern = convertToSpringUrlPattern(relativePath);
                    registerFileRoute(urlPattern, relativePath, resource);
                }
            }
        } catch (Exception e) {
            // Log or ignore if pte-routes directory is not present
        }
    }

    String convertToSpringUrlPattern(String relativePath) {
        String path = relativePath.replace("+page.pte", "");
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        path = path.replaceAll("\\[([^\\]]+)\\]", "{$1}");
        return path.isEmpty() ? "/" : path;
    }

    @SuppressWarnings("unchecked")
    void registerFileRoute(String urlPattern, String relativePath, Resource resource) {
        HttpRequestHandler handler = (request, response) -> {
            try {
                String templateContent = new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                CompiledTemplate compiled = templateEngine.compile(templateContent);
                Map<String, Object> initialMetadata = compiled.getMetadata();

                // Enforce auth check
                if (Boolean.TRUE.equals(initialMetadata.get("auth"))) {
                    if (request.getUserPrincipal() == null) {
                        response.sendError(401, "Unauthorized");
                        return;
                    }
                }

                // Enforce roles check
                if (initialMetadata.containsKey("roles")) {
                    Object rolesObj = initialMetadata.get("roles");
                    List<String> requiredRoles;
                    if (rolesObj instanceof List) {
                        requiredRoles = (List<String>) rolesObj;
                    } else {
                        requiredRoles = List.of(String.valueOf(rolesObj));
                    }
                    boolean hasRole = false;
                    for (String role : requiredRoles) {
                        if (request.isUserInRole(role)) {
                            hasRole = true;
                            break;
                        }
                    }
                    if (!hasRole) {
                        response.sendError(403, "Forbidden");
                        return;
                    }
                }

                Map<String, Object> model = new HashMap<>();

                Map<String, String> pathVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVars != null) {
                    model.putAll(pathVars);
                }

                request.getParameterMap().forEach((k, v) -> {
                    if (v != null && v.length > 0) {
                        model.put(k, v.length == 1 ? v[0] : v);
                    }
                });

                PageDataLoader loader = dataLoaders.get(urlPattern);
                if (loader != null) {
                    Map<String, Object> loaded = loader.load(request);
                    if (loaded != null) {
                        model.putAll(loaded);
                    }
                }

                if (!model.containsKey("page")) {
                    model.put("page", new PipedPageContext(request));
                }

                RenderResult result = templateEngine.renderTemplateSource(templateContent, model);
                Map<String, Object> metadata = result.metadata();

                // Apply custom headers via shared applicator
                metadataApplicator.apply(metadata, response);

                if (!metadata.containsKey("contentType")) {
                    response.setContentType("text/html;charset=UTF-8");
                }

                response.getWriter().write(result.html());
            } catch (Exception e) {
                response.sendError(500, e.getMessage());
            }
        };

        registerHandler(urlPattern, handler);
    }
}
