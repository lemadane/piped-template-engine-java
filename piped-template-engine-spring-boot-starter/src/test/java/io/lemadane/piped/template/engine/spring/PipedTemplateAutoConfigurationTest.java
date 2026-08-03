package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PipedTemplateAutoConfigurationTest {

    final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PipedTemplateAutoConfiguration.class));

    @Test
    void defaultConfiguration_registersBeans() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TemplateEngine.class);
            assertThat(context).hasSingleBean(PipedTemplateViewResolver.class);

            PipedTemplateViewResolver resolver = context.getBean(PipedTemplateViewResolver.class);
            assertThat(resolver.getSuffix()).isEqualTo(".pte");
        });
    }

    @Test
    void customProperties_areApplied() {
        this.contextRunner
                .withPropertyValues(
                        "spring.pipedtemplate.prefix=custom/templates",
                        "spring.pipedtemplate.suffix=.html",
                        "spring.pipedtemplate.order=50"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(TemplateEngine.class);
                    assertThat(context).hasSingleBean(PipedTemplateViewResolver.class);

                    PipedTemplateViewResolver resolver = context.getBean(PipedTemplateViewResolver.class);
                    assertThat(resolver.getSuffix()).isEqualTo(".html");
                    assertThat(resolver.getOrder()).isEqualTo(50);
                });
    }
}
