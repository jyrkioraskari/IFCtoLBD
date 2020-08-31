# IFCtoLBD
Version 2.2

Contributors: Jyrki Oraskari, Mathias Bonduel, Kris McGlinn, Anna Wagner, and Pieter Pauwels.


This repository presents the results of our ongoing work to 
create a usable converter to convert
Industry Foundation Classes (IFC) STEP formatted files into 
Resource Description Framework (RDF) triples that follow the small ontologies devised in  the  World Wide Web Consortium (W3C) Linked Building Data Community Group (W3C LBD-CG)
(https://github.com/w3c-lbd-cg/).

Proceedings of the 6th Linked Data in Architecture and Construction Workshop:
[The IFC to Linked Building Data Converter - Current Status](http://ceur-ws.org/Vol-2159/04paper.pdf).

It is recommended to use Java 8. It can be [downloaded from Oracle](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)

## Precompiled binaries

Precompiled applications are included in this repository.

* Desktop application: [IFCtoLBD-Desktop Java 8](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD-Desktop_Java_8.jar?raw=true)
* The special Java 12 version: [IFCtoLBD-Desktop Java 12](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD-Desktop_Java_12.jar?raw=true)

These are runnable JAR files. If the Java installation is fine, the file can be run by clicking it. 
When converting large files, `run.bat` can be used. It is also faster since it allows the program to use more memory for the calculation.

If the program does not start, try the following command at the command line: `java -jar IFCtoLBD-Desktop_Java_8.jar`.

![GitHub Logo](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/desktop_java8/src/main/resources/screen.PNG)

## Source Code Documentation 

[Javadoc](https://jyrkioraskari.github.io/IFCtoLBD/)


## Compiling the code
The converter can be compiled using maven and Java JDK (the above link). Maven can be downloaded from https://maven.apache.org/download.cgi.

First, make sure that the `JAVA_HOME` environment variable point to the JAVA JDK directory. JRE is not enough. Then run the following commands:

```sh
cd ifc2rdf_library
mvn clean
mvn install
cd ..
cd converter
mvn clean 
mvn install
cd desktop
mvn clean 
mvn install
```

The created package will be at the target subdirectory.
You can rename the `ifc2lbd-1.08-jar-with-dependencies.jar` file to `IFCtoRDF.jar`.

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

## IFCtoLBD OpenAPI Implementation

An Restful OpenAPI implementation for IFCtoLBD converter.  

A test installation:
http://lbd.arch.rwth-aachen.de/IFCtoLBD_OpenAPI/apidocs/

### Swagger.json description for the services

http://lbd.arch.rwth-aachen.de/IFCtoLBD_OpenAPI/apidocs/ui/swagger.json

### Docker for the Open API interface

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

## Cite this
```
@software{jyrki_oraskari_2020_4009498,
  author       = {Jyrki Oraskari and
				  Mathias Bonduel and
                  Kris McGlinn and
                  Pieter Pauwels and
                  Freddy Priyatna and
                  Anna Wagner and
                  Joel Lehtonen OH64K},
  title        = {jyrkioraskari/IFCtoLBD: IFCtoLBD 2.2},
  month        = aug,
  year         = 2020,
  publisher    = {Zenodo},
  version      = {2.2},
  doi          = {10.5281/zenodo.4009498},
  url          = {https://doi.org/10.5281/zenodo.4009498}
}
```

## Frequently asked questions

1. What does it mean when IFCtoLBD says Java heap space?

   - The most probable situation for this is when a large file is converted. Try to start the program using `run.bat`. 

2. Why does the program say: *"Error: Cannot determine which IFC version the model it is: [IFC2X2_FINAL]"*

   - IFC 2x2 Final was published as early as 2003, 14 years ago. There are still some test files that are generated using this version. Support for this may be added.  Currently the supported IFC versions are:  IFC2x3TC1, FC2x3FINAL, IFC4, IFC4 ADD1 and  IFC4 ADD2.

3. Nothing happens when I start the program.

   - Check that Java 8 is installed, open a command prompt
     at the directory where IFCtoLBD-Desktop_Java_8.jar is located. Run the following command:
     `java -jar IFCtoLBD-Desktop_Java_8.jar`

4. In Windows, I cannot open the program by double clicking the file
   - Open a command prompt as admin
   - Run the following commands:
   
   ```
   assoc .jar=jarfile
   type jarfile="your java installation directory\bin\javaw.exe" -jar "%1" %*
   ```

   where *your java installation directory* is the base directory where your Java runtime is installed.

