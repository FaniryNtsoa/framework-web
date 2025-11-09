package com.framework.Servlets;

import com.framework.Scanners.ScanControllers;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/*") // intercepte toutes les requêtes
public class FrontServlet extends HttpServlet {

    private static final String CONTROLLERS_PACKAGES_PARAM = "controllers-packages";
    private Map<String, Method> routeRegistry = Collections.emptyMap();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        registerRoutes(config);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = extractPath(req);
        Method handler = routeRegistry.get(path);

        if (handler != null) {
            dispatchToHandler(handler, req, resp);
            return;
        }

        if (serveStaticResource(path, req, resp)) {
            return;
        }

        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Erreur 404 : " + path + " introuvable.");
    }

    private void registerRoutes(ServletConfig config) throws ServletException {
        String packagesDeclaration = config.getInitParameter(CONTROLLERS_PACKAGES_PARAM);
        if (packagesDeclaration == null || packagesDeclaration.isBlank()) {
            packagesDeclaration = getServletContext().getInitParameter(CONTROLLERS_PACKAGES_PARAM);
        }

        if (packagesDeclaration == null || packagesDeclaration.isBlank()) {
            packagesDeclaration = "com"; // valeur par défaut raisonnable
            getServletContext().log("Aucun paramètre '" + CONTROLLERS_PACKAGES_PARAM + "' fourni. Utilisation du package par défaut 'com'.");
        }

        Map<String, Method> discoveredRoutes = new HashMap<>();
        for (String declaredPackage : packagesDeclaration.split(",")) {
            String basePackage = declaredPackage.trim();
            if (basePackage.isEmpty()) {
                continue;
            }

            Map<String, Method> routes = ScanControllers.mapHandlePaths(basePackage);
            for (Map.Entry<String, Method> entry : routes.entrySet()) {
                Method previous = discoveredRoutes.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw new ServletException("Route dupliquée détectée pour le chemin : " + entry.getKey());
                }
            }
        }

        routeRegistry = Collections.unmodifiableMap(discoveredRoutes);
        getServletContext().log("Routes enregistrées : " + routeRegistry.keySet());
    }

    private String extractPath(HttpServletRequest req) {
        String requestUri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String relative = requestUri.substring(contextPath.length());

        if (relative.isEmpty()) {
            return "/";
        }

        return relative.startsWith("/") ? relative : "/" + relative;
    }

    private void dispatchToHandler(Method handler, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Object controllerInstance = instantiateController(handler);
        Object result;

        try {
            result = invokeHandler(handler, controllerInstance, req, resp);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof ServletException servletException) {
                throw servletException;
            }
            if (target instanceof IOException ioException) {
                throw ioException;
            }
            throw new ServletException("Erreur lors de l'exécution du handler", target);
        } catch (ReflectiveOperationException e) {
            throw new ServletException("Impossible d'invoquer le handler " + handler, e);
        }

        if (resp.isCommitted()) {
            return;
        }

        if (result instanceof String viewName) {
            renderView(viewName, req, resp);
        } else if (result != null) {
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().print(result.toString());
        }
    }

    private Object instantiateController(Method handler) throws ServletException {
        Class<?> controllerClass = handler.getDeclaringClass();
        try {
            return controllerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ServletException("Impossible d'instancier le contrôleur " + controllerClass.getName(), e);
        }
    }

    private Object invokeHandler(Method handler, Object controllerInstance, HttpServletRequest req, HttpServletResponse resp)
            throws ReflectiveOperationException {
        handler.setAccessible(true);
        Class<?>[] parameterTypes = handler.getParameterTypes();

        if (parameterTypes.length == 2
                && HttpServletRequest.class.isAssignableFrom(parameterTypes[0])
                && HttpServletResponse.class.isAssignableFrom(parameterTypes[1])) {
            return handler.invoke(controllerInstance, req, resp);
        }

        if (parameterTypes.length == 1 && HttpServletRequest.class.isAssignableFrom(parameterTypes[0])) {
            return handler.invoke(controllerInstance, req);
        }

        if (parameterTypes.length == 0) {
            return handler.invoke(controllerInstance);
        }

        throw new IllegalStateException("Signature de handler non supportée : " + handler);
    }

    private void renderView(String viewName, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (viewName.startsWith("redirect:")) {
            String target = viewName.substring("redirect:".length());
            if (!target.startsWith("/")) {
                target = "/" + target;
            }
            resp.sendRedirect(req.getContextPath() + target);
            return;
        }

        RequestDispatcher dispatcher = req.getRequestDispatcher(viewName);
        if (dispatcher == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Vue introuvable pour le résultat : " + viewName);
            return;
        }

        dispatcher.forward(req, resp);
    }

    private boolean serveStaticResource(String path, HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if ("/".equals(path)) {
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().println("/");
            return true;
        }

        URL resource = getServletContext().getResource(path);
        if (resource == null) {
            return false;
        }

        RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("default");
        if (dispatcher == null) {
            return false;
        }

        dispatcher.forward(req, resp);
        return true;
    }
}
