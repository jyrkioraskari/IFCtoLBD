# IFCtoLBD
This repository presents the results of our ongoing work to 
create a usable converter to convert  
Industry Foundation Classes (IFC) STEP formatted files into 
Resource Description Framework (RDF) triples that follow the small ontologies devised in  the  World Wide Web Consortium (W3C) Linked Building Data Community Group (W3C LBD-CG)
(https://github.com/w3c-lbd-cg/).

The precompiled desktop application, IFCtoLBD-Desktop.jar, is available at this page. It is a runnable JAR-file. It is recommended to run the JAR using Java JDK version 8 that can be downloaded from http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html .  If the Java installation is fine, the file can be run by clicking it. If not, try the following command at the command line: "java -jar IFCtoLBD-Desktop.jar".
![GitHub Logo](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/desktop/src/main/resources/screen.PNG)


## Compiling the code
The converter can be compiled using maven and Java JDK (the above link). Maven can be downloaded from the site here: https://maven.apache.org/download.cgi.

The following steps are needed:
1. Make sure that the JAVA_HOME environment variable point to the JAVA JDK directory. JRE is not enough.
2. cd converter
3. mvn clean 
4. mvn install

The created package will be at the target subdirectory.
You can rename the ifc2lbd-1.08-jar-with-dependencies.jar file into name IFCtoRDF.jar

An example commaand line usage of the program is:
java -jar IFCtoRDF.jar Duplex_A.ifc http://uribase  out.ttl

## License
This project is released under the open source [GNU Affero General Public License v3](http://www.gnu.org/licenses/agpl-3.0.en.html)

