# !/usr/bin/env python3

import jpype

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter

# Convert the IFC file into LBD level 3 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  3)

model=lbdconverter.convert("Duplex_A_20110505.ifc");
statements=model.listStatements();


# List all RDF subjects of the statements in the LBD Model
while statements.hasNext() :
        triple=statements.next()
        subject=triple.getSubject().toString()
        print(subject)

jpype.shutdownJVM()

