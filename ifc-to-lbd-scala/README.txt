You may need to install Ccala and add it to the path.
https://www.scala-lang.org/download/

To Compile (in this directory=:
mvn scala:compile 

Run this with:

mvn scala:run -DmainClass=de.aachen.rwth.ip.App


Package:
mvn clean package

- This creates an exacutable  JAR file.  Modify the code to read an IFC file from your file system,