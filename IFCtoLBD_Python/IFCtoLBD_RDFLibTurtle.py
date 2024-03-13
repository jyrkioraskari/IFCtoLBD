# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json
# Enable Java imports
import jpype.imports
# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

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
