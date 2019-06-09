# IFCtoLBD
Version 1.78

Contributors: Jyrki Oraskari, Mathias Bonduel, Kris McGlinn, Anna Wagner, and Pieter Pauwels.


This repository presents the results of our ongoing work to 
create a usable converter to convert  
Industry Foundation Classes (IFC) STEP formatted files into 
Resource Description Framework (RDF) triples that follow the small ontologies devised in  the  World Wide Web Consortium (W3C) Linked Building Data Community Group (W3C LBD-CG)
(https://github.com/w3c-lbd-cg/).

It is recommended to use Java 8. It can be downloaded from:
https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

The precompiled desktop application, IFCtoLBD_Desktop_Java8-1.77.jar, is available at this page at 
 [IFCtoLBD-Desktop Java 8](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Desktop_Java8-1.77.jar?raw=true).

 The special Java 11 versio is available at:
 [IFCtoLBD-Desktop Java 10](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Desktop_Java11-1.77.jar?raw=true).

 
It is a runnable JAR-file. If the Java installation is fine, the file can be run by clicking it. 
If converting large files, run.bat can be used. It is also faster since it allow the program to use more memory for the calculation.


If the program does not start, try the following command at the command line: "java -jar IFCtoLBD_Desktop_Java8-1.77.jar".

![GitHub Logo](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/desktop_java8/src/main/resources/screen.PNG)


## Compiling the code
The converter can be compiled using maven and Java JDK (the above link). Maven can be downloaded from the site here: https://maven.apache.org/download.cgi.

The following steps are needed:
1. Make sure that the JAVA_HOME environment variable point to the JAVA JDK directory. JRE is not enough.
2. cd ifc2rdf_library
3. mvn clean
4. mvn install
5. cd ..
6. cd converter
7. mvn clean 
8. mvn install
9. cd desktop
10. mvn clean 
11. mvn install

The created package will be at the target subdirectory.
You can rename the ifc2lbd-1.08-jar-with-dependencies.jar file into name IFCtoRDF.jar

An example command line usage of the program is:
java -jar IFCtoLBD_Desktop_Java8-1.77.jar Duplex_A.ifc http://uribase  out.ttl

## License
This project is released under the open source [GNU Affero General Public License v3](http://www.gnu.org/licenses/agpl-3.0.en.html)

## Cite this
```
@misc{jyrki_oraskari_2019_2645528,
  author       = {Jyrki Oraskari and
                  Mathias Bonduel and
                  Kris McGlinn and
                  Pieter Pauwels and
                  Anna Wagner},
  title        = {jyrkioraskari/IFCtoLBD 1.77},
  month        = apr,
  year         = 2019,
  doi          = {10.5281/zenodo.2645528},
  url          = {https://doi.org/10.5281/zenodo.2645528}
}
```

## Frequently asked questions

1.  What does it mean when IFCtoLBD says Java heap space?

- The most probable situation for this is when a large file is converted. Try to start the program using run.bat. 

2. Why does the program say: "Error: Cannot determine which IFC version the model it is: [IFC2X2_FINAL]"

- IFC 2x2 Final was published as early as 2003, 14 years ago. There are still some test files that are generated using this version. Support for this may be added.  Currently the supported IFC versions are:  IFC2x3TC1, FC2x3FINAL, IFC4, IFC4 ADD1 and  IFC4 ADD2.  

3. Nothing happens when I start the program.
- Check that Java 8 is installed, open a command prompth
at the directory where IFCtoLBD-Desktop_Java_8.jar is located. Run the following command:
java -jar IFCtoLBD-Desktop_Java_8.jar

4. In Windows, I cannot open the program by double clicking the file
- Open a command prompt as admin
- Run the following commands:

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;assoc .jar=jarfile

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;type jarfile="your java installation directory\bin\javaw.exe" -jar "%1" %*

where "your java installation directory" is the base directory where your runtime Java is installed.

