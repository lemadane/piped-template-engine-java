package io.lemadane.piped.template.engine.spring.routing;

import io.lemadane.piped.template.engine.RenderResult;
import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.compiler.CompiledTemplate;
import io.lemadane.piped.template.engine.spring.PipedPageContext;
import io.lemadane.piped.template.engine.spring.PipedResponseMetadataApplicator;
import io.lemadane.piped.template.engine.spring.SafeTemplateErrorHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PipedFileRouteHandlerMapping extends AbstractUrlHandlerMapping {

    static final Log log = LogFactory.getLog(PipedFileRouteHandlerMapping.class);

    final TemplateEngine templateEngine;
    final Map<String, PageDataLoader> dataLoaders = new HashMap<>();
    final PipedResponseMetadataApplicator metadataApplicator = new PipedResponseMetadataApplicator();
    final Set<String> registeredPatterns = new HashSet<>();
    boolean failFast = true;

    public PipedFileRouteHandlerMapping(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        setOrder(0);
    }

    public PipedFileRouteHandlerMapping(TemplateEngine templateEngine, boolean failFast) {
        this(templateEngine);
        this.failFast = failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isFailFast() {
        return failFast;
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
            Resource[] resources;
            try {
                resources = resolver.getResources("classpath*:pte-routes/**/+page.pte");
            } catch (Exception e) {
                if (failFast) {
                    throw new RouteDiscoveryException("Failed to scan pte-routes directory", e);
                } else {
                    log.error("Failed to scan pte-routes directory: " + e.getMessage(), e);
                    return;
                }
            }

            for (Resource resource : resources) {
                try {
                    String uri = resource.getURI().toString();
                    int routesIndex = uri.indexOf("pte-routes/");
                    if (routesIndex != -1) {
                        String relativePath = uri.substring(routesIndex + "pte-routes/".length());
                        String urlPattern = convertToSpringUrlPattern(relativePath, resource);
                        if (registeredPatterns.contains(urlPattern)) {
                            DuplicateTemplateRouteException ex = new DuplicateTemplateRouteException("Duplicate file-route pattern discovered: " + urlPattern);
                            if (failFast) {
                                throw ex;
                            } else {
                                log.error("Skipping duplicate template route: " + urlPattern, ex);
                                continue;
                            }
                        }
                        registeredPatterns.add(urlPattern);
                        registerFileRoute(urlPattern, relativePath, resource);
                    }
                } catch (RouteDiscoveryException rde) {
                    if (failFast) {
                        throw rde;
                    } else {
                        log.error("Failed to register file route for resource " + resource + ": " + rde.getMessage(), rde);
                    }
                } catch (Exception ex) {
                    RouteDiscoveryException rde = new RouteDiscoveryException("Malformed route resource: " + resource, ex);
                    if (failFast) {
                        throw rde;
                    } else {
                        log.error("Malformed route resource " + resource + ": " + ex.getMessage(), ex);
                    }
                }
            }
        } catch (RouteDiscoveryException rde) {
            throw rde;
        } catch (Exception e) {
            if (failFast) {
                throw new RouteDiscoveryException("Error discovering file routes", e);
            } else {
                log.error("Error discovering file routes: " + e.getMessage(), e);
            }
        }
    }

    void validateRoutePathBrackets(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        int openBracketIndex = relativePath.indexOf('[');
        while (openBracketIndex != -1) {
            int closeBracketIndex = relativePath.indexOf(']', openBracketIndex);
            if (closeBracketIndex == -1) {
                throw new InvalidTemplateRouteException("Unclosed dynamic segment bracket in route path: " + relativePath);
            }
            String segment = relativePath.substring(openBracketIndex + 1, closeBracketIndex);
            if (segment.isBlank()) {
                throw new InvalidTemplateRouteException("Empty dynamic segment name in route path: " + relativePath);
            }
            openBracketIndex = relativePath.indexOf('[', closeBracketIndex + 1);
        }
    }

    public String convertToSpringUrlPattern(String relativePath, Resource resource) {
        validateRoutePathBrackets(relativePath);
        String path = relativePath;
        if (path.startsWith("pte-routes/")) {
            path = path.substring("pte-routes/".length());
        }
        path = path.replace("+page.pte", "");
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        path = path.replaceAll("\\[([^\\]]+)\\]", "{$1}");
        String urlPattern = path.isEmpty() ? "/" : path;
        if (registeredPatterns.contains(urlPattern)) {
            if (failFast) {
                throw new DuplicateTemplateRouteException("Duplicate file-route pattern discovered: " + urlPattern);
            }
        }
        return urlPattern;
    }

    @SuppressWarnings("unchecked")
    public void registerFileRoute(String urlPattern, String relativePath, Resource resource) {
        validateRoutePathBrackets(relativePath);
        String effectiveUrlPattern = (urlPattern != null && !urlPattern.isBlank()) ? urlPattern : convertToSpringUrlPattern(relativePath, resource);
        registeredPatterns.add(effectiveUrlPattern);
        final String routePattern = effectiveUrlPattern;
        HttpRequestHandler handler = (request, response) -> {
            try {
                String templateContent;
                try (InputStream input = resource.getInputStream()) {
                    templateContent = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }

                CompiledTemplate compiled = templateEngine.compile(templateContent);
                Map<String, Object> initialMetadata = compiled.getMetadata();

                // Enforce auth check
                if (Boolean.TRUE.equals(initialMetadata.get("auth"))) {
                    if (request.getUserPrincipal() == null) {
                        SafeTemplateErrorHandler.handleError(new io.lemadane.piped.template.engine.exceptions.TemplateUnauthorizedException("Authentication required"), request, response);
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
                        SafeTemplateErrorHandler.handleError(new io.lemadane.piped.template.engine.exceptions.TemplateForbiddenException("Required role is missing"), request, response);
                        return;
                    }
                }

                Map<String, String> pathVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVars == null || pathVars.isEmpty()) {
                    String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
                    if (getPathMatcher().match(routePattern, lookupPath)) {
                        pathVars = getPathMatcher().extractUriTemplateVariables(routePattern, lookupPath);
                    }
                }
                PageDataLoader loader = dataLoaders.get(routePattern);
                Map<String, Object> loaderData = loader != null ? loader.load(request) : null;

                Map<String, Object> model = RouteModelBuilder.buildModel(request, pathVars, loaderData);

                if (!model.containsKey("page")) {
                    model.put("page", new PipedPageContext(request));
                }

                RenderResult result = templateEngine.renderTemplateSource(templateContent, model);
                Map<String, Object> metadata = result.metadata();

                metadataApplicator.apply(metadata, response);

                if (!metadata.containsKey("contentType")) {
                    response.setContentType("text/html;charset=UTF-8");
                }

                response.getWriter().write(result.html());
            } catch (Exception e) {
                SafeTemplateErrorHandler.handleError(e, request, response);
            }
        };

        registerHandler(routePattern, handler);
    }
}
