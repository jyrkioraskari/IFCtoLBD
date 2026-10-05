@echo off
echo %JAVA_HOME%

echo It takes some time to load all the libraries
echo --------------------------------------------------
set "APP_LIB=%~dp0IFCtoLBD-Desktop_Java_21_lib"
set "JAVAFX_MODULES=%APP_LIB%\javafx-base-21.0.10-win.jar;%APP_LIB%\javafx-graphics-21.0.10-win.jar;%APP_LIB%\javafx-controls-21.0.10-win.jar;%APP_LIB%\javafx-fxml-21.0.10-win.jar"

"%JAVA_HOME%\bin\java" -Xms512M -Xmx4G --module-path "%JAVAFX_MODULES%" --add-modules javafx.controls,javafx.fxml -cp "%~dp0IFCtoLBD-Desktop_Java_21.jar;%APP_LIB%\*" org.linkedbuildingdata.ifc2lbd.desktop.Main
pause


