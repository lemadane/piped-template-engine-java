package io.lemadane.piped.template.engine.spring.routing;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.res.ClasspathTemplateSourceResolver;
import io.lemadane.piped.template.engine.spring.PipedTemplateViewResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileRoutingIntegrationTest {

    MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    static class TestConfig implements WebMvcConfigurer {

        @Bean
        TemplateEngine templateEngine() {
            return new TemplateEngine(new ClasspathTemplateSourceResolver("classpath:/", ".pte"));
        }

        @Bean
        PipedFileRouteHandlerMapping pipedFileRouteHandlerMapping(TemplateEngine engine) {
            PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(engine);

            mapping.registerFileRoute("/", "pte-routes/+page.pte",
                    new ByteArrayResource("|page title='Home'|\n<h1>Welcome Home</h1>".getBytes()));

            mapping.registerFileRoute("/about", "pte-routes/about/+page.pte",
                    new ByteArrayResource("|page title='About Us' contentType='text/html' cacheControl='max-age=3600'|\n<h1>About Us Page</h1>".getBytes()));

            mapping.registerFileRoute("/posts/{id}", "pte-routes/posts/[id]/+page.pte",
                    new ByteArrayResource("<h1>Post |id|</h1><p>Filter: |filter ?? 'none'|</p>".getBytes()));

            mapping.registerFileRoute("/nested/settings", "pte-routes/nested/settings/+page.pte",
                    new ByteArrayResource("<h1>Settings Page</h1>".getBytes()));

            mapping.registerFileRoute("/protected", "pte-routes/protected/+page.pte",
                    new ByteArrayResource("|page auth=true|\n<h1>Protected Content</h1>".getBytes()));

            mapping.registerFileRoute("/admin", "pte-routes/admin/+page.pte",
                    new ByteArrayResource("|page roles=['ADMIN']|\n<h1>Admin Panel</h1>".getBytes()));

            mapping.registerFileRoute("/error", "pte-routes/error/+page.pte",
                    new ByteArrayResource("|wat invalid directive syntax|".getBytes()));

            mapping.registerPageDataLoader("/posts/{id}", req -> Map.of("filter", "loaded-filter"));

            return mapping;
        }

        @Override
        public void configureViewResolvers(ViewResolverRegistry registry) {
            PipedTemplateViewResolver resolver = new PipedTemplateViewResolver();
            resolver.setPrefix("");
            resolver.setSuffix(".pte");
            registry.viewResolver(resolver);
        }
    }

    @BeforeEach
    void setUp() {
        MockServletContext servletContext = new MockServletContext();
        AnnotationConfigWebApplicationContext wac = new AnnotationConfigWebApplicationContext();
        wac.setServletContext(servletContext);
        wac.register(TestConfig.class);
        wac.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("Root route '/' renders welcome content and metadata title")
    void testRootRoute() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("<h1>Welcome Home</h1>"));
    }

    @Test
    @DisplayName("'/about' route renders custom content-type, cache-control header, and body")
    void testAboutRouteHeaders() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600"))
                .andExpect(content().string("<h1>About Us Page</h1>"));
    }

    @Test
    @DisplayName("'/posts/{id}' renders path variables and page data loader values")
    void testPostRoutePathVarsAndDataLoader() throws Exception {
        mockMvc.perform(get("/posts/42"))
                .andExpect(status().isOk())
                .andExpect(content().string("<h1>Post 42</h1><p>Filter: loaded-filter</p>"));
    }

    @Test
    @DisplayName("'/nested/settings' renders nested route correctly")
    void testNestedSettingsRoute() throws Exception {
        mockMvc.perform(get("/nested/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string("<h1>Settings Page</h1>"));
    }

    @Test
    @DisplayName("Unauthenticated request to auth-protected route returns HTTP 401 Unauthorized")
    void testProtectedUnauthenticated() throws Exception {
        mockMvc.perform(get("/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Unauthorized"));
    }

    @Test
    @DisplayName("User missing required ADMIN role returns HTTP 403 Forbidden")
    void testAdminForbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
    }

    @Test
    @DisplayName("Internal error returns generic HTTP 500 without exposing stack traces")
    void testInternalServerErrorHandling() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(content().string("Internal Server Error"));
    }
}
