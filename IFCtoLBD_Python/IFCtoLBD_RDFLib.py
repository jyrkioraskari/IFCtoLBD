# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['./jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter
from org.linkedbuildingdata.ifc2lbd import ConversionProperties


#-------------------------------------------------------------------------------
# Name:        RDFLib access
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     29/01/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


props = ConversionProperties();
props.setHasGeometry(True);
# Convert the IFC (Industry Foundation Classes) file into LBD (Linked Building Data), OPM (Ontology for Property Management) level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

# Convert the specified IFC file ("Duplex_A_20110505.ifc") using the provided properties (props)
lbdconverter.convert("Duplex_A_20110505.ifc", props)

# Export the output as JSON-LD
lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
# Parse it into the Python native rdflib Graph
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

q = """
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>

SELECT ?element WHERE {
    ?element a beo:Door.
}"""

# Apply the query to the graph and iterate through results
for r in g.query(q):
    print(r["element"])

jpype.shutdownJVM()

