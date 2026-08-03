package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.compiler.CompiledTemplate;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParityTestSuite {

    @Test
    @DisplayName("Identical output across render(), renderString(), compile(), compileToBytecode()")
    void testRenderingParity() throws Exception {
        TemplateEngine engine = new TemplateEngine(Map.of("partial", "Hello |name|"));
        String template = "Greeting: |include partial| |if age >= 18|(Adult)|/if|";

        Map<String, Object> model = Map.of("name", "Bob", "age", 25);
        TemplateContext.EngineRenderDelegate delegate = (tpl, ctx) -> engine.renderStringWithContext(tpl, ctx);

        // 1. renderString
        String res1 = engine.renderString(template, model);

        // 2. render
        String res2 = engine.render(template, model);

        // 3. compile().renderToString()
        CompiledTemplate compiled = engine.compile(template);
        String res3 = compiled.renderToString(new TemplateContext(model).withResolver(engine.getTemplateSourceResolver()).withEngine(delegate));

        // 4. compileToBytecode()
        var executable = engine.compileToBytecode(template);
        StringWriter sw = new StringWriter();
        executable.render(new TemplateContext(model).withResolver(engine.getTemplateSourceResolver()).withEngine(delegate), sw, engine);
        String res4 = sw.toString();

        assertEquals(res1, res2, "render() vs renderString() mismatch");
        assertEquals(res1, res3, "compile() vs renderString() mismatch");
        assertEquals(res1, res4, "compileToBytecode() vs renderString() mismatch");
    }
}
