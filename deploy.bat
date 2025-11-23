@echo off

:: Définition des variables
set "APP_NAME=framework-web"
set "APP_SRC=C:\Users\fanir\Documents\ITU\Faniry\S5\Naina\framework-web\src\main\java\com\framework"
set "WEB_DIR=C:\Users\fanir\Documents\ITU\Faniry\S5\Naina\framework-web\src\main\webapp"
set "BUILD_DIR=build"
set "LIB_DIR=lib"
set "WEB_INF=%BUILD_DIR%\WEB-INF"
set "TOMCAT_DIR=C:\Users\fanir\Documents\utils\apache-tomcat-10.1.34\webapps"
set "SERVLET_API_JAR=%LIB_DIR%\jakarta.servlet-api-6.0.0.jar"

:: Création du répertoire lib et téléchargement de Jakarta Servlet API si nécessaire
if not exist "%LIB_DIR%" mkdir "%LIB_DIR%"
if not exist "%SERVLET_API_JAR%" (
    echo Téléchargement de Jakarta Servlet API...
    curl -L -o "%SERVLET_API_JAR%" "https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar"
    if errorlevel 1 (
        echo Erreur lors du téléchargement de Jakarta Servlet API.
        pause
        exit /b 1
    )
)

:: Nettoyage et création de l'arborescence temporaire
if exist "%BUILD_DIR%" (
    rmdir /s /q "%BUILD_DIR%"
)
mkdir "%BUILD_DIR%"
mkdir "%WEB_INF%\classes"
mkdir "%WEB_INF%\lib"

:: Création de la liste des fichiers Java
if exist sources.txt del sources.txt
for /r "%APP_SRC%" %%f in (*.java) do @echo %%f >> sources.txt

if not exist sources.txt (
    echo Aucun fichier Java trouvé dans %APP_SRC%.
    pause
    exit /b 1
)

:: Compilation des fichiers Java avec Jakarta Servlet API
javac -cp "%SERVLET_API_JAR%" -d "%WEB_INF%\classes" @"sources.txt"
if errorlevel 1 (
    echo Erreur lors de la compilation des fichiers Java.
    pause
    exit /b 1
)

:: Copier Jakarta Servlet API dans WEB-INF/lib (optionnel pour JAR)
copy "%SERVLET_API_JAR%" "%WEB_INF%\lib\"

:: Copier les fichiers web si ils existent (web.xml, JSP, etc.)
if exist "%WEB_DIR%\WEB-INF" (
    xcopy /e /i /q "%WEB_DIR%\WEB-INF" "%WEB_INF%"
    if errorlevel 1 (
        echo Erreur lors de la copie des fichiers WEB-INF.
        pause
        exit /b 1
    )
)

:: Génération du fichier .war dans le dossier build
cd "%BUILD_DIR%"
jar -cvf "%APP_NAME%.war" *
if errorlevel 1 (
    echo Erreur lors de la création du fichier WAR.
    pause
    exit /b 1
)
cd ..

:: Génération du fichier .jar pour les classes uniquement
cd "%WEB_INF%\classes"
jar -cvf "..\..\..\%APP_NAME%.jar" *
if errorlevel 1 (
    echo Erreur lors de la création du fichier JAR.
    pause
    exit /b 1
)
cd ..\..\..

:: Déploiement du fichier .war vers Tomcat (optionnel)
if exist "%TOMCAT_DIR%" (
    copy "%BUILD_DIR%\%APP_NAME%.war" "%TOMCAT_DIR%\"
    if errorlevel 1 (
        echo Erreur lors du déploiement vers Tomcat.
        pause
        exit /b 1
    )
    echo Déploiement WAR vers Tomcat terminé !
) else (
    echo Répertoire Tomcat non trouvé : %TOMCAT_DIR%
    echo WAR créé dans %BUILD_DIR%\%APP_NAME%.war
)

echo.
echo ==========================================
echo Deploiement termine avec succes !
echo ==========================================
echo.
echo Fichiers generés :
echo - JAR : %APP_NAME% 
echo - WAR : %BUILD_DIR%\%APP_NAME%.war
echo.
echo Instructions :
echo 1. Pour JAR : Copiez %APP_NAME%.jar dans WEB-INF/lib de votre app web
echo 2. Pour WAR : Deployez %BUILD_DIR%\%APP_NAME%.war dans Tomcat
echo.

pause