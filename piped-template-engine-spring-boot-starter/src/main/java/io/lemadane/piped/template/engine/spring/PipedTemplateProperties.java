package io.lemadane.piped.template.engine.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.pipedtemplate")
public class PipedTemplateProperties {

    String prefix = "classpath:/pte-templates/";
    String suffix = ".pte";
    String contentType = "text/html;charset=UTF-8";
    int order = 20;

    Routing routing = new Routing();
    Pwa pwa = new Pwa();

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }

    public Pwa getPwa() { return pwa; }
    public void setPwa(Pwa pwa) { this.pwa = pwa; }

    public static class Routing {
        boolean failFast = true;

        public boolean isFailFast() { return failFast; }
        public void setFailFast(boolean failFast) { this.failFast = failFast; }
    }

    public static class Pwa {
        String registrationMode = "external";
        String registrationScript = "/pte-assets/pwa-register.js";
        boolean requireNonceForInline = false;

        public String getRegistrationMode() { return registrationMode; }
        public void setRegistrationMode(String registrationMode) { this.registrationMode = registrationMode; }

        public String getRegistrationScript() { return registrationScript; }
        public void setRegistrationScript(String registrationScript) { this.registrationScript = registrationScript; }

        public boolean isRequireNonceForInline() { return requireNonceForInline; }
        public void setRequireNonceForInline(boolean requireNonceForInline) { this.requireNonceForInline = requireNonceForInline; }
    }
}
