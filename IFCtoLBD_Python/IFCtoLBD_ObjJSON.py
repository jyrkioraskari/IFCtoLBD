# !/usr/bin/env python3

import jpype
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['./jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")

#-------------------------------------------------------------------------------
# Name:        Direct access as Python objects
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
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

lbdconverter.convert("Duplex_A_20110505.ifc",props)
lbd_json = str(lbdconverter.getObjJSON("""
                    PREFIX bot: <https://w3id.org/bot#>
                    PREFIX fog: <https://w3id.org/fog#>

                    JSON { 'element' : ?element,'obj': ?obj } WHERE {
                      ?element a bot:Element .
                      ?element <https://w3id.org/omg#hasGeometry> ?g .
                      ?g fog:asObj_v3.0-obj ?obj
                    }"""))
lbd_objs = json.loads(lbd_json);

for o in lbd_objs:
  print(o["element"])
  print(o["obj"])   # base64 obj
jpype.shutdownJVM()

