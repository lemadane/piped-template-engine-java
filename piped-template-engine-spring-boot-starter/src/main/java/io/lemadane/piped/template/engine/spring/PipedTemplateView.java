package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.compiler.CompiledTemplate;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.view.AbstractTemplateView;

import java.util.HashMap;
import java.util.Map;

public class PipedTemplateView extends AbstractTemplateView {

    private TemplateEngine templateEngine;

    public void setTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    protected void renderMergedTemplateModel(
            Map<String, Object> model,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String viewName = getUrl();
        CompiledTemplate compiled = templateEngine.compileTemplate(viewName);
        Map<String, Object> metadata = compiled.getMetadata();

        // Prevent conflicts: only add "page" if not already present
        if (!model.containsKey("page")) {
            model.put("page", new PipedPageContext(request));
        }

        // Prevent conflicts: only add "title" from page metadata if not already present in the model
        if (metadata.containsKey("title") && !model.containsKey("title")) {
            model.put("title", metadata.get("title"));
        }

        // Call the templateEngine render method directly to support layouts and section yields
        String html = templateEngine.render(viewName, model);

        // Apply HTMX metadata response headers
        if (metadata.containsKey("hxTrigger")) {
            response.setHeader("HX-Trigger", String.valueOf(metadata.get("hxTrigger")));
        }
        if (metadata.containsKey("hxRedirect")) {
            response.setHeader("HX-Redirect", String.valueOf(metadata.get("hxRedirect")));
        }
        if (metadata.containsKey("hxPushUrl")) {
            response.setHeader("HX-Push-Url", String.valueOf(metadata.get("hxPushUrl")));
        }
        if (metadata.containsKey("hxRefresh")) {
            Object refresh = metadata.get("hxRefresh");
            if (refresh instanceof Boolean b && b) {
                response.setHeader("HX-Refresh", "true");
            } else {
                response.setHeader("HX-Refresh", String.valueOf(refresh));
            }
        }

        response.setContentType(getContentType());
        response.getWriter().write(html);
    }
}
