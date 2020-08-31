# IFCtoLBD
Version 0.1 - branched from version 1.74 of https://github.com/jyrkioraskari/IFCtoLBD
Author: Pieter Pauwels

This repository is a considerably adapted and self-moderated branch from the IFCtoLBD repository by Jyrki Oraskari at https://github.com/jyrkioraskari/IFCtoLBD/. It does not have a user interface, nor does it have a mapping step to the OWL version of IFC, nor does it provide the diversity of options provided by the mother repository. This repository is instead a simple one-to-one command line based conversion tool that takes in .ifc files and generates .ttl files in its simplest version, with the use of a number of ontologies:

- Building Topology Ontology (BOT)
- Building Element Ontology (BE): https://pi.pauwel.be/voc/buildingelement
- Distribution Element Ontology (MEP): https://pi.pauwel.be/voc/distributionelement

There is no ontology associated to the PROPS prefix / namespace. This might be a future potential extension.

__NOTE: This code is under active development and can still change significantly. Also, conversion is not exhaustive and/or complete. Should you want particular items to change, please file an issue request.__

## How to run this code?
If you simply want to run your computer on your device, you are advised to download
- the shaded executable JAR archive from the GitHub Release folder at https://github.com/pipauwel/IFCtoLBD/releases; or
- the shaded executable JAR archive from the Maven Central repository at https://search.maven.org/artifact/com.github.pipauwel/IFCtoLBD  

Both are identical, and include all necessary dependencies to be able to run the code out of the box.

This code does not have a Graphical User Interface (GUI). Run any one of the following commands in a command line interface (CLI) to generate an RDF graph in TTL format for the provided IFC-SPF files. These commands allow converting an ifc file with a user-specific URI specified or not (`--baseURI` flag). It is additionally possible to exclude the building elements (add --excludeElements flag) and/or its properties (add --excludeProps flag).

```
java -jar IFCtoLBD-0.1-SNAPSHOT-shaded.jar path/to/file.ifc path/to/file.ttl
java -jar IFCtoLBD-0.1-SNAPSHOT-shaded.jar --baseURI https://www.myownwebspace.be/ path/to/file.ifc path/to/file.ttl
java -jar IFCtoLBD-0.1-SNAPSHOT-shaded.jar --baseURI https://www.myownwebspace.be/ path/to/file.ifc path/to/file.ttl --excludeElements --excludeProps
```

## How to re-use this code in your own Java code project?
This Java code is managed using [Maven](https://maven.apache.org/). If you plan to re-use this code, you are advised to do this through maven. The code is published as a Maven module in Maven Central (https://search.maven.org/artifact/com.github.pipauwel/IFCtoLBD). Therefore, you can directly include and use this code by adding the following lines to your `pom.xml` file.

```
<dependency>
  <groupId>com.github.pipauwel</groupId>
  <artifactId>IFCtoLBD</artifactId>
  <version>0.1</version>
</dependency>
```

## Dependencies
Through maven, this code depends primarily on:
- IFCtoRDF v0.4

## Access to source code
All source code is accessible through the [IFCtoLBD GitHub repository](https://github.com/pipauwel/IFCtoLBD/) in the master branch. Anyone is free to fork the repository, make changes, and potentially suggest updates and changes through Git pull requests. 

You will need Java JDK and Maven installed. After downloading the code from the Github repository, you need to run the below command to compile the code and download all necessary maven dependencies:

```
mvn compile
```

## Issues
Issues can be posted in https://github.com/pipauwel/IFCtoLBD/issues.

## License
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

See License details at [LICENSE](LICENSE).

## Contact
Want to know more? Contact:

Pieter Pauwels  
Eindhoven University of Technology  
p.pauwels@tue.nl  
