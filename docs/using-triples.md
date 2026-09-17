# From IFC to useful triples

[Documentation home](README.md) · Previous: [first conversion](quick-start.md)

## What is Linked Building Data?

You know IFC as a way of exchanging a building model. Linked Building Data
(LBD) expresses building information as a graph with web identifiers, using
shared vocabularies. This lets applications connect a building element to
information from other sources without putting everything into one IFC file.
See the [W3C LBD Community Group](https://www.w3.org/community/lbd/) for the
community’s work and resources.

An **ontology** defines terms and their relationships. The
[Building Topology Ontology (BOT)](https://w3c-lbd-cg.github.io/bot/) provides
terms for sites, buildings, storeys, spaces, and elements. Additional
vocabularies describe element types, properties, units, and geometry.
The converter’s output depends on the IFC content and your export settings;
not every IFC detail appears in every LBD export.

## Read one triple

RDF describes facts as **subject → predicate → object**. This small, illustrative
Turtle graph is independent of your actual model’s identifiers:

```turtle
@prefix inst: <https://example.com/building/> .
@prefix bot: <https://w3id.org/bot#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

inst:building a bot:Building .
inst:building bot:hasStorey inst:groundFloor .
inst:groundFloor a bot:Storey .
inst:groundFloor rdfs:label "Ground floor" .
```

`inst:building bot:hasStorey inst:groundFloor` says that the building has that
storey. The subject and object identify resources; `"Ground floor"` is a literal
text value. A prefix abbreviates a full identifier (IRI), so `inst:groundFloor`
means `https://example.com/building/groundFloor`. The shorthand `a` means
`rdf:type`: it tells us what kind of resource something is.

Turtle and JSON-LD serialize RDF; the graph is the data, and these are ways to
write it. The [W3C RDF Primer](https://www.w3.org/TR/rdf11-primer/) explains
resources, literals, and graphs in more depth.

## Ask your first question with SPARQL

SPARQL matches patterns in the graph. Open the desktop’s **Query** tool after
conversion, where available, or use the [Python RDFLib example](python_examples.md)
to run this query against your exported Turtle:

```sparql
PREFIX bot: <https://w3id.org/bot#>
SELECT ?building ?storey WHERE {
  ?building a bot:Building ;
            bot:hasStorey ?storey .
}
```

Each result row contains a building and one of its storeys. Names beginning
with `?` are variables filled from matching triples. To discover what kinds of
resources were exported:

```sparql
SELECT ?type (COUNT(DISTINCT ?resource) AS ?count) WHERE {
  ?resource a ?type .
}
GROUP BY ?type
ORDER BY DESC(?count)
```

To inspect a particular element, copy its full IRI from the output or query
results and replace the example below:

```sparql
SELECT ?predicate ?object WHERE {
  <https://example.com/building/your-element> ?predicate ?object .
}
```

If a query returns no rows, inspect the exported types and predicates. Check
filters, property settings, and whether you exported products or properties
into separate files. Load those files into the same graph for queries that
combine them. A plain RDF query does not automatically infer every relationship
implied by an ontology.

## Work with properties and identifiers

Property levels 1, 2, and 3 offer different representations of property values.
Start with level 1 for simpler exploration; levels 2 and 3 add structure, so a
query for a direct value may need to follow intermediate resources. Property
mode, blank-node settings, and units also affect the graph. Inspect your export
before assuming a fixed property path. The community’s
[property modelling presentation](https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf)
provides background on the levels.

The converter maps IFC Tag attributes to the compatibility name `batid`.
A tag can be absent or reused; it is not the IFC GlobalId. Use the exported
GlobalId and full resource IRI when identifying elements, and check the actual
namespace and property representation in your file. For comparing model
revisions in the structured API, see the stable identity options in the
[development guide](development.md).

## Use the graph in your own work

You can load Turtle into RDFLib (Python), Apache Jena (Java), or a triple store
with a SPARQL endpoint. RDFLib and Jena let you inspect, query, and add triples
locally. A triple store is useful when several applications need to query the
same dataset. Loading a graph into a store and configuring access are separate
steps from converting an IFC file.

To connect external information, refer to the element’s full IRI in your own
triples. For example, your application can associate a maintenance record with
that element. Use a namespace you control and keep the model’s identity strategy
consistent across conversions. Changing the base URI can change resource IRIs.

Continue with [Python](python_examples.md) or [Java](java_examples.md).
For domain-specific enrichment, see [supply-chain and sustainability output](supply-chain-and-sustainability.md).
