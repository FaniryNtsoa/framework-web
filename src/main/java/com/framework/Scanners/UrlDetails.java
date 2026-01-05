package com.framework.Scanners;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.framework.annotation.HttpMethodType;

/**
 * Minimal descriptor of a routed URL. A route belongs to a single controller class but can
 * expose several handler methods in that class.
 */
public final class UrlDetails {

    private final Class<?> controllerClass;
    private final String template;
    private final String normalisedPath;
    private final List<String> parameterNames;
    private final List<HandlerMethod> handlerMethods;
    private final Pattern dynamicPattern; // null when the path is static

    public UrlDetails(Class<?> controllerClass, String template) {
        if (controllerClass == null) {
            throw new IllegalArgumentException("controllerClass is required");
        }

        this.controllerClass = controllerClass;
        this.template = template == null ? "/" : template.trim();
        this.normalisedPath = normalisePath(this.template);
        this.parameterNames = new ArrayList<>();
        this.handlerMethods = new ArrayList<>();
        this.dynamicPattern = buildPattern(normalisedPath, parameterNames);
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public String getTemplate() {
        return template;
    }

    public String getNormalisedPath() {
        return normalisedPath;
    }

    public boolean isDynamic() {
        return dynamicPattern != null;
    }

    public List<String> getParameterNames() {
        return Collections.unmodifiableList(parameterNames);
    }

    public List<HandlerMethod> getHandlerMethods() {
        return Collections.unmodifiableList(handlerMethods);
    }

    public void addMethodsFrom(UrlDetails other) {
        if (other == null) {
            return;
        }
        if (!controllerClass.equals(other.controllerClass)) {
            throw new IllegalStateException("Conflicting controllers for path " + normalisedPath
                    + ": " + controllerClass.getName() + " vs " + other.controllerClass.getName());
        }
        for (HandlerMethod handler : other.handlerMethods) {
            addHandler(handler.getMethod(), handler.getHttpMethods());
        }
    }

    public void addHandler(Method method, EnumSet<HttpMethodType> httpMethods) {
        if (method == null) {
            return;
        }
        if (!method.getDeclaringClass().equals(controllerClass)) {
            throw new IllegalArgumentException("Handler " + method + " does not belong to " + controllerClass.getName());
        }

        EnumSet<HttpMethodType> supported;
        if (httpMethods == null || httpMethods.isEmpty()) {
            supported = EnumSet.noneOf(HttpMethodType.class);
        } else {
            supported = EnumSet.copyOf(httpMethods);
        }

        HandlerMethod existing = findHandler(method);
        if (existing != null) {
            existing.addHttpMethods(supported);
            return;
        }

        handlerMethods.add(new HandlerMethod(method, supported));
    }

    private HandlerMethod findHandler(Method method) {
        for (HandlerMethod handler : handlerMethods) {
            if (handler.getMethod().equals(method)) {
                return handler;
            }
        }
        return null;
    }

    public List<Method> getMethods() {
        Map<Method, Boolean> unique = new LinkedHashMap<>();
        for (HandlerMethod handler : handlerMethods) {
            unique.putIfAbsent(handler.getMethod(), Boolean.TRUE);
        }
        return Collections.unmodifiableList(new ArrayList<>(unique.keySet()));
    }

    public List<String> match(String requestPath) {
        if (requestPath == null) {
            return null;
        }

        if (dynamicPattern == null) {
            return normalisedPath.equals(requestPath) ? Collections.emptyList() : null;
        }

        Matcher matcher = dynamicPattern.matcher(requestPath);
        if (!matcher.matches()) {
            return null;
        }

        List<String> values = new ArrayList<>(matcher.groupCount());
        for (int i = 1; i <= matcher.groupCount(); i++) {
            values.add(matcher.group(i));
        }
        return values;
    }

    private static String normalisePath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private static Pattern buildPattern(String normalisedPath, List<String> parameterNames) {
        String trimmed = normalisedPath.startsWith("/") ? normalisedPath.substring(1) : normalisedPath;
        if (trimmed.isEmpty()) {
            return null;
        }

        StringBuilder regex = new StringBuilder("^/");
        String[] segments = trimmed.split("/");
        boolean dynamic = false;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                regex.append('/');
                continue;
            }

            if (isDynamicSegment(segment)) {
                dynamic = true;
                String parameterName = segment.substring(1, segment.length() - 1).trim();
                if (parameterName.isEmpty()) {
                    throw new IllegalArgumentException("Dynamic segment name cannot be empty in path: " + normalisedPath);
                }
                parameterNames.add(parameterName);
                regex.append("([^/]+)");
            } else {
                regex.append(Pattern.quote(segment));
            }

            if (i < segments.length - 1) {
                regex.append('/');
            }
        }

        if (!dynamic) {
            parameterNames.clear();
            return null;
        }

        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private static boolean isDynamicSegment(String segment) {
        return segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2;
    }

    public static final class HandlerMethod {
        private final Method method;
        private final EnumSet<HttpMethodType> httpMethods;

        HandlerMethod(Method method, EnumSet<HttpMethodType> httpMethods) {
            this.method = method;
            this.httpMethods = httpMethods == null || httpMethods.isEmpty()
                    ? EnumSet.noneOf(HttpMethodType.class)
                    : EnumSet.copyOf(httpMethods);
        }

        public Method getMethod() {
            return method;
        }

        public EnumSet<HttpMethodType> getHttpMethods() {
            if (httpMethods.isEmpty()) {
                return EnumSet.noneOf(HttpMethodType.class);
            }
            return EnumSet.copyOf(httpMethods);
        }

        public boolean matches(HttpMethodType requestMethod) {
            if (httpMethods.isEmpty()) {
                return true;
            }
            if (requestMethod == null) {
                return false;
            }
            return httpMethods.contains(requestMethod);
        }

        void addHttpMethods(EnumSet<HttpMethodType> additional) {
            if (additional == null || additional.isEmpty()) {
                httpMethods.clear();
                return;
            }
            if (httpMethods.isEmpty()) {
                return;
            }
            httpMethods.addAll(additional);
        }
    }
}
