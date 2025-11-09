package com.framework;

import java.io.IOException;
import java.net.URL;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.RequestDispatcher;

@WebServlet("/*") // intercepte toutes les requêtes
public class FrontServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        chercherRessource(req, resp);
    }

    private void chercherRessource(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        //  Récupération du chemin relatif à la racine de ton appli
        String path = req.getRequestURI().substring(req.getContextPath().length());

        //  Si c’est la racine → afficher juste "/"
        if ("/".equals(path)) {
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().println("/");
            return;
        }

        // Vérifie si la ressource demandée existe dans ton dossier web
        URL resource = getServletContext().getResource(path);

        if (resource != null) {
            // Si elle existe → le conteneur (Tomcat, WildFly, etc.) la sert
            RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("default");
            dispatcher.forward(req, resp);
        } else {
            // Sinon → renvoyer une vraie 404
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Erreur 404 : " + path + " introuvable.");
        }
    }
}
