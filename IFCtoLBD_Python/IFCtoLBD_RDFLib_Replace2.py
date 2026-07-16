# !/usr/bin/env python3

from rdflib import Graph
import json
from IFCtoLBD_wrapper import ConversionProperties, IFCtoLBDConverter, shutdown_jvm

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

props = ConversionProperties();
props.setHasGeometry(True);
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

replacements = {
    'http://lbd.arch.rwth-aachen.de/props#globalIdIfcRoot_attribute_simple': 'https://my.org/guid'
}
lbdconverter.setProperty_replace_map(replacements)

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

shutdown_jvm()

