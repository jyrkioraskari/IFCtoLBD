@echo off
echo %JAVA_HOME%

echo If you will get an error use "run - Java12.bat"
echo It takes some time to load all the libraries
echo --------------------------------------------------
"%JAVA_HOME%\\bin\\java" -Xms16G -Xmx16G -jar IFCtoLBD-Desktop_Java_8.jar
pause


