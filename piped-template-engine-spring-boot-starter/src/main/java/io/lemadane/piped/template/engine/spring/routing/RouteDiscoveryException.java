package io.lemadane.piped.template.engine.spring.routing;

public class RouteDiscoveryException extends RuntimeException {
    public RouteDiscoveryException(String message) {
        super(message);
    }

    public RouteDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
