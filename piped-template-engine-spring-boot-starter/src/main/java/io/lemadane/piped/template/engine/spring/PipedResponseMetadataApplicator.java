package io.lemadane.piped.template.engine.spring;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

public class PipedResponseMetadataApplicator {

    public PipedResponseMetadataApplicator() {
    }

    public void apply(Map<String, Object> metadata, HttpServletResponse response) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        if (metadata.containsKey("contentType")) {
            response.setContentType(String.valueOf(metadata.get("contentType")));
        }

        if (metadata.containsKey("cacheControl")) {
            response.setHeader("Cache-Control", String.valueOf(metadata.get("cacheControl")));
        } else if (metadata.containsKey("cache-control")) {
            response.setHeader("Cache-Control", String.valueOf(metadata.get("cache-control")));
        } else if (metadata.containsKey("cache")) {
            response.setHeader("Cache-Control", String.valueOf(metadata.get("cache")));
        }

        if (metadata.containsKey("hxTrigger")) {
            response.setHeader("HX-Trigger", String.valueOf(metadata.get("hxTrigger")));
        }

        if (metadata.containsKey("hxRedirect")) {
            response.setHeader("HX-Redirect", String.valueOf(metadata.get("hxRedirect")));
        }

        if (metadata.containsKey("hxPushUrl")) {
            response.setHeader("HX-Push-Url", String.valueOf(metadata.get("hxPushUrl")));
        }

        if (metadata.containsKey("hxRefresh")) {
            Object refresh = metadata.get("hxRefresh");
            if (refresh instanceof Boolean b && b) {
                response.setHeader("HX-Refresh", "true");
            } else {
                response.setHeader("HX-Refresh", String.valueOf(refresh));
            }
        }
    }
}
