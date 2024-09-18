# IFCtoRDF
IFCtoRDF is a set of reusable Java components that allows to parse IFC-SPF files and convert them into [RDF](https://www.w3.org/standards/techs/rdf#w3c_all) graphs. This library supports the conversion of ifc-spf files according to the schemas available at https://technical.buildingsmart.org/standards/ifc/ifc-schema-specifications/, namely:

- IFC2X3_TC1
- IFC4_ADD1
- IFC4_ADD2
- IFC4_ADD2_TC1
- IFC4x1
- IFC4
- IFC4x3_RC1
- IFC4_ADD2


## How to run this code?
If you simply want to run your computer on your device, you are advised to download
- the shaded executable JAR archive from the GitHub Release folder at https://github.com/pipauwel/IFCtoRDF/releases; or
- the shaded executable JAR archive from the Maven Central repository at https://search.maven.org/artifact/com.github.pipauwel/IFCtoRDF  

Both are identical, and include all necessary dependencies to be able to run the code out of the box.

This code does not have a Graphical User Interface (GUI). Run any one of the following commands in a command line interface (CLI) to generate an RDF graph in TTL format for the provided IFC-SPF files. These commands allow converting all ifc files in a directory (`--dir` flag) or just one specific file (no `--dir` flag), with a user-specific URI specified or not (`--baseURI` flag).

```
java -jar ifc2rdf-1.4.0-shaded.jar --baseURI https://www.myownwebspace.be/ --dir path/to/folder/
java -jar ifc2rdf-1.4.0-shaded.jar --dir path/to/folder/
java -jar ifc2rdf-1.4.0-shaded.jar path/to/file.ifc path/to/file.ttl
java -jar ifc2rdf-1.4.0-shaded.jar --baseURI http://www.test.be/ path/to/file.ifc path/to/file.ttl
```

The conversion process can be memory-intensive. 400MB files are fully loaded in memory, often twice, because of the use of a Jena RDF library, not to mention the IFC OWL ontology. It is therefore to reserve a sufficient amount of RAM memory to this Java process. You can do this by explicitly stating the optimal Java heap space to be used with the `-Xmx` and `-Xms` flags. For example, 8GB RAM is associated to this process in the below command.

```
java -Xmx8g -Xms8g -jar ifc2rdf-1.4.0-shaded.jar --baseURI https://www.myownwebspace.be/ --dir path/to/folder/
```

## How to re-use this code in your own Java code project?
This Java code is managed using [Maven](https://maven.apache.org/). If you plan to re-use this code, you are advised to do this through maven. The code was originally published as a Maven module in Maven Central (https://search.maven.org/artifact/com.github.pipauwel/IFCtoRDF). 

## ifcOWL compatibility
The output RDF graphs follow the ifcOWL ontology, of which a number of versions are available:

- https://standards.buildingsmart.org/IFC/DEV/IFC2x3/FINAL/OWL/
- https://standards.buildingsmart.org/IFC/DEV/IFC2x3/TC1/OWL/
- https://standards.buildingsmart.org/IFC/DEV/IFC4/ADD1/OWL/
- https://standards.buildingsmart.org/IFC/DEV/IFC4/ADD2/OWL/
- https://standards.buildingsmart.org/IFC/DEV/IFC4/ADD2_TC1/OWL/
- https://standards.buildingsmart.org/IFC/DEV/IFC4/FINAL/OWL/
- https://standards.buildingsmart.org/IFC/DEV/IFC4_1/OWL/

Each of these ontologies rely on the EXPRESS and LIST ontologies:
- https://w3id.org/express/
- https://w3id.org/list/

## Dependencies
Through maven, this code depends primarily on:
- jena-core v5.1.0
- jena-arq v5.1.0

You will need Java JDK 17 and Maven installed. After downloading the code from the Github repository, you need to run the below command to compile the code and download all necessary maven dependencies:

```
mvn compile
```

## Issues
Issues can be posted in https://github.com/pipauwel/IFCtoRDF/issues.

## Changes
A change log is available at [CHANGES.md](CHANGES.md). 

## Java API Documentation
The API Documentation is very limited, yet it is available at:
https://pipauwel.github.io/IFCtoRDF/0.4/

## License
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

See License details at [LICENSE](LICENSE).

## Contact
Want to know more? Contact:


Jyrki Oraskari  
Individualized Production, RWTH Aachen University
oraskari@ip.rwth-aachen.de  


Pieter Pauwels  
Eindhoven University of Technology  
p.pauwels@tue.nl  
