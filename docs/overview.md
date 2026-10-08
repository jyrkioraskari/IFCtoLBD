# From an IFC file to useful linked data

[Documentation home](README.md) · Next: [your first conversion](quick-start.md)

This page explains the idea before the controls. You do not need to know RDF or
SPARQL yet.

## The conversion in one picture

```text
┌──────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ IFC model            │    │ ifcOWL staging graph │    │ Linked Building Data │
│ walls, spaces,       │ -> │ the IFC schema and   │ -> │ BOT topology, product│
│ properties, geometry │    │ instances as RDF     │    │ types, PROPS and OPM │
└──────────────────────┘    └──────────────────────┘    └──────────────────────┘
          read                         map                         save/query
```

The middle graph is a working representation. It keeps IFC’s detailed structure
while the converter finds relationships and values. The final graph is smaller
and easier to query:

- **BOT** says how a site, building, storey, space, and element fit together.
- **Product/BEO classes** say that an element is a wall, door, beam, and so on.
- **PROPS** supplies relationship names for IFC properties.
- **OPM** can give a property its own identity and, at level 3, record a value
  state and when it was created.

Geometry is optional. Turning it off does not remove the building hierarchy or
ordinary properties.

## Walk through the two-wall model

The development-version [example IFC file](../examples/getting-started/building-inventory/model.ifc)
contains one storey and two walls. Its checked 2.54.1 output has this shape:

```text
Building storey "Level 04 - T.O. Fnd. Wall"
├── contains element → Wall 1
└── contains element → Wall 2
```

In Turtle, the identifiers are longer, but the relationships are direct:

```turtle
@prefix bot:  <https://w3id.org/bot#> .
@prefix beo:  <https://pi.pauwel.be/voc/buildingelement#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

<https://example.org/storey/level-04>
    a bot:Storey ;
    rdfs:label "Level 04 - T.O. Fnd. Wall" ;
    bot:containsElement <https://example.org/wall/1>,
                        <https://example.org/wall/2> .

<https://example.org/wall/1> a beo:Wall .
<https://example.org/wall/2> a beo:Wall .
```

The example uses short, readable identifiers to show the pattern; a real export
uses full [IRIs](glossary.md) derived
from the IFC model. The saved SPARQL query groups
the elements by storey and type. Its checked answer is one row: the named storey
contains two `beo:Wall` resources.

That is the practical value of the conversion: instead of navigating many IFC
relationship entities, an application can ask a short graph query. Continue
with the [desktop quick start](quick-start.md) to produce the graph, then
[use the triples](using-triples.md) to read and query it.
