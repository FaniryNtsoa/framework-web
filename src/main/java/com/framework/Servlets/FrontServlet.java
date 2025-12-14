package com.framework.Servlets;

import com.framework.Scanners.ScanControllers;
import com.framework.Scanners.UrlDetails;
import com.framework.annotation.RequestParam;
import com.framework.util.ModelView;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FrontServlet - Sprint 1, 2, 2-bis, 3, 4, 4-bis, 5
 * 
 * Sprint 1: Intercepte toutes les requêtes avec @WebServlet("/*")
 * Sprint 2: Utilise les annotations @Controller et @HandlePath
 * Sprint 2-bis: HashMap pour mapper URL -> Méthode, retourne 404 si non trouvé
 * Sprint 3: Scanning automatique dans init()
 * Sprint 4: Invocation par réflexion et affichage du String retourné
 * Sprint 4-bis: Support de ModelView pour dispatch vers JSP
 * Sprint 5: Transfert des données du ModelView vers request.setAttribute()
 * 
 * IMPORTANT: Le servlet intercepte TOUT, mais il laisse passer les fichiers statiques
 * (HTML, CSS, JS, images) en utilisant getServletContext().getResource()
 * pour vérifier si le fichier existe physiquement dans webapp/
 */
@WebServlet("/")
public class FrontServlet extends HttpServlet {

    private static final String CONTROLLERS_PACKAGES_PARAM = "controllers-packages";
    public static final String ROUTE_REGISTRY_ATTRIBUTE = "framework.routes";
    private Map<String, UrlDetails> routeRegistry = new HashMap<>();
    private List<UrlDetails> dynamicRoutes = new ArrayList<>();

    /**
     * Sprint 3: Init() effectue le scanning au démarrage
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        
        // Récupérer le package à scanner depuis web.xml
        String packagesDeclaration = config.getInitParameter(CONTROLLERS_PACKAGES_PARAM);
        if (packagesDeclaration == null || packagesDeclaration.isBlank()) {
            packagesDeclaration = getServletContext().getInitParameter(CONTROLLERS_PACKAGES_PARAM);
        }

        if (packagesDeclaration == null || packagesDeclaration.isBlank()) {
            throw new ServletException("Paramètre '" + CONTROLLERS_PACKAGES_PARAM + "' non défini dans web.xml");
        }

        // Sprint 2-bis: Scanner et préparer l'enregistrement des routes
        routeRegistry = ScanControllers.mapHandlePaths(packagesDeclaration.trim());

        dynamicRoutes = new ArrayList<>();
        for (UrlDetails details : routeRegistry.values()) {
            if (details.isDynamic()) {
                dynamicRoutes.add(details);
            }
        }

        // Sprint 3: Stocker la HashMap dans le ServletContext pour que les contrôleurs puissent y accéder
        getServletContext().setAttribute(ROUTE_REGISTRY_ATTRIBUTE, routeRegistry);
        
        // Log pour debug
        getServletContext().log("Routes enregistrees : " + routeRegistry.keySet());
        getServletContext().log("Nombre de routes : " + routeRegistry.size());
    }

    /**
     * Sprint 1: Intercepter les requêtes GET
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    /**
     * Sprint 1: Intercepter les requêtes POST
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    /**
     * Traiter toutes les requêtes
     */
    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Extraire le chemin de la requête
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.isEmpty()) {
            path = "/";
        }

        // Sprint 2-bis: Chercher d'abord une correspondance exacte parmi les routes scannées
        UrlDetails exactMatch = routeRegistry.get(path);
        if (exactMatch != null && invokeMatchingHandler(exactMatch, Collections.emptyList(), req, resp)) {
            return;
        }

        // Sprint 3-ter: Rechercher ensuite une route dynamique avec segments {variable}
        for (UrlDetails candidate : dynamicRoutes) {
            List<String> extractedValues = candidate.match(path);
            if (extractedValues == null) {
                continue;
            }
            if (invokeMatchingHandler(candidate, extractedValues, req, resp)) {
                return;
            }
        }

        // Sprint 2-bis: Erreur 404 si ni contrôleur ni fichier statique trouvé
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.setContentType("text/plain;charset=UTF-8");
        resp.getWriter().println("Erreur 404 : " + path + " introuvable.");
    }

    /**
     * Parcours les handlers associés à une URL et exécute le premier dont la signature
     * est compatible avec les paramètres préparés (requête, réponse, variables dynamiques).
     */
    private boolean invokeMatchingHandler(UrlDetails urlDetails, List<String> pathVariables,
                                          HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        for (Method handler : urlDetails.getMethods()) {
            Object[] arguments;
            try {
                arguments = resolveArguments(urlDetails, handler, pathVariables, req, resp);
            } catch (UnsupportedOperationException unsupported) {
                throw new ServletException("Type de paramètre non supporté : " + handler, unsupported);
            }

            if (arguments == null) {
                continue;
            }

            executeHandler(handler, arguments, req, resp);
            return true;
        }

        return false;
    }

    private Object[] resolveArguments(UrlDetails urlDetails, Method handler, List<String> pathVariables,
                                      HttpServletRequest req, HttpServletResponse resp) {
        java.lang.reflect.Parameter[] parameters = handler.getParameters();
        List<Object> arguments = new ArrayList<>(parameters.length);
        // Sprint 6-ter: extraire les segments dynamiques {variable} de l'URL
        List<PathVariableValue> dynamicSegments = buildPathVariableValues(urlDetails.getParameterNames(), pathVariables);
        Map<String, String[]> requestParams = req.getParameterMap();

        for (java.lang.reflect.Parameter parameter : parameters) {
            Class<?> paramType = parameter.getType();

            if (HttpServletRequest.class.isAssignableFrom(paramType)) {
                arguments.add(req);
                continue;
            }

            if (HttpServletResponse.class.isAssignableFrom(paramType)) {
                arguments.add(resp);
                continue;
            }

            // Sprint 6-bis: associer un nom explicite via @RequestParam
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            String annotatedName = null;
            if (requestParam != null) {
                annotatedName = requestParam.value();
                if (annotatedName != null) {
                    annotatedName = annotatedName.trim();
                    if (annotatedName.isEmpty()) {
                        annotatedName = null;
                    }
                }
            }

            String paramName = parameter.getName();
            String rawValue = null;

            String[] candidateNames;
            if (annotatedName != null && paramName != null && !annotatedName.equals(paramName)) {
                candidateNames = new String[]{annotatedName, paramName};
            } else if (annotatedName != null) {
                candidateNames = new String[]{annotatedName};
            } else if (paramName != null) {
                candidateNames = new String[]{paramName};
            } else {
                candidateNames = new String[0];
            }

            // Sprint 6-ter: privilégier la correspondance par nom sur les segments d'URL
            for (String candidate : candidateNames) {
                PathVariableValue matched = consumePathVariableByName(dynamicSegments, candidate);
                if (matched != null) {
                    rawValue = matched.value();
                    break;
                }
            }

            // Sprint 6: rechercher ensuite les paramètres dans la query string / formulaire
            if (rawValue == null && candidateNames.length > 0) {
                for (String candidate : candidateNames) {
                    if (candidate == null) {
                        continue;
                    }
                    String[] candidates = requestParams.get(candidate);
                    if (candidates != null && candidates.length > 0) {
                        rawValue = candidates[0];
                        break;
                    }
                }
            }

            // Sprint 6-ter: si aucun @RequestParam, consommer le prochain segment dynamique
            if (rawValue == null && requestParam == null) {
                PathVariableValue byOrder = consumeFirstPathVariable(dynamicSegments);
                if (byOrder != null) {
                    rawValue = byOrder.value();
                }
            }

            if (rawValue == null) {
                arguments.add(defaultValueFor(paramType));
                continue;
            }

            if (rawValue.isEmpty()) {
                arguments.add(emptyValueFor(paramType));
                continue;
            }

            try {
                arguments.add(convertParameterValue(rawValue, paramType));
            } catch (IllegalArgumentException conversionFailure) {
                return null;
            }
        }

        if (!dynamicSegments.isEmpty()) {
            for (PathVariableValue segment : dynamicSegments) {
                if (!segment.isUsed()) {
                    return null;
                }
            }
        }

        return arguments.toArray();
    }

    private static List<PathVariableValue> buildPathVariableValues(List<String> names, List<String> values) {
        List<PathVariableValue> segments = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return segments;
        }

        int nameCount = names == null ? 0 : names.size();
        for (int i = 0; i < values.size(); i++) {
            String segmentName = i < nameCount ? names.get(i) : null;
            segments.add(new PathVariableValue(segmentName, values.get(i)));
        }
        return segments;
    }

    private static PathVariableValue consumePathVariableByName(List<PathVariableValue> segments, String candidate) {
        if (segments == null || segments.isEmpty() || candidate == null) {
            return null;
        }
        for (PathVariableValue segment : segments) {
            if (segment.isUsed()) {
                continue;
            }
            if (segment.matches(candidate)) {
                segment.markUsed();
                return segment;
            }
        }
        return null;
    }

    private static PathVariableValue consumeFirstPathVariable(List<PathVariableValue> segments) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        for (PathVariableValue segment : segments) {
            if (!segment.isUsed()) {
                segment.markUsed();
                return segment;
            }
        }
        return null;
    }

    private Object defaultValueFor(Class<?> targetType) {
        if (!targetType.isPrimitive()) {
            return null;
        }
        if (targetType.equals(boolean.class)) {
            return Boolean.FALSE;
        }
        if (targetType.equals(char.class)) {
            return Character.valueOf('\0');
        }
        if (targetType.equals(byte.class)) {
            return Byte.valueOf((byte) 0);
        }
        if (targetType.equals(short.class)) {
            return Short.valueOf((short) 0);
        }
        if (targetType.equals(int.class)) {
            return Integer.valueOf(0);
        }
        if (targetType.equals(long.class)) {
            return Long.valueOf(0L);
        }
        if (targetType.equals(float.class)) {
            return Float.valueOf(0F);
        }
        if (targetType.equals(double.class)) {
            return Double.valueOf(0D);
        }
        return null;
    }

    private Object emptyValueFor(Class<?> targetType) {
        if (targetType.equals(String.class)) {
            return "";
        }
        return defaultValueFor(targetType);
    }

    private Object convertParameterValue(String value, Class<?> targetType) {
        if (targetType.equals(String.class)) {
            return value;
        }

        if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
            return Integer.parseInt(value);
        }
        if (targetType.equals(long.class) || targetType.equals(Long.class)) {
            return Long.parseLong(value);
        }
        if (targetType.equals(double.class) || targetType.equals(Double.class)) {
            return Double.parseDouble(value);
        }
        if (targetType.equals(float.class) || targetType.equals(Float.class)) {
            return Float.parseFloat(value);
        }
        if (targetType.equals(boolean.class) || targetType.equals(Boolean.class)) {
            return Boolean.parseBoolean(value);
        }
        if (targetType.equals(short.class) || targetType.equals(Short.class)) {
            return Short.parseShort(value);
        }
        if (targetType.equals(byte.class) || targetType.equals(Byte.class)) {
            return Byte.parseByte(value);
        }
        if (targetType.equals(char.class) || targetType.equals(Character.class)) {
            if (value.length() != 1) {
                throw new IllegalArgumentException("Impossible de convertir en char : " + value);
            }
            return value.charAt(0);
        }
        if (targetType.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) targetType.asSubclass(Enum.class);
            try {
                return Enum.valueOf((Class) enumType, value);
            } catch (IllegalArgumentException ex) {
                for (Enum<?> constant : enumType.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(value)) {
                        return constant;
                    }
                }
                throw ex;
            }
        }

        if (targetType.equals(java.math.BigDecimal.class)) {
            return new java.math.BigDecimal(value);
        }
        if (targetType.equals(java.math.BigInteger.class)) {
            return new java.math.BigInteger(value);
        }
        if (targetType.equals(java.util.UUID.class)) {
            return java.util.UUID.fromString(value);
        }

        if (targetType.equals(java.time.LocalDate.class)) {
            return java.time.LocalDate.parse(value);
        }
        if (targetType.equals(java.time.LocalDateTime.class)) {
            return java.time.LocalDateTime.parse(value);
        }
        if (targetType.equals(java.time.LocalTime.class)) {
            return java.time.LocalTime.parse(value);
        }
        if (targetType.equals(java.time.OffsetDateTime.class)) {
            return java.time.OffsetDateTime.parse(value);
        }
        if (targetType.equals(java.time.Instant.class)) {
            return java.time.Instant.parse(value);
        }
        if (targetType.equals(java.util.Date.class)) {
            try {
                return java.util.Date.from(java.time.Instant.parse(value));
            } catch (java.time.format.DateTimeParseException ignored) {
                java.time.LocalDate localDate = java.time.LocalDate.parse(value);
                return java.util.Date.from(localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
            }
        }

        throw new UnsupportedOperationException("Type non supporté : " + targetType.getName());
    }

    private static final class PathVariableValue {
        private final String name;
        private final String value;
        private boolean used;

        PathVariableValue(String name, String value) {
            this.name = name == null ? null : name.trim();
            this.value = value;
        }

        boolean matches(String candidate) {
            if (candidate == null || name == null) {
                return false;
            }
            return name.equalsIgnoreCase(candidate.trim());
        }

        boolean isUsed() {
            return used;
        }

        void markUsed() {
            this.used = true;
        }

        String value() {
            return value;
        }
    }

    private void executeHandler(Method handler, Object[] arguments,
                                HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            Class<?> controllerClass = handler.getDeclaringClass();
            Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
            handler.setAccessible(true);
            Object result = handler.invoke(controllerInstance, arguments);
            handleInvocationResult(result, req, resp);
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'invocation du handler : " + handler, e);
        }
    }

    private void handleInvocationResult(Object result, HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (result instanceof ModelView && !resp.isCommitted()) {
            ModelView modelView = (ModelView) result;
            String viewPath = modelView.getVue();

            if (viewPath == null || viewPath.isBlank()) {
                throw new ServletException("ModelView.getVue() retourne null ou vide");
            }

            Map<String, Object> data = modelView.getData();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }

            RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
            dispatcher.forward(req, resp);
        } else if (result instanceof String && !resp.isCommitted()) {
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().print(result);
        }
    }
}
