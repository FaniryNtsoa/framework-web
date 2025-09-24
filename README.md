# Framework Web

Un framework web Java simple qui intercepte toutes les requêtes HTTP via un FrontController pattern.

## 📋 Description

Ce projet implémente un framework web minimal utilisant Jakarta Servlet API. Le `FrontController` capture toutes les URLs entrantes (`/*`) et affiche des informations détaillées sur chaque requête interceptée.

## 🏗️ Structure du projet

```
framework-web/
├── pom.xml                          # Configuration Maven
├── deploy.ps1                       # Script de déploiement PowerShell
├── deploy.bat                       # Script de déploiement Batch
├── README.md                        # Documentation
└── src/
    └── main/
        └── java/
            └── com/
                └── framework/
                    └── FrontController.java  # Servlet principal
```

## 🔧 Prérequis

- **Java 17** ou supérieur
- **Maven 3.6+**
- **Apache Tomcat 10** ou supérieur (pour le déploiement)

## 🚀 Compilation

### Option 1 : Script PowerShell (Recommandé)

```powershell
# Compilation simple
.\deploy.ps1

# Avec nettoyage préalable
.\deploy.ps1 -Clean

# Avec copie automatique vers un répertoire
.\deploy.ps1 -TargetPath "C:\tomcat\webapps\myapp\WEB-INF\lib"

# Mode verbose pour debugging
.\deploy.ps1 -Verbose
```

### Option 2 : Script Batch

```batch
deploy.bat
```

### Option 3 : Maven direct

```bash
# Compilation et packaging
mvn clean compile package

# Le JAR sera généré dans target/framework-web-1.0.0.jar
```

## 📦 Déploiement

1. **Copiez le JAR** généré (`target/framework-web-1.0.0.jar`) dans le répertoire `WEB-INF/lib` de votre application web Tomcat

2. **Redémarrez Tomcat**

3. **Testez** en accédant à n'importe quelle URL de votre application web

## ✨ Fonctionnalités

- ✅ Interception de toutes les requêtes HTTP (`GET`, `POST`, `PUT`, `DELETE`)
- ✅ Affichage détaillé des informations de requête
- ✅ Interface web responsive avec CSS intégré
- ✅ Logging complet des requêtes
- ✅ Support de Jakarta Servlet API 6.0
- ✅ Compatible Java 17+

## 🔍 Informations affichées

Le FrontController affiche les informations suivantes pour chaque requête :

- Méthode HTTP utilisée
- URI de la requête
- Chemin du contexte
- Chemin du servlet
- Paramètres de requête (query string)
- Adresse IP du client
- User-Agent du navigateur
- Timestamp de la requête

## 📋 Configuration Maven

Le projet utilise :

- **Jakarta Servlet API 6.0.0** (scope: provided)
- **Maven Compiler Plugin 3.11.0** (Java 17)
- **Maven JAR Plugin 3.3.0** (génération du JAR)
- **Maven Dependency Plugin 3.6.1** (copie des dépendances)

## 🧪 Test rapide

Après déploiement, accédez à :
- `http://localhost:8080/votre-app/`
- `http://localhost:8080/votre-app/test`
- `http://localhost:8080/votre-app/api/users`

Toutes ces URLs seront interceptées et afficheront la page de réponse du framework.

## 🐛 Dépannage

### Maven non trouvé
```bash
# Vérifiez l'installation de Maven
mvn -version

# Ou installez Maven via Chocolatey (Windows)
choco install maven
```

### Java non trouvé
```bash
# Vérifiez la version de Java
java -version

# Assurez-vous que JAVA_HOME est configuré
echo $JAVA_HOME
```

### Problèmes de compilation
1. Vérifiez que vous utilisez Java 17+
2. Nettoyez le projet : `mvn clean`
3. Recompilez : `mvn compile package`

## 📝 Logs

Les logs du framework sont disponibles dans les logs de Tomcat :
- Initialisation du servlet
- Interception des requêtes (avec détails)
- Destruction du servlet

## 🤝 Contribution

Pour contribuer au projet :
1. Fork du repository
2. Créez une branche feature
3. Committez vos changements
4. Créez une Pull Request

## 📄 Licence

Ce projet est sous licence MIT.

## 👨‍💻 Auteur

Framework-Web v1.0.0