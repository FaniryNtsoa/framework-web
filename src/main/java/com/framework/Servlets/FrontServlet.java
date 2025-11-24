package com.framework.Servlets;

import com.framework.Scanners.ScanControllers;
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
import java.util.HashMap;
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
@WebServlet("/*")
public class FrontServlet extends HttpServlet {

    private static final String CONTROLLERS_PACKAGES_PARAM = "controllers-packages";
    public static final String ROUTE_REGISTRY_ATTRIBUTE = "framework.routes";
    private Map<String, Method> routeRegistry = new HashMap<>();

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

        // Sprint 2-bis: Scanner et remplir la HashMap
        routeRegistry = ScanControllers.mapHandlePaths(packagesDeclaration.trim());
        
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

        // Sprint 2-bis: Chercher d'abord dans la HashMap des contrôleurs
        Method handler = routeRegistry.get(path);

        if (handler != null) {
            // Route trouvée dans nos contrôleurs, invoquer la méthode
            invokeHandler(handler, req, resp);
            return;
        }

        // Si pas de contrôleur, vérifier si c'est un fichier statique (HTML, CSS, JS, images, etc.)
        // getServletContext().getResource() vérifie si le fichier existe physiquement dans webapp/
        URL resource = getServletContext().getResource(path);
        if (resource != null) {
            // Si c'est un fichier JSP, laisser le servlet JSP de Tomcat le gérer
            if (path.endsWith(".jsp")) {
                RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("jsp");
                if (dispatcher != null) {
                    dispatcher.forward(req, resp);
                    return;
                }
            }
            
            // C'est un fichier statique (HTML, CSS, JS, images), laisser le servlet par défaut de Tomcat le servir
            RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("default");
            if (dispatcher != null) {
                dispatcher.forward(req, resp);
                return;
            }
        }

        // Sprint 2-bis: Erreur 404 si ni contrôleur ni fichier statique trouvé
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.setContentType("text/plain;charset=UTF-8");
        resp.getWriter().println("Erreur 404 : " + path + " introuvable.");
    }

    /**
     * Sprint 4: Invoquer la méthode du contrôleur
     * Sprint 4-bis: Gérer le retour ModelView pour dispatch JSP
     * Sprint 5: Transférer les données du ModelView vers request.setAttribute()
     */
    private void invokeHandler(Method handler, HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        try {
            // Instancier le contrôleur
            Class<?> controllerClass = handler.getDeclaringClass();
            Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

            // Rendre la méthode accessible
            handler.setAccessible(true);

            // Invoquer la méthode
            Class<?>[] paramTypes = handler.getParameterTypes();
            Object result;

            if (paramTypes.length == 2) {
                // Méthode avec (HttpServletRequest, HttpServletResponse)
                result = handler.invoke(controllerInstance, req, resp);
            } else if (paramTypes.length == 1) {
                // Méthode avec (HttpServletRequest) ou (HttpServletResponse)
                if (HttpServletRequest.class.isAssignableFrom(paramTypes[0])) {
                    result = handler.invoke(controllerInstance, req);
                } else if (HttpServletResponse.class.isAssignableFrom(paramTypes[0])) {
                    result = handler.invoke(controllerInstance, resp);
                } else {
                    throw new ServletException("Type de paramètre non supporté : " + paramTypes[0]);
                }
            } else if (paramTypes.length == 0) {
                // Méthode sans paramètre
                result = handler.invoke(controllerInstance);
            } else {
                throw new ServletException("Signature de méthode non supportée : " + handler);
            }

            // Sprint 4-bis & 5: Si la méthode retourne un ModelView, dispatcher vers la vue JSP
            // IMPORTANT: Vérifier ModelView AVANT String pour éviter les problèmes de Content-Type
            if (result instanceof ModelView && !resp.isCommitted()) {
                ModelView modelView = (ModelView) result;
                String viewPath = modelView.getVue();
                
                if (viewPath == null || viewPath.isBlank()) {
                    throw new ServletException("ModelView.getVue() retourne null ou vide");
                }
                
                // Sprint 5: Transférer toutes les données du ModelView vers le request
                // Les données seront accessibles dans la JSP via request.getAttribute(key)
                Map<String, Object> data = modelView.getData();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                
                // NE PAS définir de Content-Type avant le dispatch !
                // Le JSP définira son propre Content-Type
                RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
                dispatcher.forward(req, resp);
            }
            // Sprint 4: Si la méthode retourne un String, l'afficher avec PrintWriter
            else if (result instanceof String && !resp.isCommitted()) {
                resp.setContentType("text/plain;charset=UTF-8");
                resp.getWriter().print(result);
            }

        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'invocation du handler : " + handler, e);
        }
    }
}
