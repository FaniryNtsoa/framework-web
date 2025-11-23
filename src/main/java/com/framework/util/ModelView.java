package com.framework.util;

/**
 * Sprint 4-bis: ModelView
 * 
 * Classe permettant de spécifier une vue (JSP) à afficher après le traitement d'une requête.
 * Le contrôleur retourne un ModelView avec le nom de la vue, et le FrontServlet dispatch vers cette vue.
 */
public class ModelView {
    
    private String vue;

    /**
     * Constructeur par défaut
     */
    public ModelView() {
    }

    /**
     * Constructeur avec le nom de la vue
     * @param vue Le chemin vers la vue (ex: "page.jsp", "views/home.jsp")
     */
    public ModelView(String vue) {
        this.vue = vue;
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

    @Override
    public String toString() {
        return "ModelView{vue='" + vue + "'}";
    }
}
