package io.lemadane.piped.template.engine.spring;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

public class PipedPageContext {
    final String requestUri;
    final String queryString;
    final String method;
    final Map<String, String> headers;
    final Map<String, Object> params;
    final Map<String, Object> session;
    final boolean isHTMX;
    final String hxTarget;
    final String hxTrigger;
    final String hxCurrentURL;

    public PipedPageContext(HttpServletRequest request) {
        this.requestUri = request.getRequestURI();
        this.queryString = request.getQueryString();
        this.method = request.getMethod();
        this.isHTMX = "true".equals(request.getHeader("HX-Request"));
        this.hxTarget = request.getHeader("HX-Target");
        this.hxTrigger = request.getHeader("HX-Trigger");
        this.hxCurrentURL = request.getHeader("HX-Current-URL");

        // Extract headers
        Map<String, String> headerMap = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headerMap.put(name, request.getHeader(name));
            }
        }
        this.headers = Collections.unmodifiableMap(headerMap);

        // Extract parameters
        Map<String, Object> paramMap = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                paramMap.put(k, v.length == 1 ? v[0] : v);
            }
        });
        this.params = Collections.unmodifiableMap(paramMap);

        // Extract session attributes
        Map<String, Object> sessionMap = new LinkedHashMap<>();
        var sessionObj = request.getSession(false);
        if (sessionObj != null) {
            Enumeration<String> sessionNames = sessionObj.getAttributeNames();
            while (sessionNames.hasMoreElements()) {
                String name = sessionNames.nextElement();
                sessionMap.put(name, sessionObj.getAttribute(name));
            }
        }
        this.session = Collections.unmodifiableMap(sessionMap);
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getQueryString() {
        return queryString;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Map<String, Object> getSession() {
        return session;
    }

    public boolean isHTMX() {
        return isHTMX;
    }

    public boolean isIsHTMX() {
        return isHTMX;
    }

    public String getHxTarget() {
        return hxTarget;
    }

    public String getHxTrigger() {
        return hxTrigger;
    }

    public String getHxCurrentURL() {
        return hxCurrentURL;
    }
}
