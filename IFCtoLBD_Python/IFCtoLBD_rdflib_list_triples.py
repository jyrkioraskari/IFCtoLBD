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
