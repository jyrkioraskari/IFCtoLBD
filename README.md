# IFCtoLBD
Version 2.41.0

Contributors: Jyrki Oraskari, Mathias Bonduel, Kris McGlinn, Anna Wagner, Pieter Pauwels, Ville Kukkonen, Simon Steyskaland, and Joel Lehtonen.


This repository presents the results of our ongoing work to create a usable converter to convert Industry Foundation Classes (IFC) STEP formatted files into 
Resource Description Framework (RDF) triples that follow the small ontologies devised in  the  World Wide Web Consortium (W3C) Linked Building Data Community Group (W3C LBD-CG)
(https://github.com/w3c-lbd-cg/).

Proceedings of the 6th Linked Data in Architecture and Construction Workshop:
[The IFC to Linked Building Data Converter - Current Status](http://ceur-ws.org/Vol-2159/04paper.pdf).

It is recommended to use OpenJava 19. OpenJava can be downloaded from  (https://docs.microsoft.com/en-us/java/openjdk/download).
At a windows system, download the MSI file that matches your processsor type (usually x64 aka Intell), and run it to install Java.

## Precompiled binaries

Precompiled applications are available in the published release.
https://github.com/jyrkioraskari/IFCtoLBD/releases

* Desktop application: IFCtoLBD-Desktop 
Minimum requirement is JAVA 15. Java 19 can be used for the converter and Desctop.  For the OpenAPI interface, use Java 15 (The Enunciate library does not support newer yet).

These are runnable JAR files. If the Java installation is fine, the file can be run by clicking it. 
When converting large files, `run.bat` can be used. It is also faster since it allows the program to use more memory for the calculation.


Also Windows 10/11 installations are availabe. The zip files contain script files to run the program neatly in the windows operating system. 

If the program does not start, try the following command at the command line: `java -jar IFCtoLBD-Desktop.jar`.

![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Desktop/src/main/resources/screen.PNG)

## Source Code Documentation 

[Javadoc](https://jyrkioraskari.github.io/IFCtoLBD/)

The desktop user interface was written using Java FXML. The editor can be get from:
https://gluonhq.com/products/scene-builder/ (You need to import the org.openjfx:javafx-graphics, and org.openjfx:javafx-controls with the library manager to get the editor working)

## Compiling the code
The converter can be compiled using maven and Java JDK (the above link). Maven can be downloaded from https://maven.apache.org/download.cgi.

First, make sure that the `JAVA_HOME` environment variable point to the JAVA JDK directory. JRE is not enough. Then run the following commands:

- In Eclipse, select first Maven Update project for all projects. 
```
cd IFCtoRDF
call mvn clean install
cd ..

cd IFCtoLBD_Geometry
call mvn clean install
cd ..

cd IFCtoLBD
call mvn clean install
cd ..

cd IFCtoLBD_Desktop
call mvn clean install
cd ..

cd IFCtoLBD_OpenAPI
call mvn clean install
call mvn enunciate:docs install
cd ..
```
-  Note: If you have problems compiling the sources, remove the module-info.java files (they expect to find the JAR files of the Maven referred libraries of older Java versions). 

Then, the best way to create a runnable [Java 19] (https://jdk.java.net/19/) program is to 
1. Use an Eclipse (https://www.eclipse.org/) installation,
2. Open org.linkedbuildingdata.ifc2lbd.Main class on the Eclipse editor
3. Select from the menu /Run/Run
4. Select the /File/Export:Java/Runnabe Jar file/Next
5. Launch configuration: -Select the created Main runtime configuration-, Package resource libraries into generated JAR
6. Select destination file and Finish.

An example command line usage of the program is:

```
java -jar IFCtoLBD.jar Duplex_A_20110505.ifc http://uribase out.ttl
```

## Maven
The Maven library was published on 29th of May, 2020.  

```
<dependency>
  <groupId>io.github.jyrkioraskari</groupId>
  <artifactId>IFCtoLBD</artifactId>
  <version>1.88</version>
  <classifier>jar-with-dependencies</classifier>
</dependency>
```
https://mvnrepository.com/artifact/io.github.jyrkioraskari/IFCtoLBD/1.88

[Javadoc](https://jyrkioraskari.github.io/IFCtoLBD/org/lbd/ifc2lbd/IFCtoLBDConverter.html)


How to use the code:
```
new IFCtoLBDConverter("c:\\in\model.ifc", "http://example.uri/", "c:\\out\\file.ttl",2, true, false, true, false, false, true);
```

## IFCtoLBD Python Implementation

The example implementation can be found in the IFCtoLBD_Python  subfolder

Installation:

pip install JPype1

```
# !/usr/bin/env python3

import jpype

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter
from org.apache.jena.query import QueryFactory, QueryExecutionFactory


# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

model=lbdconverter.convert("Duplex_A_20110505.ifc")
queryString = """PREFIX bot: <https://w3id.org/bot#>

SELECT ?building ?predicate ?object
WHERE {
   ?building a bot:Building .
   ?building ?predicate ?object
}"""

query = QueryFactory.create(queryString)
qexec = QueryExecutionFactory.create(query, model)
results = qexec.execSelect()
while results.hasNext() :
    soln = results.nextSolution()
    x = soln.get("building")
    print(x)

jpype.shutdownJVM()

```


### Docker for the Open API interface

Install Docker Desktop:  https://www.docker.com/get-started

Command-line commands needed to start the server at your computer;
```
docker pull jyrkioraskari/ifc2lbdopenapi:latest

docker container run -it --publish 8081:8080 jyrkioraskari/ifc2lbdopenapi


```
Then the software can be accessed from the local web address:
http://localhost:8081/IFCtoLBD_OpenAPI


## IFCtoLBD BimBot service plugin for BIMserver

[jyrkioraskari/IFCtoLBD_BIMBot-Plugin](https://github.com/jyrkioraskari/IFCtoLBD_BIMBot-Plugin)



## License
This project is released under the open source [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## How to cite
```
@software{jyrki_oraskari_2023_7636217,
 author       = {Jyrki Oraskari and
                  Mathias Bonduel and
                  Kris McGlinn and
                  Pieter Pauwels and
                  Freddy Priyatna and
                  Anna Wagner and
                  Ville Kukkonen and
                  Simon Steyskaland and
                  Joel Lehtonen},
  title        = {jyrkioraskari/IFCtoLBD: IFCtoLBD v 2.40.1},
  month        = feb,
  year         = 2023,
  publisher    = {Zenodo},
  version      = {2.40.1},
  doi          = {10.5281/zenodo.7636217},
  url          = {https://doi.org/10.5281/zenodo.7636217}
}

```

## Command line usage
```
Usage: IFCtoLBD_CLI [-bhpV] [-be] [--hasGeolocation] [--hasGeometry]
                    [--hasSeparateBuildingElementsModel]
                    [--hasSeparatePropertiesModel] [--hasTriG] [--hasUnits]
                    [--ifcOWL] [-l=<props_level>] [-t=<target_file>]
                    [-u=<uriBase>] <ifc_filename>
      <ifc_filename>     The absolute path for the IFC file that will be
                           converted.
  -b, --hasBlankNodes    Blank nodes are used.
      -be, --hasBuildingElements
                         The Building Elements will be created in the output.
  -h, --help             Show this help message and exit.
      --hasGeolocation   Geolocation, i.e., the latitude and longitude are
                           added.
      --hasGeometry      The bounding boxes are generated for elements.
      --hasSeparateBuildingElementsModel
                         The Building elements will have a separate file.
      --hasSeparatePropertiesModel
                         The properties will be written in a separate file.
      --hasTriG          TriG is a serialization format for RDF (Resource
                           Description Framework) graphs. It is a plain text
                           format for serializing named graphs
      --hasUnits         Data units are added.
      --ifcOWL           Geolocation, i.e., the latitude and longitude are
                           added.
  -l, --level=<props_level>
                         The OPM ontology complexity level
  -p, --hasBuildingElementProperties
                         The properties will ne added into the output.
  -t, --target_file=<target_file>
                         he main file name for the output. If there are many,
                           they will be sharing the same name beginning.
  -u, --url=<uriBase>    The URI base for all the elemenents that will be
                           created.
  -V, --version          Print version information and exit.

```

## Blog
### March 1, 2023  
Tested and update the Python interface.

### February 13, 2023  
The geometry tests are finished. The converter now exports OBJ formatted geometry for the building elements. The ifcZip format support is implemented, but needs still some more testing.

### June 07, 2022  
Support for xsd:decimal.


### May 16, 2022  
Support for multi-character Unicode sequences.

### April 19, 2022  
Added unlinked elements and those that have no LBD type.It allows using the converter when there are not BOT elements connected to the 
interested in elements.

### March 18, 2022  "This application requires a Java Runtime Environment"
The windows executable bundle for the Open JDK was fixed to contain the Java version.

### December 15, 2021  Log4J
Last Sunday the software was pached to contain Log4J version 2.15 and today version 2.16 was added. 
The OpenAPI installation was shortly tested under Apache Tomcat 9.0.54 and updated to the current IFCtoLBD 
release. The Docker image is also updated.

### December 9, 2021  Command line converter tool

Example usage: 
java -jar IFCtoLBD_Java_15.jar  http://lbd.example.com/ c:\IFC\Duplex_A_20110505.ifc c:\IFC\Duplex_A_20110505.ttl

### December 7, 2021  The degree sign character

The Unicode notation of the degree sign character caused Jena to stop reading the raw ifcOWL output. This is fixed now.

### September 6, 2021  Java 15
Java 8 is more than seven years old now, which means that not all libraries are supporting that old Java variant any more.  To keep the software secure, it is preferred to deprecated the version of the code in a long run. Currently, a separate Java 8 branch is kept in case only Java 8 can be used. 

### April 6, 2021  Support for European languages
The program code was modified so that the default backslash notation for UTF-8 characters is removed. äÄöÖåÅßüÜáÁàÀâÂ should be human-readable.

### October 13, 2020  Testing the software
The sofware was tested to function with https://jdk.java.net/15/

### October 5, 2020  Testing the software

Testing the correctness of the created bounding boxes.

![The bounding boxes](https://raw.githubusercontent.com/jyrkioraskari/IFCtoLBD/master/docs/bounding_boxes.PNG)

## Frequently asked questions

1. What does it mean when IFCtoLBD says Java heap space?

   - The most probable situation for this is when a large file is converted. Try to start the program using `run.bat`. 

2. Why does the program say: *"Error: Cannot determine which IFC version the model it is: [IFC2X2_FINAL]"*

   - IFC 2x2 Final was published as early as 2003, 14 years ago. There are still some test files that are generated using this version. Support for this may be added.  Currently the supported IFC versions are:  IFC2x3TC1, FC2x3FINAL, IFC4, IFC4 ADD1 and  IFC4 ADD2.

3. Nothing happens when I start the program.

   - Check that Java 15 is installed, open a command prompt, from the releases list, download the precompiled
     binaries, then at the directory where IFCtoLBD-Desktop_Java_15.ja is located. Run the following command:
     `java -jar IFCtoLBD-Desktop_Java_15.jar`
	 
	- If any further problem, under the Windows 10 operating system, you can also try to use the 
	bundled version of the converter: IFCtoLBD_Java15.exe  
	 
4. I have a problem running the OpenAPI interface under Apache Tomcat 9:
    - Check that the JAVA_HOME environmental variable at your computer points to Java version 15 or newer.
	The older versions of Java are not supported any more (If you must to use it for some reason, an older
	release of the converter can be used), since the used libraries don't support them any more. 

5. In Windows, I cannot open the program by double clicking the file
   - Open a command prompt as admin
   - Run the following commands:
   
   ```
   assoc .jar=jarfile
   type jarfile="your java installation directory\bin\javaw.exe" -jar "%1" %*
   ```

   where *your java installation directory* is the base directory where your Java runtime is installed.

## Acknowledgements
The research was funded by the EU through the H2020 project BIM4REN.

https://dc.rwth-aachen.de/de/forschung/bim4ren

