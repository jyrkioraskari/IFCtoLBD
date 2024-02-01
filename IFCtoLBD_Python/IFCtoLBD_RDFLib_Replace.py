# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter
from org.linkedbuildingdata.ifc2lbd import ConversionProperties


#-------------------------------------------------------------------------------
# Name:        RDFLib with property replacement
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     01/02/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------

import java

props = ConversionProperties();
props.setHasGeometry(True);
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

replacements = java.util.HashMap()
replacements.put('http://lbd.arch.rwth-aachen.de/props#globalIdIfcRoot_attribute_simple', 'https://my.org/guid')
map = java.util.HashMap(replacements)
lbdconverter.setProperty_replace_map(map)

lbdconverter.convert("Duplex_A_20110505.ifc",props)


lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

q = """
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>

SELECT ?element ?guid WHERE {
    ?element <https://my.org/guid> ?guid .
}"""

# Apply the query to the graph and iterate through results
for r in g.query(q):
    print(r["element"]," guid ",r["guid"])

jpype.shutdownJVM()

