# !/usr/bin/env python3
# pip install matplotlib

import jpype
from rdflib import Graph
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['./jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")

import io
import numpy as np
import pydotplus
from IPython.display import display
from rdflib.tools.rdf2dot import rdf2dot
import matplotlib.pyplot as plt
from PIL import Image


#-------------------------------------------------------------------------------
# Name:        RDFLib Draw Graph
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     30/01/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


props = ConversionProperties()
props.setHasGeometry(False)
props.setHasBuildingProperties(False)
props.setHasBuildingElements(False)
props.setHasUnits(False)
props.setHasNonLBDElement(False)
props.setExportIfcOWL(False)

# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

lbdconverter.convert("Duplex_A_20110505.ifc",props)
lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

stream = io.StringIO()
rdf2dot(g, stream, opts = {display})
dg = pydotplus.graph_from_dot_data(stream.getvalue())
png_data=dg.create_png()


img = io.BytesIO(png_data)
img_pil = Image.open(img)
img_array = np.array(img_pil)
plt.imshow(img_array)
plt.show()