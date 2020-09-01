# IFCtoRDF Change Log

## Current Development 0.5-SNAPSHOT

## Release 0.4 (2020-08-29)
- added baseURI option at start up
- moved functions to separate IfcParser Class for better reusability
- added conversion and files for IFC4x3_RC1

## Release 0.3 (2019-07-31)
- included integration with Zenodo for the generation of DOIs per release
- added support for IFC4x1 - conversion of instance data
- mvn unit testing with sample files covering most of IFC's peculiarities
- changed namespace and output to http://standards.buildingsmart.org/IFC/DEV/
- added conversion and files for IFC4_ADD2 and IFC4_ADD2_TC1
- resolved bug in loading imported ontologies, inherent to the later version of Jena
- LOG to file
- added conversion and files for IFC4_1 (IfcAlignment)
- added test files and expected output

## Release 0.2 (2018-01-11)
 - alternative EXPRESStoOWL library

## Release 0.1 (2017-06-08)
 - change to buildingsmart-tech namespace
 - Use maven-java-formatter-plugin to format all code
 - Mavenize codebase
