# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json
# Enable Java imports
import jpype.imports
# Pull in types
from jpype.types import *

#jpype.startJVM(classpath = ['./jars/*'])

# if the relative classes are not functioning, replace these with absolute paths.  You can also try the "./jars/..." style.
jpype.startJVM(classpath = ['jars/antlr-complete-3.5.2.jar','jars/commonj.sdo-2.1.1.jar','jars/plexus-classworlds-2.5.2.jar','jars/caffeine-3.1.8.jar','jars/jena-tdb-4.10.0.jar','jars/jcl-over-slf4j-1.7.36.jar','jars/slf4j-api-2.0.4.jar','jars/commons-collections4-4.4.jar','jars/jakarta.jws-api-1.1.1.jar','jars/jakarta.activation-api-1.2.1.jar','jars/antlr-3.5.2.jar','jars/jaxws-rt-2.3.2.jar','jars/jakarta.json-api-2.1.3.jar','jars/javax.json-1.0.4.jar','jars/maven-artifact-3.3.9.jar','jars/fastutil-8.2.2.jar','jars/maven-settings-3.3.9.jar','jars/javax.servlet-api-3.1.0.jar','jars/org.eclipse.core.filesystem-1.3.100.jar','jars/vecmath-1.5.2.jar','jars/jakarta.mail-api-1.6.3.jar','jars/commons-compress-1.24.0.jar','jars/gmbal-4.0.0.jar','jars/jsr250-api-1.0.jar','jars/org.eclipse.equinox.preferences-3.4.1.jar','jars/commons-codec-1.16.0.jar','jars/pfl-asm-4.0.1.jar','jars/org.eclipse.persistence.asm-2.7.4.jar','jars/collection-0.7.jar','jars/java-getopt-1.0.13.jar','jars/jena-dboe-index-4.10.0.jar','jars/ifc-to-lbd-2.43.5.jar','jars/gson-2.8.9.jar','jars/ST4-4.0.8.jar','jars/istack-commons-runtime-3.0.8.jar','jars/javassist-3.24.1-GA.jar','jars/org.eclipse.core.commands-3.6.0.jar','jars/jackson-core-2.12.6.jar','jars/jena-iri-4.10.0.jar','jars/stringtemplate-3.2.1.jar','jars/jakarta.annotation-api-1.3.4.jar','jars/guava-mini-0.1.4.jar','jars/titanium-json-ld-1.3.2.jar','jars/jsr305-3.0.2.jar','jars/org.eclipse.persistence.core-2.7.4.jar','jars/stax-ex-1.8.1.jar','jars/aether-impl-1.0.2.v20150114.jar','jars/junit-4.10.jar','jars/checker-qual-3.41.0.jar','jars/pfl-dynamic-4.0.1.jar','jars/jakarta.xml.ws-api-2.3.2.jar','jars/org.eclipse.persistence.sdo-2.7.4.jar','jars/aether-connector-basic-1.0.2.v20150114.jar','jars/jakarta.xml.bind-api-2.3.2.jar','jars/org.eclipse.core.resources-3.7.100.jar','jars/mxparser-1.2.2.jar','jars/httpclient-cache-4.5.14.jar','jars/shared-1.5.184.jar','jars/org.eclipse.equinox.common-3.6.0.jar','jars/guava-33.0.0-jre.jar','jars/jackson-databind-2.12.7.1.jar','jars/jakarta.persistence-api-2.2.2.jar','jars/commons-lang-2.6.jar','jars/jakarta.json-2.0.1.jar','jars/jaxws-eclipselink-plugin-2.3.2.jar','jars/ha-api-3.1.12.jar','jars/org.eclipse.equinox.app-1.3.100.jar','jars/org.eclipse.core.contenttype-3.4.100.jar','jars/commons-lang3-3.14.0.jar','jars/org.eclipse.core.runtime-3.7.0.jar','jars/jena-shacl-4.10.0.jar','jars/rtree-multi-0.1.jar','jars/policy-2.7.6.jar','jars/aether-api-1.0.2.v20150114.jar','jars/aether-transport-file-1.0.2.v20150114.jar','jars/log4j-slf4j2-impl-2.19.0.jar','jars/xstream-1.4.20.jar','jars/stax-api-1.0.1.jar','jars/httpmime-4.5.7.jar','jars/org.eclipse.persistence.moxy-2.7.4.jar','jars/commons-logging-1.2.jar','jars/pluginbase-1.5.184.jar','jars/jackson-annotations-2.12.7.jar','jars/yasson-3.0.3.jar','jars/jena-rdfpatch-4.10.0.jar','jars/maven-model-builder-3.3.9.jar','jars/pfl-basic-4.0.1.jar','jars/xmlbeans-2.6.0.jar','jars/protobuf-java-3.24.3.jar','jars/commons-text-1.11.0.jar','jars/maven-repository-metadata-3.3.9.jar','jars/ifcplugins-0.0.101.jar','jars/failureaccess-1.0.2.jar','jars/org.eclipse.sisu.plexus-0.3.2.jar','jars/maven-plugin-api-3.3.9.jar','jars/org.eclipse.emf.common-2.15.0.jar','jars/cdi-api-1.0.jar','jars/jena-tdb2-4.10.0.jar','jars/maven-builder-support-3.3.9.jar','jars/hamcrest-core-1.1.jar','jars/pfl-tf-4.0.1.jar','jars/jena-dboe-storage-4.10.0.jar','jars/commons-cli-1.5.0.jar','jars/httpcore-4.4.13.jar','jars/jakarta.xml.soap-api-1.4.1.jar','jars/httpclient-4.5.13.jar','jars/jena-dboe-base-4.10.0.jar','jars/libthrift-0.19.0.jar','jars/management-api-3.2.1.jar','jars/stax2-api-4.1.jar','jars/guice-4.0-no_aop.jar','jars/jena-shaded-guava-4.8.0.jar','jars/plexus-component-annotations-1.6.jar','jars/jaxb-runtime-2.3.2.jar','jars/org.eclipse.emf.ecore-2.15.0.jar','jars/org.eclipse.osgi-3.7.1.jar','jars/jena-dboe-trans-data-4.10.0.jar','jars/jena-shex-4.10.0.jar','jars/plexus-utils-3.0.22.jar','jars/gunit-3.5.2.jar','jars/jena-dboe-transaction-4.10.0.jar','jars/jaxb-jxc-2.3.2.jar','jars/sdo-eclipselink-plugin-2.3.2.jar','jars/xmlpull-1.1.3.1.jar','jars/streambuffer-1.5.7.jar','jars/org.eclipse.core.expressions-3.4.300.jar','jars/org.eclipse.core.jobs-3.5.100.jar','jars/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar','jars/jena-rdfconnection-4.10.0.jar','jars/maven-settings-builder-3.3.9.jar','jars/org.eclipse.text-3.5.101.jar','jars/javax.inject-1.jar','jars/log4j-core-2.19.0.jar','jars/maven-model-3.3.9.jar','jars/plexus-cipher-1.4.jar','jars/RoaringBitmap-1.0.0.jar','jars/saaj-impl-1.5.1.jar','jars/IFCtoLBD_Desktop.jar','jars/antlr-runtime-3.5.2.jar','jars/mimepull-1.9.11.jar','jars/jaxb-xjc-2.3.2.jar','jars/plexus-interpolation-1.21.jar','jars/woodstox-core-5.1.0.jar','jars/jena-core-4.10.0.jar','jars/j2objc-annotations-2.8.jar','jars/ifc_to_lbd_geometry-2.43.5.jar','jars/log4j-api-2.19.0.jar','jars/maven-aether-provider-3.3.9.jar','jars/txw2-2.3.2.jar','jars/parsson-1.1.5.jar','jars/org.eclipse.equinox.registry-3.5.101.jar','jars/plexus-sec-dispatcher-1.3.jar','jars/org.eclipse.sisu.inject-0.3.2.jar','jars/jaxws-tools-2.3.2.jar','jars/jena-base-4.10.0.jar','jars/aopalliance-1.0.jar','jars/FastInfoset-1.2.16.jar','jars/error_prone_annotations-2.23.0.jar','jars/org.eclipse.emf.ecore.xmi-2.15.0.jar','jars/pfl-basic-tools-4.0.1.jar','jars/picocli-4.7.5.jar','jars/jena-arq-4.10.0.jar','jars/aether-transport-http-1.0.2.v20150114.jar','jars/buildingsmartlibrary-1.0.13.jar','jars/commons-io-2.11.0.jar','jars/commons-csv-1.10.0.jar','jars/aether-spi-1.0.2.v20150114.jar','jars/aether-util-1.0.2.v20150114.jar','jars/jsonld-java-0.13.4.jar','jars/pfl-tf-tools-4.0.1.jar','jars/antlr-2.7.7.jar','jars/ifc2rdf-1.3.2.jar','jars/org.eclipse.jdt.core-3.10.0.jar','jars/maven-core-3.3.9.jar'])


IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")

#-------------------------------------------------------------------------------
# Name:        RDFLib access
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     13/03/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------

#   ConversionProperties:
#	setHasBuildingElements(boolean)              # if set False, no building elements are included
#	setHasSeparateBuildingElementsModel(boolean) # If True, a separate file for the building elements is created
#	setHasBuildingProperties(boolean)      # If false, property sets are not included
#	setHasSeparatePropertiesModel(boolean)  # If true, properties are written into a separate file
#	setHasGeolocation(boolean)   # If geolocation is included
#	setHasGeometry(boolean)      # If geometry is included
#	setExportIfcOWL(boolean)      # if the BOT elements are linked with ifcOWL elements and ifcOWL graph is created into a separate file
#	setHasUnits(boolean)              #  if the units for the values are included  (Level 2 ja Level 3 )
#	setHasBoundingBoxWKT(boolean)  # If the bounding boxes of the element are expressed as Well Known Text
#	setHasHierarchicalNaming(boolean)  # Affects how the URLs are formed
#	setHasPerformanceBoost(boolean)    #  If set True, the converter tries to use conversion results of previous conversions and  filters out 
                                                                                 #  data that is not used in the calculation later
props = ConversionProperties()
props.setHasGeometry(True)
props.setExportIfcOWL(False)
props.setHasPerformanceBoost(False)

# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://www.econom.one/",  1)

selected_types = "[\"Beam\", \"Wall\", \"Member\", \"Slab\", \"Stair\", \"Railing\"]"
lbdconverter.setSelected_types(selected_types)   # Exported elements can be selected using the JSON list syntax.

lbdconverter.setHasSimplified_properties(True)    # More clear naming,  but not recommended if rules or inferencing models are used (OWL full)
lbdconverter.setHasNonLBDElement(False)            #  Filters out IFC elements not specified in Linked Building Data ontologies.

# Model to be read and selected properties
lbdconverter.convert("AC20-FZK-Haus.ifc",props)

lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

#  Just list the triples
print(g.serialize(format="turtle"))

jpype.shutdownJVM()
