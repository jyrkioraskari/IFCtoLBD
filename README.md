# IFCtoLBD
Version 1.74 - branched

Contributors: Jyrki Oraskari, Mathias Bonduel, Kris McGlinn, Pieter Pauwels 

This repository is a considerably adapted and self-moderated branch from the IFCtoLBD repository by Jyrki Oraskari at https://github.com/jyrkioraskari/IFCtoLBD/. It does not have a user interface, nor does it have a mapping step to the OWL version of IFC, nor does it provide the diversity of options provided by the mother repository. This repository is instead a simple one-to-one command line based conversion tool that takes in .ifc files and generates .ttl files in its simplest version, with the use of a number of ontologies:

- Building Topology Ontology (BOT)
- Building Element Ontology (BE): https://pi.pauwel.be/voc/buildingelement
- Distribution Element Ontology (MEP): https://pi.pauwel.be/voc/distributionelement

There is no ontology associated to the PROPS prefix / namespace. This might be a future potential extension.

## Installing
The repository requires that the IFCtoRDF code is installed in version 0.4. This code is available at https://github.com/pipauwel/IFCtoRDF. You need to download this code and install it with maven using `mvn install`.

Maven can be downloaded from: https://maven.apache.org/download.cgi.

When the IFCtoRDF code is installed in your machine, you can install the IFCtoLBD code:

1. Make sure that the JAVA_HOME environment variable point to the JAVA JDK directory. JRE is not enough.
2. Download the IFCtoLBD code from this repository
3. Start the command line interface in the home directory of this repository
4. Run `mvn install`
5. Run `mvn compile`

This will lead to a resulting JAR file that you can execute in a command line window.

## Run
Execute the following command in a command line interface:

```
java -jar IFCtoLBDConverter.jar pathToInputFile/yourIFCFile.ifc http://uribase pathToInputFile/youroutputTTLFile.ttl
```
 
## License
This project is released under the open source [GNU Affero General Public License v3](http://www.gnu.org/licenses/agpl-3.0.en.html).

