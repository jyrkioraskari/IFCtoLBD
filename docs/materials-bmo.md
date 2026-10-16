# IFC materials with BMO

The opt-in `materials-bmo` profile adds IFC material assignments to the normal
topology and product output. It works with IFC2x3 and IFC4, requires neither
geometry nor network access, and leaves all existing profiles unchanged.

## Convert a model

With the shaded converter JAR built, run:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --profile materials-bmo \
  --url https://example.com/building/ \
  --target_file materials.ttl \
  model.ifc
```

Turtle is the merged data view. To retain the named product, property,
diagnostics, validation, and manifest graphs, use a TriG output instead:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --profile materials-bmo \
  --url https://example.com/building/ \
  --target_file materials.trig \
  --hasTriG \
  model.ifc
```

The profile automatically applies its material SHACL shapes. Material
conversion warnings, including unsupported structures and unresolved units,
are available in the TriG diagnostics graph.

In the desktop application, choose the built-in **Materials and properties**
preset or enable **IFC materials (BMO)** under **Product / BOT**. Material export
requires building elements and properties and turns off Performance mode because
the reduced source graph does not retain the required IFC material relationships.

## Supported IFC material structures

The first increment supports:

- occurrence assignments through `IfcRelAssociatesMaterial`;
- direct `IfcMaterial` selections;
- `IfcMaterialLayerSet`, its ordered `IfcMaterialLayer` list, and repeated
  references to the same material;
- occurrence-specific `IfcMaterialLayerSetUsage`, including
  `LayerSetDirection`, `DirectionSense`, and `OffsetFromReferenceLine` when
  present; and
- material assignments inherited through `IfcRelDefinesByType`.

An occurrence-level material association overrides a type assignment. If the
occurrence association is unsupported or malformed, it is reported and the
converter does not silently fall back to the type assignment. Shared material
and layer-set definitions stay separate from occurrence-specific usage.
Optional IFC names, descriptions, categories, material references, and
thicknesses may be absent without aborting conversion.

Material lists, constituents and constituent sets, material profiles and
profile sets/usages, classification, bSDD enrichment, and EPD links are not yet
mapped. Unsupported material-select structures produce structured warnings;
the converter does not fabricate replacement materials, thicknesses, or
classifications. Layer order does not imply interior/exterior orientation.

## BMO and the IFCtoLBD extension

The mapping uses the Digital Construction BuildingMaterials 0.1 namespace
`https://w3id.org/digitalconstruction/BuildingMaterials#`. The verified source
is `v/0.1/bm.ttl` at revision
`b30a0a29e04c4f5d4a63e26a50987afce7d8492f`, with SHA-256
`b8ed0e82828ccc2700cc1c30252e0c1383bb2149821e63b43ed85ad925da7ca9`.
An unchanged copy is bundled for offline verification. Attribution and the CC
BY 4.0 licence notice are recorded in
[`THIRD_PARTY_PROVENANCE.md`](../THIRD_PARTY_PROVENANCE.md) and the packaged
`META-INF/NOTICE-BMO.md`.

Only terms verified in that source are used. `bmo:Material`,
`bmo:MaterialDefinition`, `bmo:LayerSet`, and `bmo:Layer` are classes;
`bmo:hasLayers`, `bmo:hasMaterial`, and `bmo:hasThickness` express the material
structure. The converter does not import the ontology into output or enable
unrestricted reasoning. In particular, it does not treat the `bmo:Concrete`
or `bmo:Steel` individuals as classes, use a string with the object property
`bmo:hasMaterialName`, or attach `bmo:hasMaterial` directly to an LBD element.

BMO does not define element assignments, ordered layer membership, usage
metadata, or source-assignment provenance. IFCtoLBD supplies those terms in its
project-controlled `https://w3id.org/ifctolbd/mapping#` namespace:

| Term | Meaning |
| --- | --- |
| `materialAssignment` | Existing LBD element to the selected assignment |
| `assignedMaterialDefinition` | Assignment or usage to a material definition or layer set |
| `assignedMaterial` | Direct assignment to its BMO material |
| `sourceIfcAssociation` | Assignment to its source `IfcRelAssociatesMaterial` |
| `sourceIfcTypeResource` | Inherited assignment to its source IFC type object |
| `materialAssignmentInherited` | Whether the assignment came from the type |
| `layerSetUsage` | Assignment to occurrence-specific usage metadata |
| `layerMembership` | Layer set to an ordered membership resource |
| `layer`, `position` | Membership to its layer and list position |
| `thicknessQuantity` | Layer to its QUDT value and source-value provenance |
| `layerSetDirection`, `directionSense`, `offsetFromReferenceLine` | Preserved usage values |

Positions are **one-based**, matching IFC list order. A membership IRI is
scoped to its layer set and position, so the same material or layer can be
reused without acquiring conflicting position values. Each set also links
directly to its layers with `bmo:hasLayers`.

## Thickness and identifiers

Within this application profile, every scalar `bmo:hasThickness` value is a
decimal in **metres**. The linked `qudt:QuantityValue` carries
`qudt:unit unit:M`, while provenance retains the original IFC numeric value and
unit information. IFC SI prefixes and supported linear conversion-based length
units are normalized. If the project length unit cannot be resolved, the source
value is retained on the quantity/provenance node and a warning is emitted;
neither a metre-valued `bmo:hasThickness` nor `unit:M` is asserted.
`bmo:hasUnit` is never attached to a layer because its BMO domain is
`opm:Property`.

Material resources, definitions, usages, and assignments are keyed by IFC
source identity, not by name. Repeating a conversion with the same base/model
scope and source identities is deterministic, and existing LBD element IRIs
are reused. Most non-root material structures depend on STEP entity numbers;
those numbers, and therefore their generated IRIs, are not guaranteed to remain
stable across IFC model revisions. Changing the conversion base or model scope
also changes generated identifiers.

## Example RDF

This abbreviated Turtle shows the merged product and property graphs for one
layer. Actual generated IRIs are model-scoped.

```turtle
@prefix bmo: <https://w3id.org/digitalconstruction/BuildingMaterials#> .
@prefix ifctolbd: <https://w3id.org/ifctolbd/mapping#> .
@prefix qudt: <http://qudt.org/schema/qudt/> .
@prefix unit: <http://qudt.org/vocab/unit/> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<https://example.com/building/wall-1>
    ifctolbd:materialAssignment <https://example.com/building/assignment-1> .

<https://example.com/building/assignment-1>
    a ifctolbd:MaterialAssignment ;
    ifctolbd:assignedMaterialDefinition <https://example.com/building/layer-set-1> ;
    ifctolbd:sourceIfcAssociation <https://example.com/building/ifc-association-1> .

<https://example.com/building/layer-set-1>
    a bmo:LayerSet ;
    bmo:hasLayers <https://example.com/building/layer-1> ;
    ifctolbd:layerMembership <https://example.com/building/membership-1> .

<https://example.com/building/membership-1>
    a ifctolbd:LayerMembership ;
    ifctolbd:position "1"^^xsd:long ;
    ifctolbd:layer <https://example.com/building/layer-1> .

<https://example.com/building/layer-1>
    a bmo:Layer ;
    bmo:hasMaterial <https://example.com/building/material-1> ;
    bmo:hasThickness 0.1 ;
    ifctolbd:thicknessQuantity <https://example.com/building/thickness-1> .

<https://example.com/building/material-1>
    a bmo:Material ;
    rdfs:label "Brick" .

<https://example.com/building/thickness-1>
    a qudt:QuantityValue ;
    qudt:numericValue 0.1 ;
    qudt:unit unit:M .
```

Run this query against the merged Turtle model (or a union view of the TriG
data graphs) to return the element, material name, one-based layer position,
metre-normalized thickness, and explicit unit:

```sparql
PREFIX bmo: <https://w3id.org/digitalconstruction/BuildingMaterials#>
PREFIX ifctolbd: <https://w3id.org/ifctolbd/mapping#>
PREFIX qudt: <http://qudt.org/schema/qudt/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?element ?materialName ?position ?thickness ?unit
WHERE {
  ?element ifctolbd:materialAssignment ?assignment .
  ?assignment ifctolbd:assignedMaterialDefinition ?layerSet .
  ?layerSet a bmo:LayerSet ;
            ifctolbd:layerMembership ?membership .
  ?membership ifctolbd:position ?position ;
              ifctolbd:layer ?layer .
  ?layer bmo:hasMaterial ?material ;
         bmo:hasThickness ?thickness ;
         ifctolbd:thicknessQuantity ?quantity .
  ?material rdfs:label ?materialName .
  ?quantity qudt:numericValue ?thickness ;
            qudt:unit ?unit .
}
ORDER BY ?element ?position
```

This query intentionally returns normalized layered materials. Direct
single-material assignments have no layer position or thickness and therefore
do not match it.

## Validation and ontology cautions

The bundled `shacl/materials-bmo-v1.0.0.ttl` shapes check assignment target
types, layer/material links, unique one-based positions within each set,
normalized numeric quantities and metre units, and useful diagnostic codes and
messages when source data is missing or unsupported.

The pinned BMO source declares `bmo:hasConstituents` twice with different
ranges (`bmo:Material` and `bmo:Constituent`). It also gives several named
material types questionable classifications: cement stone, ceramic, and glass
are typed as `OrganicaMaterial`, while polymer and wood are typed as
`InorganicNonMetallicMaterial`. IFCtoLBD does not repair or reason over these
axioms.
