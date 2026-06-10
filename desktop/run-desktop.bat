@echo off
REM Launch the packaged desktop client on Windows.
REM Build it first with:  mvn clean package   (produces target\desktop-app.jar)
REM Requires a JRE 21+ on PATH. The jar bundles JavaFX, so no separate JavaFX install is needed.
setlocal
set "JAR=%~dp0target\desktop-app.jar"
if not exist "%JAR%" (
    echo desktop-app.jar not found. Build it first:  cd /d "%~dp0" ^&^& mvn clean package
    exit /b 1
)
java -jar "%JAR%"
