package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.spring.routing.PipedFileRouteHandlerMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PipedTemplateStarterIntegrationTest {

    final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PipedTemplateAutoConfiguration.class));

    @Test
    @DisplayName("Auto-configuration creates default beans when enabled")
    void defaultBeansCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TemplateEngine.class);
            assertThat(context).hasSingleBean(PipedTemplateViewResolver.class);
            assertThat(context).hasSingleBean(PipedFileRouteHandlerMapping.class);
            assertThat(context).hasSingleBean(PipedTemplateProperties.class);
        });
    }

    @Test
    @DisplayName("Auto-configuration respects custom prefix and suffix properties")
    void customPropertiesBound() {
        contextRunner.withPropertyValues(
                "spring.pipedtemplate.prefix=classpath:/custom-templates/",
                "spring.pipedtemplate.suffix=.custom"
        ).run(context -> {
            PipedTemplateProperties properties = context.getBean(PipedTemplateProperties.class);
            assertThat(properties.getPrefix()).isEqualTo("classpath:/custom-templates/");
            assertThat(properties.getSuffix()).isEqualTo(".custom");
        });
    }
}
