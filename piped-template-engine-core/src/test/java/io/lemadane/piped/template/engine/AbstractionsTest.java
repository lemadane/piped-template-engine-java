package io.lemadane.piped.template.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.io.StringWriter;
import io.lemadane.piped.template.engine.expression.TemplateContext;

import static org.junit.jupiter.api.Assertions.*;

class AbstractionsTest {
    private final TemplateEngine engine = new TemplateEngine();

    @Test
    @DisplayName("Renders PWANode correctly in both interpreter and bytecode modes")
    void testPWANode() throws Exception {
        String template = "|pwa name='TaskMaster' theme='#4f46e5' icon='/icon-192.png' sw='/sw.js'|";

        // Interpreter mode
        String htmlInterp = engine.renderString(template, Map.of());
        assertTrue(htmlInterp.contains("meta name=\"theme-color\" content=\"#4f46e5\""));
        assertTrue(htmlInterp.contains("apple-mobile-web-app-title\" content=\"TaskMaster\""));
        assertTrue(htmlInterp.contains("link rel=\"apple-touch-icon\" href=\"/icon-192.png\""));
        assertTrue(htmlInterp.contains("navigator.serviceWorker.register('/sw.js')"));

        // Bytecode mode
        var executable = engine.compileToBytecode(template);
        StringWriter sw = new StringWriter();
        executable.render(new TemplateContext(Map.of()), sw, engine);
        String htmlByte = sw.toString();
        assertTrue(htmlByte.contains("meta name=\"theme-color\" content=\"#4f46e5\""));
        assertTrue(htmlByte.contains("apple-mobile-web-app-title\" content=\"TaskMaster\""));
        assertTrue(htmlByte.contains("link rel=\"apple-touch-icon\" href=\"/icon-192.png\""));
        assertTrue(htmlByte.contains("navigator.serviceWorker.register('/sw.js')"));
    }

    @Test
    @DisplayName("Renders HTMXHead and HTMXAttributes correctly")
    void testHTMXTags() throws Exception {
        String headTemplate = "|htmx src='/js/htmx.min.js' ext='json-enc' indicator=true|";

        // Interpreter
        String headInterp = engine.renderString(headTemplate, Map.of());
        assertTrue(headInterp.contains("script src=\"/js/htmx.min.js\""));
        assertTrue(headInterp.contains("dist/ext/json-enc.js"));
        assertTrue(headInterp.contains(".htmx-indicator{display:none;}"));

        // Bytecode
        var execHead = engine.compileToBytecode(headTemplate);
        StringWriter swHead = new StringWriter();
        execHead.render(new TemplateContext(Map.of()), swHead, engine);
        String headByte = swHead.toString();
        assertTrue(headByte.contains("script src=\"/js/htmx.min.js\""));
        assertTrue(headByte.contains("dist/ext/json-enc.js"));
        assertTrue(headByte.contains(".htmx-indicator{display:none;}"));

        String btnTemplate = "<button |htmx-get '/api/tasks' target='#task-list' swap='outerHTML'|>Refresh</button>";

        // Interpreter
        String btnInterp = engine.renderString(btnTemplate, Map.of());
        assertTrue(btnInterp.contains("hx-get=\"/api/tasks\""));
        assertTrue(btnInterp.contains("hx-target=\"#task-list\""));
        assertTrue(btnInterp.contains("hx-swap=\"outerHTML\""));

        // Bytecode
        var execBtn = engine.compileToBytecode(btnTemplate);
        StringWriter swBtn = new StringWriter();
        execBtn.render(new TemplateContext(Map.of()), swBtn, engine);
        String btnByte = swBtn.toString();
        assertTrue(btnByte.contains("hx-get=\"/api/tasks\""));
        assertTrue(btnByte.contains("hx-target=\"#task-list\""));
        assertTrue(btnByte.contains("hx-swap=\"outerHTML\""));
    }

    @Test
    @DisplayName("Renders AlpineJS settings, plugins, state and attributes correctly")
    void testAlpineTags() throws Exception {
        String headTemplate = "|alpine plugins='collapse,focus' cloak=true|";

        // Interpreter
        String headInterp = engine.renderString(headTemplate, Map.of());
        assertTrue(headInterp.contains("cdn.jsdelivr.net/npm/@alpinejs/collapse"));
        assertTrue(headInterp.contains("cdn.jsdelivr.net/npm/alpinejs"));
        assertTrue(headInterp.contains("[x-cloak]{display:none !important;}"));

        // Bytecode
        var execHead = engine.compileToBytecode(headTemplate);
        StringWriter swHead = new StringWriter();
        execHead.render(new TemplateContext(Map.of()), swHead, engine);
        String headByte = swHead.toString();
        assertTrue(headByte.contains("cdn.jsdelivr.net/npm/@alpinejs/collapse"));
        assertTrue(headByte.contains("cdn.jsdelivr.net/npm/alpinejs"));
        assertTrue(headByte.contains("[x-cloak]{display:none !important;}"));

        String stateTemplate = "<div |alpine-data open=false count=0 tab='home'|>";

        // Interpreter
        String stateInterp = engine.renderString(stateTemplate, Map.of());
        assertTrue(stateInterp.contains("x-data=\"{ count: 0, open: false, tab: &#39;home&#39; }\"") || stateInterp.contains("x-data=\"{ count: 0, open: false, tab: 'home' }\""));

        // Bytecode
        var execState = engine.compileToBytecode(stateTemplate);
        StringWriter swState = new StringWriter();
        execState.render(new TemplateContext(Map.of()), swState, engine);
        String stateByte = swState.toString();
        assertTrue(stateByte.contains("x-data=\"{ count: 0, open: false, tab: &#39;home&#39; }\"") || stateByte.contains("x-data=\"{ count: 0, open: false, tab: 'home' }\""));

        String showTemplate = "<div |alpine-show 'open'| |alpine-cloak|>";

        // Interpreter
        String showInterp = engine.renderString(showTemplate, Map.of());
        assertTrue(showInterp.contains("x-show=\"open\""));
        assertTrue(showInterp.contains("x-cloak"));

        // Bytecode
        var execShow = engine.compileToBytecode(showTemplate);
        StringWriter swShow = new StringWriter();
        execShow.render(new TemplateContext(Map.of()), swShow, engine);
        String showByte = swShow.toString();
        assertTrue(showByte.contains("x-show=\"open\""));
        assertTrue(showByte.contains("x-cloak"));
    }
}
