# !/usr/bin/env python3

import jpype

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter

jpype.java.lang.System.out.println("This was printed via System.out.println.")

lbdconverter = IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset",  3)

model=lbdconverter.convert("Duplex_A_20110505.ifc");

model.write(jpype.java.lang.System.out)
jpype.shutdownJVM()