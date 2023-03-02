# !/usr/bin/env python3

import jpype

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter
from org.apache.jena.query import QueryFactory, QueryExecutionFactory


# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

model=lbdconverter.convert("Duplex_A_20110505.ifc")
queryString = """PREFIX bot: <https://w3id.org/bot#>

SELECT ?building ?predicate ?object
WHERE {
   ?building a bot:Building .
   ?building ?predicate ?object
}"""

query = QueryFactory.create(queryString)
qexec = QueryExecutionFactory.create(query, model)
results = qexec.execSelect()
while results.hasNext() :
    soln = results.nextSolution()
    x = soln.get("building")
    print(x)

jpype.shutdownJVM()

