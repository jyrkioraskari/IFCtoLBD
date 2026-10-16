@echo off
echo %JAVA_HOME%

echo It takes some time to load all the libraries
echo --------------------------------------------------
echo This legacy launcher now uses the Java 21 desktop artifact.
"%JAVA_HOME%\\bin\\java" -Xms16G -Xmx16G -jar IFCtoLBD-Desktop_Java_21.jar
pause


