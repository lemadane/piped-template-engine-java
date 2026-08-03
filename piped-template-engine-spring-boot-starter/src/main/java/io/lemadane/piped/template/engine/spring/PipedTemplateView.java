package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.RenderResult;
import io.lemadane.piped.template.engine.TemplateEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.view.AbstractTemplateView;

import java.util.Map;

public class PipedTemplateView extends AbstractTemplateView {

    TemplateEngine templateEngine;
    final PipedResponseMetadataApplicator metadataApplicator = new PipedResponseMetadataApplicator();

    public void setTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    protected void renderMergedTemplateModel(
            Map<String, Object> model,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String viewName = getUrl();

        // Prevent conflicts: only add "page" if not already present
        if (!model.containsKey("page")) {
            model.put("page", new PipedPageContext(request));
        }

        RenderResult result = templateEngine.renderNamedTemplate(viewName, model);
        Map<String, Object> metadata = result.metadata();

        // Apply metadata response headers
        metadataApplicator.apply(metadata, response);

        if (!metadata.containsKey("contentType")) {
            response.setContentType(getContentType());
        }

        response.getWriter().write(result.html());
    }
}
