package com.framework.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Sprint 4-bis: ModelView
 * Sprint 5: Ajout de données (Map) pour passer des objets à la vue
 * 
 * Classe permettant de spécifier une vue (JSP) à afficher après le traitement d'une requête.
 * Le contrôleur retourne un ModelView avec le nom de la vue, et le FrontServlet dispatch vers cette vue.
 * 
 * Sprint 5: Les données ajoutées via addObject() sont transférées dans le request.setAttribute()
 * pour être accessibles dans la JSP via request.getAttribute()
 */
public class ModelView {
    
    private String vue;
    private Map<String, Object> data;

    /**
     * Constructeur par défaut
     */
    public ModelView() {
        this.data = new HashMap<>();
    }

    /**
     * Constructeur avec le nom de la vue
     * @param vue Le chemin vers la vue (ex: "page.jsp", "views/home.jsp")
     */
    public ModelView(String vue) {
        this.vue = vue;
        this.data = new HashMap<>();
    }

    /**
     * Getter pour l'attribut vue
     * @return Le chemin de la vue
     */
    public String getVue() {
        return vue;
    }

    /**
     * Setter pour l'attribut vue
     * @param vue Le chemin vers la vue
     */
    public void setVue(String vue) {
        this.vue = vue;
    }

    /**
     * Sprint 5: Ajouter un objet au modèle
     * Les données seront accessibles dans la JSP via request.getAttribute(key)
     * 
     * @param key Le nom de la variable (clé)
     * @param value L'objet à transmettre (valeur)
     * @return this (pour chaînage fluent)
     */
    public ModelView addObject(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    /**
     * Sprint 5: Récupérer toutes les données du modèle
     * @return La Map contenant toutes les données
     */
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ModelView{vue='" + vue + "', data=" + data.keySet() + "}";
    }
}
