package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipedTemplateViewTest {

    @Test
    void testRenderAppliesHTMXHeaders() throws Exception {
        // Create templates map
        Map<String, String> templates = new HashMap<>();
        templates.put("test-view", "|page hxTrigger = \"cartUpdated\"|\n" +
                                   "|page hxRedirect = \"/checkout\"|\n" +
                                   "|page hxPushUrl = \"/featured\"|\n" +
                                   "|page hxRefresh = true|\n" +
                                   "<div>Rendered</div>");
        
        TemplateEngine engine = new TemplateEngine(templates);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/test");
        request.addHeader("HX-Request", "true");
        request.addHeader("HX-Target", "#main");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        PipedTemplateView view = new PipedTemplateView();
        view.setUrl("test-view");
        view.setTemplateEngine(engine);
        
        Map<String, Object> model = new HashMap<>();
        view.renderMergedTemplateModel(model, request, response);
        
        // Verify HTMX response headers are set on response object
        assertEquals("cartUpdated", response.getHeader("HX-Trigger"));
        assertEquals("/checkout", response.getHeader("HX-Redirect"));
        assertEquals("/featured", response.getHeader("HX-Push-Url"));
        assertEquals("true", response.getHeader("HX-Refresh"));
        
        // Verify context page properties are set
        assertTrue(model.containsKey("page"));
        PipedPageContext ctx = (PipedPageContext) model.get("page");
        assertTrue(ctx.isHTMX());
        assertEquals("#main", ctx.getHxTarget());
        
        // Verify output content
        assertEquals("<div>Rendered</div>", response.getContentAsString().trim());
    }
}
