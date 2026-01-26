echo "This is a sample compilation script"
cd ..
cd IFCtoRDF
call C:\apache-maven-3.6.3\bin\mvn clean install
cd ..
cd IFCtoLBDGeometry
call C:\apache-maven-3.6.3\bin\mvn clean install
cd ..
cd converter
call C:\apache-maven-3.6.3\bin\mvn clean install
cd ..
cd desktop_java8
call C:\apache-maven-3.6.3\bin\mvn clean install
cd ..

cd IFCtoLBD_OpenAPI
call C:\apache-maven-3.6.3\bin\mvn clean install
call C:\apache-maven-3.6.3\bin\mvn enunciate:docs install
cd ..

