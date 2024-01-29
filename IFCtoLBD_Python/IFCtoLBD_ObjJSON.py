# !/usr/bin/env python3

import jpype
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter
from org.linkedbuildingdata.ifc2lbd import ConversionProperties


props = ConversionProperties();
props.setHasGeometry(True);
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

lbdconverter.convert("Duplex_A_20110505.ifc",props)
lbd_json = str(lbdconverter.getObjJSON())
lbd_objs = json.loads(lbd_json);

for o in lbd_objs:
  print(o["element"])
  print(o["obj"])   # base64 obj
jpype.shutdownJVM()

