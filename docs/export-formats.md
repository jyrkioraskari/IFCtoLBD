# Export formats, split files, and ICDD

[Documentation home](README.md) · [Your first conversion](quick-start.md)

Start with Turtle unless another system requires a different format. The
options on this page are useful when a downstream tool needs named graphs,
separate files, or an ISO container.

## One RDF graph

- **Turtle (`.ttl`)** is compact and readable. It is the best first export.
- **JSON-LD (`.jsonld`)** carries the same RDF graph in JSON syntax. It is not
  IFC/JSON, which is an IFC input format.
- **TriG (`.trig`)** can store several named RDF graphs in one text file.

## Separate Turtle files

When separate building-element and property files are enabled, the converter
partitions the result without copying a triple into more than one output:

| File | Contents |
| --- | --- |
| Main `.ttl` file | BOT topology and spatial resources |
| `_building_elements.ttl` | Product/BEO elements and their IFC attributes |
| `_element_properties.ttl` | PROPS/OPM property and quantity-set data |

Use this form when different systems own or load these parts separately. Keep
the files together: their IRIs connect them into one logical graph.

## ICDD package

ICDD (`.icdd`) creates an ISO 21597-1 ZIP container. It includes the original
IFC file and separate Turtle documents for:

- BOT topology, including IFC attributes of spatial resources;
- Product/BEO building elements and their IFC attributes;
- geometry and links from elements to geometry; and
- PROPS/OPM properties and quantities.

The RDF files do not repeat triples: each triple is written to one document.
`Index.rdf` describes the documents. The package also contains
the standard `Ontology resources`, `Payload documents`, and `Payload triples`
folders. `Ontology resources/bot.ttl` is an offline BOT 0.3.2 copy, while
`Ontology resources/props.ttl` declares the property predicates used by this
export.

In an ICDD package, geometry is in `lbd/geometry.ttl`; the property document
contains only property-set and quantity-set content. This dedicated geometry
file is an ICDD feature, not an extra file created by an ordinary split Turtle
export.

ICDD and multi-file partitioning are advanced choices. They do not improve the
meaning of a small first conversion. Use them when another tool requires the
package or when different teams manage different parts of the data.
