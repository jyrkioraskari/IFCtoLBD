# IFCtoLBD
Version 2.43.5

Contributors: Jyrki Oraskari, Mathias Bonduel, Kris McGlinn, Anna Wagner, Pieter Pauwels, Ville Kukkonen, Simon Steyskaland, Joel Lehtonen, Maxime Lefrançois, and Lewis John McGibbney.

Free for all of us, forever.

This repository presents the results of our ongoing work to create a usable converter to convert Industry Foundation Classes (IFC) STEP formatted files into 
Resource Description Framework (RDF) triples that follow the small ontologies devised in  the  World Wide Web Consortium (W3C) Linked Building Data Community Group (W3C LBD-CG)
(https://github.com/w3c-lbd-cg/).

Proceedings of the 6th Linked Data in Architecture and Construction Workshop:
[The IFC to Linked Building Data Converter - Current Status](http://ceur-ws.org/Vol-2159/04paper.pdf).

It is recommended to use OpenJDK 21 (it is the modt current  Long-Term Support version). Java 17 is supported. OpenJava can be downloaded from  (https://docs.microsoft.com/en-us/java/openjdk/download).
On a Windows system, download the MSI file that matches your processor type (usually x64 aka Intell), and run it to install Java.

## Precompiled binaries

Precompiled applications are available in the published release.
https://github.com/jyrkioraskari/IFCtoLBD/releases

* Desktop application: IFCtoLBD-Desktop 
Use Java 21 for compiling the converter and the desktop app.  For the OpenAPI interface, it is recommended to use Java 21.

These are runnable JAR files. If the Java installation is fine, the file can be run by clicking it. 
When converting large files, `run.bat` can be used. It is also faster since it allows the program to use more memory for the calculation.


Also, Windows 10/11 installations are available. The zip files contain script files to run the program neatly in the windows operating system. 

If the program does not start, try the following command at the command line: `java -jar IFCtoLBD-Desktop.jar`.

![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Desktop/src/main/resources/screen.PNG)

## Source Code Documentation 

[Javadoc](https://jyrkioraskari.github.io/IFCtoLBD/)


Java programming examples can be found
[here ](/IFCtoLBD/examples.md).

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
-  Note: If you have problems compiling the sources, remove the module-info.java files (they expect to find the JAR files of the Maven-referred libraries of older Java versions). 

OLD instruction was:
```
cd IFCtoLBD_OpenAPI
call mvn clean install
set MAVEN_OPTS=--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
call mvn enunciate:docs install
cd ..
```

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
The Maven library was published on the 16th of January, 2024.  

```
<dependency>
  <groupId>io.github.jyrkioraskari</groupId>
  <artifactId>ifc2rdf</artifactId>
  <version>1.3.2</version>
</dependency>

<dependency>
  <groupId>io.github.jyrkioraskari</groupId>
  <artifactId>ifc-to-lbd</artifactId>
  <version>2.43.4</version>
</dependency>

<dependency>
  <groupId>de.rwth-aachen.lbd</groupId>
  <artifactId>idc_to_lbd_geometry</artifactId>
  <version>2.43.4</version>
</dependency>
```

## IFCtoLBD Python Implementation

The example implementation can be found in the IFCtoLBD_Python  subfolder

Installation:
```
pip install JPype1
pip install rdflib
```

```
# !/usr/bin/env python3
import pprint

import jpype
from rdflib import Graph, Literal, RDF, URIRef
# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath=['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter

# Convert the IFC file into LBD level 3 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/", 3)

model = lbdconverter.convert("Duplex_A_20110505.ifc");
statements = model.listStatements();

g = Graph()

# Copy triples to the Python rdflib library
# Apache Jena  operations:
# -------------------
while statements.hasNext():
    triple = statements.next()
    rdf_subject = URIRef(triple.getSubject().toString())
    rdf_predicate = URIRef(triple.getPredicate().toString())
    if triple.getObject().isLiteral():
        rdf_object = Literal(triple.getObject().toString())
    else:
        rdf_object = URIRef(triple.getObject().toString())
    g.add((rdf_subject, rdf_predicate, rdf_object))

# rdflib operations:
# -------------------
for stmt in g:
    pprint.pprint(stmt)
jpype.shutdownJVM()

```

More Python examples and detailed description can be found 
[here ](/IFCtoLBD_Python/examples.md).

## Docker for the Open API interface

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
                  Joel Lehtonen and
                  Maxime Lefrançois },
  title        = {IFCtoLBD: IFCtoLBD v 2.43.3},
  month        = jul,
  year         = 2023,
  publisher    = {GitHub},
  version      = {2.43.3},
  url          = {https://github.com/jyrkioraskari/IFCtoLBD}
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
                         The properties will be added to the output.
  -t, --target_file=<target_file>
                         he main file name for the output. If there are many,
                           they will be sharing the same name beginning.
  -u, --url=<uriBase>    The URI base for all the elements that will be
                           created.
  -V, --version          Print version information and exit.

```

Examples of the use:
```
java  -Xms16G -Xmx16G -jar IFCtoLBD_CLI.jar Duplex_A_20110907.ifc
java  -Xms16G -Xmx16G -jar IFCtoLBD_CLI.jar Duplex_A_20110907.ifc --target_file output.ttl
java  -jar IFCtoLBD_CLI.jar Duplex_A_20110907.ifc --level 1 --target_file output.ttl
```

## Blog
### February 21, 2024
2D linegraph splits from the model 
![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD/Split_Demo.png)
The short example code can be found [here](/IFCtoLBD/examples.md).


### January 30, 2024
Simple graph plot with Python.  Source code is [here ](/IFCtoLBD_Python/examples.md).
![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Python/graph_plot.png)

### January 26, 2024
Python visualization demo code available [here ](/IFCtoLBD_Python/examples.md).
![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Python/Python3DDemo.png)

### January 02, 2024
The recommendations was changed to encourage to use one of the last two performant Long-Term Support version of Java (17 or 21).
  
### November 13, 2023  
The new user Java 17 (and above) compatible OpenAPI is now in the source code. Earlier Java versions are not supported as the current Enuciate package
has that limitation. 

### June 21, 2023  
The new user interface is in the testing phase. This is not the final version yet. I still test how the filtering can be made smarter.

![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Desktop_2023/src/main/resources/screen2.PNG) (updated 21st Feb, 2024).
### March 1, 2023  
Tested and update the Python interface.

### February 13, 2023  
The geometry tests are finished. The converter now exports OBJ formatted geometry for the building elements. The ifcZip format support is implemented but needs still some more testing.

### June 07, 2022  
Support for xsd:decimal.

### May 16, 2022  
Support for multi-character Unicode sequences.

### April 19, 2022  
Added unlinked elements and those that have no LBD type. It allows using the converter when there are no BOT elements connected to the 
interested in elements.

### March 18, 2022  "This application requires a Java Runtime Environment"
The Windows executable bundle for the Open JDK was fixed to contain the Java version.

### December 15, 2021  Log4J
Last Sunday the software was patched to contain Log4J version 2.15 and today version 2.16 was added. 
The OpenAPI installation was shortly tested under Apache Tomcat 9.0.54 and updated to the current IFCtoLBD 
release. The Docker image is also updated.

### December 9, 2021  Command line converter tool

Example usage: 
java -jar IFCtoLBD_Java_15.jar  http://lbd.example.com/ c:\IFC\Duplex_A_20110505.ifc c:\IFC\Duplex_A_20110505.ttl

### December 7, 2021,  The degree sign character

The Unicode notation of the degree sign character caused Jena to stop reading the raw ifcOWL output. This is fixed now.

### September 6, 2021,  Java 15
Java 8 is more than seven years old now, which means that not all libraries are supporting that old Java variant anymore.  To keep the software secure, it is preferred to deprecate the version of the code in the long run. Currently, a separate Java 8 branch is kept in case only Java 8 can be used. 

### April 6, 2021  Support for European languages
The program code was modified so that the default backslash notation for UTF-8 characters is removed. äÄöÖåÅßüÜáÁàÀâÂ should be human-readable.

### October 13, 2020,  Testing the software
The software was tested to function with https://jdk.java.net/15/

### October 5, 2020,  Testing the software

Testing the correctness of the created bounding boxes.

![The bounding boxes](https://raw.githubusercontent.com/jyrkioraskari/IFCtoLBD/master/docs/bounding_boxes.PNG)

## Frequently asked questions

1. What does it mean when IFCtoLBD says Java heap space?

   - The most probable situation for this is when a large file is converted. Try to start the program using `run.bat`. 

2. Why does the program say: *"Error: Cannot determine which IFC version the model it is: [IFC2X2_FINAL]"*

   - IFC 2x2 Final was published as early as 2003, 14 years ago. There are still some test files that are generated using this version. Support for this may be added.  Currently, the supported IFC versions are  IFC2x3TC1, FC2x3FINAL, IFC4, IFC4 ADD1, and  IFC4 ADD2.

3. Nothing happens when I start the program.

   - Check that Java 15 is installed, open a command prompt, from the releases list, and download the precompiled
     binaries, then at the directory where IFCtoLBD-Desktop_Java_15.ja is located. Run the following command:
     `java -jar IFCtoLBD-Desktop_Java_15.jar`
	 
	- If any further problems, under the Windows 10 operating system, you can also try to use the 
	the bundled version of the converter: IFCtoLBD_Java15.exe  
	 
4. I have a problem running the OpenAPI interface under Apache Tomcat 9:
    - Check that the JAVA_HOME environmental variable at your computer points to Java version 15 or newer.
	The older versions of Java are not supported anymore (If you must use it for some reason, an older
	release of the converter can be used), since the used libraries don't support them anymore. 

5. In Windows, I cannot open the program by double-clicking the file
   - Open a command prompt as admin
   - Run the following commands:
   
   ```
   assoc .jar=jarfile
   type jarfile="your java installation directory\bin\javaw.exe" -jar "%1" %*
   ```

   where *your java installation directory* is the base directory where your Java runtime is installed.

6.  How to disable the missing project natures in Eclipse prompt
  - open Eclipse.
  - go to Window > Preferences.
  - navigate to General > Project Natures.
    There, you can disable the option for discovering missing project natures and marketplace entries.     

7.  Eclipse build takes forever to complete
    - Disable Project/Build Automatically, and build the all with Maven Install. Enable the option after.
    - eclipse -clean -clearPersistedState  // It resets Eclipse perspectives, too.

## Acknowledgements
The research was funded by the EU through the H2020 project BIM4REN.

https://dc.rwth-aachen.de/de/forschung/bim4ren

