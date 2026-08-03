package io.lemadane.piped.template.engine;

import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.options.PwaRenderOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PwaEngineDefaultsTest {

    TemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TemplateEngine(Map.of("sample-pwa", "|pwa sw='/sw.js'|"));
    }

    @Test
    @DisplayName("Configured inline mode affects renderString, renderTemplateSource, and renderNamedTemplate")
    void testConfiguredInlineModeAppliesToAllCoreApis() {
        PwaRenderOptions pwaOptions = new PwaRenderOptions(
                PwaRenderOptions.RegistrationMode.INLINE,
                "/configured-register.js",
                false
        );
        engine.setDefaultRenderOptions(new RenderOptions(false, false, pwaOptions));

        String pwaDirective = "|pwa sw='/sw.js'|";

        String renderStringResult = engine.renderString(pwaDirective, Map.of());
        assertTrue(renderStringResult.contains("<script>if('serviceWorker' in navigator)"));
        assertFalse(renderStringResult.contains("src=\"/configured-register.js\""));

        String renderTemplateSourceResult = engine.renderTemplateSource(pwaDirective, Map.of()).html();
        assertTrue(renderTemplateSourceResult.contains("<script>if('serviceWorker' in navigator)"));
        assertFalse(renderTemplateSourceResult.contains("src=\"/configured-register.js\""));

        String renderNamedTemplateResult = engine.renderNamedTemplate("sample-pwa", Map.of()).html();
        assertTrue(renderNamedTemplateResult.contains("<script>if('serviceWorker' in navigator)"));
        assertFalse(renderNamedTemplateResult.contains("src=\"/configured-register.js\""));
    }

    @Test
    @DisplayName("Configured custom external script affects renderString, renderTemplateSource, and renderNamedTemplate")
    void testConfiguredCustomExternalScriptAppliesToAllCoreApis() {
        PwaRenderOptions pwaOptions = new PwaRenderOptions(
                PwaRenderOptions.RegistrationMode.EXTERNAL,
                "/configured-register.js",
                false
        );
        engine.setDefaultRenderOptions(new RenderOptions(false, false, pwaOptions));

        String pwaDirective = "|pwa sw='/sw.js'|";

        String renderStringResult = engine.renderString(pwaDirective, Map.of());
        assertTrue(renderStringResult.contains("<script src=\"/configured-register.js\" data-pte-service-worker=\"/sw.js\" defer></script>"));
        assertFalse(renderStringResult.contains("/pte-assets/pwa-register.js"));

        String renderTemplateSourceResult = engine.renderTemplateSource(pwaDirective, Map.of()).html();
        assertTrue(renderTemplateSourceResult.contains("<script src=\"/configured-register.js\" data-pte-service-worker=\"/sw.js\" defer></script>"));
        assertFalse(renderTemplateSourceResult.contains("/pte-assets/pwa-register.js"));

        String renderNamedTemplateResult = engine.renderNamedTemplate("sample-pwa", Map.of()).html();
        assertTrue(renderNamedTemplateResult.contains("<script src=\"/configured-register.js\" data-pte-service-worker=\"/sw.js\" defer></script>"));
        assertFalse(renderNamedTemplateResult.contains("/pte-assets/pwa-register.js"));
    }

    @Test
    @DisplayName("Configured required nonce throws exception when missing and succeeds when provided across all core APIs")
    void testConfiguredRequiredNonceEnforcedAcrossAllCoreApis() {
        PwaRenderOptions pwaOptions = new PwaRenderOptions(
                PwaRenderOptions.RegistrationMode.INLINE,
                "/configured-register.js",
                true
        );
        engine.setDefaultRenderOptions(new RenderOptions(false, false, pwaOptions));

        String pwaDirectiveWithoutNonce = "|pwa sw='/sw.js'|";
        assertThrows(TemplateSyntaxException.class, () -> engine.renderString(pwaDirectiveWithoutNonce, Map.of()));
        assertThrows(TemplateSyntaxException.class, () -> engine.renderTemplateSource(pwaDirectiveWithoutNonce, Map.of()));
        assertThrows(TemplateSyntaxException.class, () -> engine.renderNamedTemplate("sample-pwa", Map.of()));

        String pwaDirectiveWithNonce = "|pwa sw='/sw.js' nonce='testNonce'|";
        TemplateEngine engineWithNonceTemplate = new TemplateEngine(Map.of("sample-pwa-nonce", pwaDirectiveWithNonce));
        engineWithNonceTemplate.setDefaultRenderOptions(new RenderOptions(false, false, pwaOptions));

        String renderStringResult = engineWithNonceTemplate.renderString(pwaDirectiveWithNonce, Map.of());
        assertTrue(renderStringResult.contains("<script nonce=\"testNonce\">if('serviceWorker' in navigator)"));

        String renderTemplateSourceResult = engineWithNonceTemplate.renderTemplateSource(pwaDirectiveWithNonce, Map.of()).html();
        assertTrue(renderTemplateSourceResult.contains("<script nonce=\"testNonce\">if('serviceWorker' in navigator)"));

        String renderNamedTemplateResult = engineWithNonceTemplate.renderNamedTemplate("sample-pwa-nonce", Map.of()).html();
        assertTrue(renderNamedTemplateResult.contains("<script nonce=\"testNonce\">if('serviceWorker' in navigator)"));
    }

    @Test
    @DisplayName("Explicit RenderOptions passed by caller override engine defaults (both external->inline and inline->external)")
    void testExplicitRenderOptionsOverrideEngineDefaults() {
        PwaRenderOptions externalOptions = new PwaRenderOptions(PwaRenderOptions.RegistrationMode.EXTERNAL, "/ext.js", false);
        PwaRenderOptions inlineOptions = new PwaRenderOptions(PwaRenderOptions.RegistrationMode.INLINE, "/inline.js", false);

        // Case 1: Engine default is external, explicit options is inline
        engine.setDefaultRenderOptions(new RenderOptions(false, false, externalOptions));
        RenderOptions explicitInline = new RenderOptions(false, false, inlineOptions);

        String htmlInline = engine.renderTemplateSource("|pwa sw='/sw.js'|", Map.of(), explicitInline).html();
        assertTrue(htmlInline.contains("<script>if('serviceWorker' in navigator)"));

        // Case 2: Engine default is inline, explicit options is external
        engine.setDefaultRenderOptions(new RenderOptions(false, false, inlineOptions));
        RenderOptions explicitExternal = new RenderOptions(false, false, externalOptions);

        String htmlExternal = engine.renderTemplateSource("|pwa sw='/sw.js'|", Map.of(), explicitExternal).html();
        assertTrue(htmlExternal.contains("<script src=\"/ext.js\" data-pte-service-worker=\"/sw.js\" defer></script>"));
    }

    @Test
    @DisplayName("Template attributes override engine defaults")
    void testTemplateAttributesOverrideEngineDefaults() {
        PwaRenderOptions externalOptions = new PwaRenderOptions(PwaRenderOptions.RegistrationMode.EXTERNAL, "/default.js", false);
        engine.setDefaultRenderOptions(new RenderOptions(false, false, externalOptions));

        String inlineAttributeTemplate = "|pwa sw='/sw.js' mode='inline' nonce='overrideNonce'|";
        String htmlInline = engine.renderString(inlineAttributeTemplate, Map.of());
        assertTrue(htmlInline.contains("<script nonce=\"overrideNonce\">if('serviceWorker' in navigator)"));

        String externalAttributeTemplate = "|pwa sw='/sw.js' mode='external' registration-script='/template-register.js'|";
        String htmlExternal = engine.renderString(externalAttributeTemplate, Map.of());
        assertTrue(htmlExternal.contains("<script src=\"/template-register.js\" data-pte-service-worker=\"/sw.js\" defer></script>"));
    }
}
