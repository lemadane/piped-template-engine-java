package io.lemadane.piped.template.engine.spring;

import io.lemadane.piped.template.engine.TemplateEngine;
import io.lemadane.piped.template.engine.res.ClasspathTemplateSourceResolver;
import io.lemadane.piped.template.engine.res.CompositeTemplateSourceResolver;
import io.lemadane.piped.template.engine.res.FileSystemTemplateSourceResolver;
import io.lemadane.piped.template.engine.res.TemplateSourceResolver;
import io.lemadane.piped.template.engine.spring.routing.PageDataLoader;
import io.lemadane.piped.template.engine.spring.routing.PageLoader;
import io.lemadane.piped.template.engine.spring.routing.PipedFileRouteHandlerMapping;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(TemplateEngine.class)
@EnableConfigurationProperties(PipedTemplateProperties.class)
public class PipedTemplateAutoConfiguration {

    final PipedTemplateProperties properties;

    public PipedTemplateAutoConfiguration(PipedTemplateProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateEngine pipedTemplateEngine() {
        String prefix = properties.getPrefix();
        String suffix = properties.getSuffix();

        TemplateSourceResolver resolver;
        if (prefix != null && prefix.startsWith("classpath:")) {
            resolver = new ClasspathTemplateSourceResolver(prefix, suffix);
        } else if (prefix != null && prefix.startsWith("file:")) {
            resolver = new FileSystemTemplateSourceResolver(prefix, suffix);
        } else {
            resolver = new CompositeTemplateSourceResolver(
                new ClasspathTemplateSourceResolver(prefix != null ? prefix : "classpath:/pte-templates/", suffix),
                new FileSystemTemplateSourceResolver(prefix != null ? prefix : ".", suffix)
            );
        }

        return new TemplateEngine(resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public PipedTemplateViewResolver pipedTemplateViewResolver() {
        PipedTemplateViewResolver resolver = new PipedTemplateViewResolver();
        resolver.setPrefix("");
        resolver.setSuffix(properties.getSuffix());
        resolver.setContentType(properties.getContentType());
        resolver.setOrder(properties.getOrder());
        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean
    public PipedFileRouteHandlerMapping pipedFileRouteHandlerMapping(
            TemplateEngine pipedTemplateEngine,
            ApplicationContext applicationContext) {
        PipedFileRouteHandlerMapping mapping = new PipedFileRouteHandlerMapping(pipedTemplateEngine, properties.getRouting().isFailFast());

        String[] beanNames = applicationContext.getBeanNamesForAnnotation(PageLoader.class);
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            if (bean instanceof PageDataLoader loader) {
                PageLoader annotation = applicationContext.findAnnotationOnBean(beanName, PageLoader.class);
                if (annotation != null) {
                    mapping.registerPageDataLoader(annotation.value(), loader);
                }
            }
        }
        return mapping;
    }
}
