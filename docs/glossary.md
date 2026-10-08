# Glossary

[Documentation home](README.md)

**AABB — Axis-aligned bounding box.** A simple box whose sides follow the X, Y,
and Z axes. IFCtoLBD can use these boxes for lightweight geometry and candidate
interface calculations.

**BOT — Building Topology Ontology.** An RDF vocabulary for sites, buildings,
storeys, spaces, elements, zones, and the relationships between them.

**bSDD — buildingSMART Data Dictionary.** An online service and identifier
system for construction concepts, properties, and classifications.

**EPD — Environmental Product Declaration.** A standardized report of a
product’s environmental impacts. IFCtoLBD can link product information to EPD
data; it does not calculate an EPD.

**GS1.** A family of global identification and data-sharing standards used for
products, companies, locations, and logistics.

**ICDD — Information Container for linked Document Delivery.** The ISO 21597
container format for packaging documents and linksets. An IFCtoLBD ICDD export
can carry the original IFC and partitioned RDF files in one ZIP-based package.

**IFC — Industry Foundation Classes.** buildingSMART’s open data model for
buildings and infrastructure. IFC files can describe objects, properties,
geometry, and relationships.

**ifcOWL.** An RDF/OWL representation of the IFC schema and model instances.
IFCtoLBD uses an ifcOWL graph as an internal staging form before producing the
simpler LBD graph.

**IRI — Internationalized Resource Identifier.** The globally unique name of
an RDF resource or predicate. It is the international form of a URI.

**LBD — Linked Building Data.** Building information represented as linked
data, usually an RDF graph that uses shared vocabularies such as BOT.

**OPM — Ontology for Property Management.** A vocabulary for properties, their
states, and related metadata such as when a state was generated.

**PROPS.** The W3C LBD community vocabulary pattern used by IFCtoLBD for
property predicates derived from IFC names.

**QUDT — Quantities, Units, Dimensions and Types.** A vocabulary for describing
quantities, units, dimensions, and data types in RDF.

**RDF — Resource Description Framework.** A graph data model made of subject,
predicate, and object triples.

**SHACL — Shapes Constraint Language.** A W3C language for checking an RDF
graph against rules. A successful SHACL report checks the selected rules; it is
not a full certification of the source IFC model.

**SPARQL.** The standard query language for RDF graphs.

**Turtle.** A readable text syntax for RDF, normally saved as `.ttl`.
