package com.framework.Servlets;

import com.framework.Scanners.ScanControllers;
import com.framework.Scanners.UrlDetails;
import com.framework.Scanners.UrlDetails.HandlerMethod;
import com.framework.annotation.HttpMethodType;
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
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        HttpMethodType requestMethod = HttpMethodType.fromRequestMethod(req.getMethod());

        for (HandlerMethod handlerMethod : urlDetails.getHandlerMethods()) {
            if (!handlerMethod.matches(requestMethod)) {
                continue;
            }

            Method handler = handlerMethod.getMethod();
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

            if (Map.class.isAssignableFrom(paramType)) {
                arguments.add(buildRequestParameterMap(parameter, requestParams));
                continue;
            }

            // Sprint 6-bis: associer un nom explicite via @RequestParam
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            String[] candidateNames = resolveCandidateNames(parameter, requestParam, requestParams);

            if (isComplexParameterType(paramType)) {
                Object complexArgument = bindComplexObject(paramType, candidateNames, requestParams);
                if (complexArgument != null) {
                    arguments.add(complexArgument);
                    continue;
                }
            }

            String rawValue = null;

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

    private String[] resolveCandidateNames(java.lang.reflect.Parameter parameter,
                                          RequestParam requestParam,
                                          Map<String, String[]> requestParams) {
        Set<String> names = new LinkedHashSet<>();
        if (requestParam != null) {
            String annotated = normaliseCandidate(requestParam.value());
            if (annotated != null) {
                names.add(annotated);
            }
        }

        if (parameter.isNamePresent()) {
            String paramName = normaliseCandidate(parameter.getName());
            if (paramName != null) {
                names.add(paramName);
            }
        }

        if (names.isEmpty()) {
            String inferred = inferRootName(parameter, requestParams);
            if (inferred != null) {
                names.add(inferred);
            }
        }

        return names.toArray(new String[0]);
    }

    private String inferRootName(java.lang.reflect.Parameter parameter, Map<String, String[]> requestParams) {
        if (requestParams == null || requestParams.isEmpty()) {
            return null;
        }

        Set<String> roots = new LinkedHashSet<>();
        for (String key : requestParams.keySet()) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            int dotIndex = key.indexOf('.');
            int bracketIndex = key.indexOf('[');

            int separatorIndex = -1;
            if (dotIndex >= 0) {
                separatorIndex = dotIndex;
            }
            if (bracketIndex >= 0 && (separatorIndex < 0 || bracketIndex < separatorIndex)) {
                separatorIndex = bracketIndex;
            }

            if (separatorIndex < 0) {
                continue;
            }

            String candidate = key.substring(0, separatorIndex).trim();
            if (!candidate.isEmpty()) {
                roots.add(candidate);
            }
        }

        if (roots.size() == 1) {
            return roots.iterator().next();
        }

        if (roots.isEmpty() && requestParams.size() == 1) {
            String onlyKey = normaliseCandidate(requestParams.keySet().iterator().next());
            if (onlyKey != null) {
                return onlyKey;
            }
        }

        if (roots.size() > 1) {
            String parameterTypeCandidate = decapitalize(parameter.getType().getSimpleName());
            if (roots.contains(parameterTypeCandidate)) {
                return parameterTypeCandidate;
            }
        }

        return null;
    }

    private String decapitalize(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        if (source.length() == 1) {
            return source.toLowerCase();
        }
        if (Character.isLowerCase(source.charAt(0))) {
            return source;
        }
        char[] chars = source.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    private String normaliseCandidate(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isComplexParameterType(Class<?> paramType) {
        if (paramType == null) {
            return false;
        }
        if (HttpServletRequest.class.isAssignableFrom(paramType)
                || HttpServletResponse.class.isAssignableFrom(paramType)) {
            return false;
        }
        if (Map.class.isAssignableFrom(paramType) || Collection.class.isAssignableFrom(paramType)) {
            return false;
        }
        if (paramType.isArray() || paramType.isPrimitive()) {
            return false;
        }
        if (isSimpleValueType(paramType)) {
            return false;
        }
        if (paramType.getPackageName().startsWith("java.")) {
            return false;
        }
        return true;
    }

    private Object bindComplexObject(Class<?> targetType, String[] candidateNames,
                                     Map<String, String[]> requestParams) {
        Map<String, String[]> prefixedParams = collectPrefixedParameters(candidateNames, requestParams);
        if (prefixedParams.isEmpty()) {
            return null;
        }

        Object instance = instantiateClass(targetType);
        for (Map.Entry<String, String[]> entry : prefixedParams.entrySet()) {
            String propertyPath = entry.getKey();
            if (propertyPath == null || propertyPath.isEmpty()) {
                continue;
            }
            assignPropertyValue(instance, propertyPath, entry.getValue());
        }
        return instance;
    }

    private Map<String, String[]> collectPrefixedParameters(String[] candidateNames,
                                                            Map<String, String[]> requestParams) {
        Map<String, String[]> collected = new LinkedHashMap<>();
        if (candidateNames == null || candidateNames.length == 0 || requestParams.isEmpty()) {
            return collected;
        }

        for (String candidate : candidateNames) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            String dotPrefix = candidate + ".";
            String bracketPrefix = candidate + "[";

            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }

                if (key.equals(candidate)) {
                    collected.putIfAbsent("", entry.getValue());
                    continue;
                }

                if (key.startsWith(dotPrefix)) {
                    collected.putIfAbsent(key.substring(dotPrefix.length()), entry.getValue());
                    continue;
                }

                if (key.startsWith(bracketPrefix)) {
                    collected.putIfAbsent(key.substring(candidate.length()), entry.getValue());
                }
            }
        }

        return collected;
    }

    private void assignPropertyValue(Object target, String propertyPath, String[] values) {
        List<PathSegment> segments = parsePathSegments(propertyPath);
        if (segments.isEmpty()) {
            return;
        }

        try {
            applySegments(target, segments, 0, values);
        } catch (ReflectiveOperationException ex) {
            throw new UnsupportedOperationException(
                    "Impossible de binder la propriété '" + propertyPath + "' pour "
                            + target.getClass().getName(), ex);
        }
    }

    private List<PathSegment> parsePathSegments(String path) {
        List<PathSegment> segments = new ArrayList<>();
        if (path == null) {
            return segments;
        }

        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch == '.' && depth == 0) {
                if (current.length() > 0) {
                    segments.add(parsePathSegment(current.toString()));
                    current.setLength(0);
                }
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth = Math.max(0, depth - 1);
            }
            current.append(ch);
        }

        if (current.length() > 0) {
            segments.add(parsePathSegment(current.toString()));
        }

        return segments;
    }

    private PathSegment parsePathSegment(String token) {
        if (token == null) {
            return new PathSegment("", List.of());
        }

        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return new PathSegment("", List.of());
        }

        List<Integer> indexes = new ArrayList<>();
        int bracketIndex = trimmed.indexOf('[');
        String name = bracketIndex < 0 ? trimmed : trimmed.substring(0, bracketIndex).trim();
        int cursor = bracketIndex;
        while (cursor >= 0 && cursor < trimmed.length()) {
            int closing = trimmed.indexOf(']', cursor);
            if (closing < 0) {
                throw new IllegalArgumentException("Segment de propriété invalide: " + token);
            }
            String indexText = trimmed.substring(cursor + 1, closing).trim();
            if (indexText.isEmpty()) {
                throw new IllegalArgumentException("Index vide dans le segment: " + token);
            }
            indexes.add(Integer.parseInt(indexText));
            cursor = trimmed.indexOf('[', closing + 1);
        }

        return new PathSegment(name, indexes);
    }

    private void applySegments(Object currentTarget, List<PathSegment> segments, int position, String[] values)
            throws ReflectiveOperationException {
        PathSegment segment = segments.get(position);
        boolean last = position == segments.size() - 1;

        if (segment.getName().isEmpty()) {
            throw new UnsupportedOperationException("Nom de propriété vide dans la chaîne de binding");
        }

        Field field = findField(currentTarget.getClass(), segment.getName());
        if (field == null) {
            throw new UnsupportedOperationException(
                    "Propriété '" + segment.getName() + "' introuvable dans "
                            + currentTarget.getClass().getName());
        }

        Class<?> fieldType = field.getType();
        Object currentValue = field.get(currentTarget);

        if (!segment.getIndexes().isEmpty()) {
            if (segment.getIndexes().size() > 1) {
                throw new UnsupportedOperationException("Les indices multiples ne sont pas supportés pour "
                        + segment.getName());
            }

            if (!Collection.class.isAssignableFrom(fieldType)) {
                throw new UnsupportedOperationException("La propriété '" + segment.getName()
                        + "' doit être un java.util.Collection pour utiliser un index");
            }

            List<Object> list = ensureListInstance(currentTarget, field, currentValue, fieldType);
            int index = segment.getIndexes().get(0);
            ensureListSize(list, index);

            Type elementTypeToken = extractTypeArgument(field.getGenericType(), 0);
            Class<?> elementType = resolveClass(elementTypeToken);
            if (elementType == null || elementType.equals(Object.class)) {
                elementType = String.class;
            }

            if (last) {
                Object converted = convertValueArray(values, elementType);
                list.set(index, converted);
                return;
            }

            Object nested = list.get(index);
            if (nested == null) {
                nested = instantiateClass(elementType);
                list.set(index, nested);
            }

            applySegments(nested, segments, position + 1, values);
            return;
        }

        if (last) {
            if (Collection.class.isAssignableFrom(fieldType)) {
                Collection<Object> collection = instantiateCollection(fieldType);
                Type elementTypeToken = extractTypeArgument(field.getGenericType(), 0);
                Class<?> elementType = resolveClass(elementTypeToken);
                if (elementType == null || elementType.equals(Object.class)) {
                    elementType = String.class;
                }
                for (String raw : values) {
                    collection.add(convertIndividualValue(raw, elementType));
                }
                field.set(currentTarget, collection);
                return;
            }

            if (fieldType.isArray()) {
                Class<?> componentType = fieldType.getComponentType();
                Object array = Array.newInstance(componentType, values.length);
                for (int i = 0; i < values.length; i++) {
                    Array.set(array, i, convertIndividualValue(values[i], componentType));
                }
                field.set(currentTarget, array);
                return;
            }

            field.set(currentTarget, convertIndividualValue(singleValue(values), fieldType));
            return;
        }

        Object nested = currentValue;
        if (nested == null) {
            nested = instantiateClass(fieldType);
            field.set(currentTarget, nested);
        }

        applySegments(nested, segments, position + 1, values);
    }

    private Field findField(Class<?> type, String name) {
        Class<?> search = type;
        while (search != null && !Object.class.equals(search)) {
            try {
                Field field = search.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                search = search.getSuperclass();
            }
        }
        return null;
    }

    private Object instantiateClass(Class<?> type) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new UnsupportedOperationException("Impossible d'instancier le type " + type.getName());
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new UnsupportedOperationException("Impossible d'instancier le type " + type.getName(), e);
        }
    }

    private List<Object> ensureListInstance(Object owner, Field field, Object currentValue, Class<?> fieldType)
            throws IllegalAccessException {
        if (currentValue instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> existing = (List<Object>) currentValue;
            return existing;
        }

        Collection<?> base;
        if (currentValue instanceof Collection<?>) {
            base = new ArrayList<>((Collection<?>) currentValue);
        } else {
            base = instantiateCollection(fieldType);
        }

        if (!(base instanceof List<?>)) {
            throw new UnsupportedOperationException("La collection '" + field.getName()
                    + "' doit implémenter java.util.List pour supporter l'indexation");
        }

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) base;
        field.set(owner, list);
        return list;
    }

    private void ensureListSize(List<Object> list, int index) {
        while (list.size() <= index) {
            list.add(null);
        }
    }

    private Object convertValueArray(String[] values, Class<?> targetType) {
        if (targetType == null || targetType.equals(Object.class)) {
            return singleValue(values);
        }
        if (targetType.equals(String.class)) {
            return singleValue(values);
        }
        if (isSimpleValueType(targetType) || targetType.isEnum()) {
            return convertIndividualValue(singleValue(values), targetType);
        }
        throw new UnsupportedOperationException("Type complexe non supporté pour la conversion de liste: "
                + targetType.getName());
    }

    private Object convertIndividualValue(String raw, Class<?> targetType) {
        if (targetType == null || targetType.equals(Object.class)) {
            return raw;
        }
        if (targetType.equals(String.class)) {
            return raw == null ? "" : raw;
        }
        if (raw == null) {
            return defaultValueFor(targetType);
        }
        if (raw.isEmpty()) {
            return emptyValueFor(targetType);
        }
        if (isSimpleValueType(targetType) || targetType.isEnum()) {
            return convertParameterValue(raw, targetType);
        }
        throw new UnsupportedOperationException("Impossible de convertir la valeur vers le type "
                + targetType.getName());
    }

    private String singleValue(String[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private boolean isSimpleValueType(Class<?> type) {
        if (type == null) {
            return true;
        }
        if (type.isPrimitive()) {
            return true;
        }
        if (type.isEnum()) {
            return true;
        }
        return SIMPLE_VALUE_TYPES.contains(type);
    }

    private static final Set<Class<?>> SIMPLE_VALUE_TYPES = Set.of(
            String.class,
            Integer.class,
            Long.class,
            Short.class,
            Byte.class,
            Double.class,
            Float.class,
            Boolean.class,
            Character.class,
            java.math.BigDecimal.class,
            java.math.BigInteger.class,
            java.util.UUID.class,
            java.time.LocalDate.class,
            java.time.LocalDateTime.class,
            java.time.LocalTime.class,
            java.time.OffsetDateTime.class,
            java.time.Instant.class,
            java.util.Date.class
    );

    private static final class PathSegment {
        private final String name;
        private final List<Integer> indexes;

        PathSegment(String name, List<Integer> indexes) {
            this.name = name == null ? "" : name.trim();
            this.indexes = indexes == null ? List.of() : List.copyOf(indexes);
        }

        String getName() {
            return name;
        }

        List<Integer> getIndexes() {
            return indexes;
        }
    }

    private Object buildRequestParameterMap(java.lang.reflect.Parameter parameter,
                                            Map<String, String[]> requestParams) {
        Class<?> declaredType = parameter.getType();
        Map<Object, Object> target = instantiateMap(declaredType);

        Type parameterType = parameter.getParameterizedType();
        Type keyTypeToken = extractTypeArgument(parameterType, 0);
        Type valueTypeToken = extractTypeArgument(parameterType, 1);

        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            Object key = convertMapKey(entry.getKey(), keyTypeToken);
            Object value = convertMapValue(entry.getValue(), valueTypeToken);
            target.put(key, value);
        }

        return target;
    }

    private Map<Object, Object> instantiateMap(Class<?> mapType) {
        if (mapType == null || mapType.isInterface() || Modifier.isAbstract(mapType.getModifiers())) {
            return new LinkedHashMap<>();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> instance = (Map<Object, Object>) mapType.getDeclaredConstructor().newInstance();
            return instance;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Object convertMapKey(String key, Type keyTypeToken) {
        Class<?> keyType = resolveClass(keyTypeToken);
        if (keyType == null || keyType.equals(Object.class) || keyType.equals(String.class)) {
            return key;
        }
        return convertParameterValue(key, keyType);
    }

    private Object convertMapValue(String[] rawValues, Type valueTypeToken) {
        if (rawValues == null) {
            return null;
        }

        Class<?> rawClass = resolveClass(valueTypeToken);

        if (rawClass == null || rawClass.equals(Object.class)) {
            if (rawValues.length == 0) {
                return null;
            }
            if (rawValues.length == 1) {
                return rawValues[0];
            }
            List<String> multiple = new ArrayList<>(rawValues.length);
            Collections.addAll(multiple, rawValues);
            return multiple;
        }

        if (rawClass.isArray()) {
            Class<?> component = rawClass.getComponentType();
            Object array = Array.newInstance(component, rawValues.length);
            for (int i = 0; i < rawValues.length; i++) {
                Object element = rawValues[i] == null || rawValues[i].isEmpty()
                        ? emptyValueFor(component)
                        : convertParameterValue(rawValues[i], component);
                Array.set(array, i, element);
            }
            return array;
        }

        if (Collection.class.isAssignableFrom(rawClass)) {
            Collection<Object> collection = instantiateCollection(rawClass);
            Type elementTypeToken = extractTypeArgument(valueTypeToken, 0);
            Class<?> elementType = resolveClass(elementTypeToken);
            for (String value : rawValues) {
                if (value == null || value.isEmpty()) {
                    collection.add(emptyValueFor(elementType == null ? String.class : elementType));
                } else if (elementType == null || elementType.equals(Object.class) || elementType.equals(String.class)) {
                    collection.add(value);
                } else {
                    collection.add(convertParameterValue(value, elementType));
                }
            }
            return collection;
        }

        if (rawValues.length == 0) {
            return defaultValueFor(rawClass);
        }

        String candidate = rawValues[0];
        if (candidate == null) {
            return defaultValueFor(rawClass);
        }
        if (candidate.isEmpty()) {
            return emptyValueFor(rawClass);
        }
        return convertParameterValue(candidate, rawClass);
    }

    private Collection<Object> instantiateCollection(Class<?> collectionType) {
        if (collectionType == null || collectionType.isInterface() || Modifier.isAbstract(collectionType.getModifiers())) {
            if (Set.class.isAssignableFrom(collectionType)) {
                return new LinkedHashSet<>();
            }
            return new ArrayList<>();
        }

        try {
            @SuppressWarnings("unchecked")
            Collection<Object> instance = (Collection<Object>) collectionType.getDeclaredConstructor().newInstance();
            return instance;
        } catch (Exception ignored) {
            if (Set.class.isAssignableFrom(collectionType)) {
                return new LinkedHashSet<>();
            }
            return new ArrayList<>();
        }
    }

    private Type extractTypeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (index >= 0 && index < arguments.length) {
                return arguments[index];
            }
        }
        return Object.class;
    }

    private Class<?> resolveClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?>) {
                return (Class<?>) raw;
            }
        }
        return Object.class;
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
