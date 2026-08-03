package io.lemadane.piped.template.engine.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.UUID;

public final class SafeTemplateErrorHandler {

    static final Log log = LogFactory.getLog(SafeTemplateErrorHandler.class);

    public static void handleError(Throwable error, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        response.setHeader("X-Correlation-ID", correlationId);

        String uri = request.getRequestURI();
        String method = request.getMethod();

        int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        Throwable current = error;
        while (current != null) {
            if (current instanceof io.lemadane.piped.template.engine.exceptions.TemplateUnauthorizedException) {
                statusCode = HttpServletResponse.SC_UNAUTHORIZED;
                break;
            } else if (current instanceof io.lemadane.piped.template.engine.exceptions.TemplateForbiddenException) {
                statusCode = HttpServletResponse.SC_FORBIDDEN;
                break;
            }
            current = current.getCause();
        }

        log.error("Template execution failure [Correlation-ID: " + correlationId + "] [" + method + "] " + uri + ": " + (error != null ? error.getMessage() : "Unknown error"), error);

        if (!response.isCommitted()) {
            response.setStatus(statusCode);
            response.setContentType("text/plain;charset=UTF-8");
            if (statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
                response.getWriter().write("Unauthorized");
            } else if (statusCode == HttpServletResponse.SC_FORBIDDEN) {
                response.getWriter().write("Forbidden");
            } else {
                response.getWriter().write("Internal Server Error");
            }
        }
    }
}
