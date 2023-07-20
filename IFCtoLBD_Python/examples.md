
## IFCtoLBD Python Implementation

The example implementations can be found in the IFCtoLBD_Python  subfolder

### Installation
```
pip install JPype1
pip install rdflib
```

### Small example

With this code you can list all RDF subjects of the triples in the LBD model of the Duplex_A_20110505.ifc file. 

```
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

while statements.hasNext() :
        triple=statements.next()
        subject=triple.getSubject().toString()
        print(subject)

jpype.shutdownJVM()
```

### List LBD triples
Here is a short Python example how to convert an IFC model and then use the rdflib Python library to 
list the statements in the mode.

```
# !/usr/bin/env python3
import pprint

import jpype
from rdflib import Graph, Literal, RDF, URIRef
# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath=['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter

# Convert the IFC file into LBD level 3 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/", 3)

model = lbdconverter.convert("Duplex_A_20110505.ifc");
statements = model.listStatements();

g = Graph()

# Copy triples to the Python rdflib library
# Apache Jena  operations:
# -------------------
while statements.hasNext():
    triple = statements.next()
    rdf_subject = URIRef(triple.getSubject().toString())
    rdf_predicate = URIRef(triple.getPredicate().toString())
    if triple.getObject().isLiteral():
        rdf_object = Literal(triple.getObject().toString())
    else:
        rdf_object = URIRef(triple.getObject().toString())
    g.add((rdf_subject, rdf_predicate, rdf_object))

# rdflib operations:
# -------------------
for stmt in g:
    pprint.pprint(stmt)
jpype.shutdownJVM()

```
### Save LBD triples as Turtle formatted into a file

A sample code to convert a model using Python and write the output as a Turtle formatted file. 

```
# !/usr/bin/env python3
import pprint

import jpype
from rdflib import Graph, Literal, RDF, URIRef
# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath=['jars/*'])

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter

# Convert the IFC file into LBD level 3 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/", 3)

model = lbdconverter.convert("Duplex_A_20110505.ifc");
statements = model.listStatements();

g = Graph()

# Copy triples to the Python rdflib library
# Apache Jena  operations:
# -------------------
while statements.hasNext():
    triple = statements.next()
    rdf_subject = URIRef(triple.getSubject().toString())
    rdf_predicate = URIRef(triple.getPredicate().toString())
    if triple.getObject().isLiteral():
        rdf_object = Literal(triple.getObject().toString())
    else:
        rdf_object = URIRef(triple.getObject().toString())
    g.add((rdf_subject, rdf_predicate, rdf_object))

# rdflib operations:
# -------------------

g.serialize(destination="output.ttl")
jpype.shutdownJVM()

```

### Use a SPARQL query to query BOT elements of a converted LBD model

```
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
```



https://rdflib.readthedocs.io/en/stable/gettingstarted.html  can be used to access the created triples. 
