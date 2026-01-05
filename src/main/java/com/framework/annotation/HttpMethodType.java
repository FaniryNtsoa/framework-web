package com.framework.annotation;

/**
 * Supported HTTP methods for controller handlers.
 */
public enum HttpMethodType {
    GET,
    POST;

    /**
     * Convert a Servlet request method (e.g. "GET") into an HttpMethodType.
     * Returns null when the verb is not supported by the framework.
     */
    public static HttpMethodType fromRequestMethod(String requestMethod) {
        if (requestMethod == null) {
            return null;
        }

        switch (requestMethod.toUpperCase()) {
            case "GET":
                return GET;
            case "POST":
                return POST;
            default:
                return null;
        }
    }
}
