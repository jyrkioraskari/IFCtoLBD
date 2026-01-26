# IFCtoLBD
Version 2.45.0
Free for all of us, forever.

The IFCtoLBD converter transforms Industry Foundation Classes (IFC) files in STEP format into Resource Description Framework (RDF) triples. These RDF triples adhere to the ontologies created by the World Wide Web Consortium (W3C) Linked Building Data Community Group (W3C LBD-CG: https://github.com/w3c-lbd-cg/).

Full documentation can be found
[here](https://jyrkioraskari.github.io/IFCtoLBD/#/).


## Contributors
Jyrki Oraskari, Mathias Bonduel, Kris McGlinn, Anna Wagner, Pieter Pauwels, Ville Kukkonen, Simon Steyskaland, Joel Lehtonen, Maxime Lefrançois, and Lewis John McGibbney. Thanks also to Vladimir Alexiev and Kathrin Dentler for their valuable comments.


## License
This project is released under the open source [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## How to cite
```
@software{jyrki_oraskari_2024_7636217,
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
  title        = {IFCtoLBD: IFCtoLBD v 2.44.0},
  month        = aug,
  year         = 2024,
  publisher    = {GitHub},
  version      = {2.44.0},
  url          = {https://github.com/jyrkioraskari/IFCtoLBD}
}

```


## Blog
### October 10, 2025
The new version introduces a significant change: the converter now relies on a TDB database. While this makes it slightly slower, it enables the conversion of substantially larger IFC models.

- If you have a pretty large IFC model, the new version used disk space to convert it. There should be a proper error message about it now.

### April 9, 2025
Added a small example in the Scala language. It depends on the maven compilation of the Java code shown above. 


### February 20, 2025
A small LLM example was added in Python.

### January 10, 2025

A short example to list properties by elements was added.
[here ](/IFCtoLBD_Python/examples.md).

### September 20, 2024
I wrote a Python program to demonstrate how the interfaces can be used to infer which door to use to 
enter a space. 
![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Python/door_interface.png)

The short example code can be found [here](/IFCtoLBD_Python/IFCtoLBD_SPARQL_Open3D_Interface.py).



### September 19, 2024
While it’s still in the preliminary stages and requires thorough testing, a source code for our bounding box-based interface generation is now available. 
The code quality is being tested, and we are gearing up for the next pre-compiled release soon. If you encounter any bugs, please let me know.

### March 14, 2024

Just in case you have a path problem in a MacBook when writing Python: 
-- It is recommended yo use absolute path names for the Java library files (macOS). You can use a text editor Search&Replace to fix there ysour configuration at the line of code:
jpype.startJVM(classpath = ['...<the JAR files in your configuration>']).

A short example code of relative names can be found [here](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_Python/IFCtoLBD_RDFLibTurtle.py).


### March 11, 2024
The Python examples were rewritten so that the import error in some JPype versions should not appear.
Also, instructions to copy the jars folder was added.

### February 21, 2024
2D linegraph splits from the model 
![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD/Split_Demo.png)
The short example code can be found [here](/IFCtoLBD/examples.md).

The same with doors and windows, and, finally, spaces.

![Screen](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD/Split_DemoSpaces3.png)
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

1. What does it mean when IFCtoLBD says “Java heap space”?

    - This error typically occurs when converting a large file. It indicates the program has run out of memory allocated for the Java heap. To resolve this, try starting the program using run.bat


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
The research was partly funded by the EU through the H2020 project BIM4REN.

https://dc.rwth-aachen.de/de/forschung/bim4ren

