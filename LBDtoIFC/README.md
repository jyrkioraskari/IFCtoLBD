# LBDtoIFC

`LBDtoIFC` reconstructs an IFC4 STEP model from Linked Building Data (RDF). It
is a standalone Java 21/Maven project and is intentionally not part of the root
IFCtoLBD reactor.

## What is reconstructed

- `bot:Site`, `bot:Building`, `bot:Storey`, and `bot:Space`
- BOT aggregation (`bot:hasBuilding`, `bot:hasStorey`, `bot:hasSpace`)
- `bot:containsElement` spatial containment and `bot:hasSubElement` decomposition
- common BEO classes such as walls, slabs, doors, windows, beams, columns,
  roofs, stairs, railings, footings, coverings, and furnishings
- `lbd:globalId` and the legacy
  `props:globalIdIfcRoot_attribute_simple` value when it is a valid IFC GUID
- labels, IFC object type/long name attributes, door/window dimensions, element
  tags, and literal `props:*_simple` values
- embedded `fog:asObj_v3.0-obj` triangle meshes and verified local
  `geometry:artifact` OBJ files as IFC4 tessellated body geometry

Unknown element classes become `IfcBuildingElementProxy`. Missing site,
building, or storey levels are synthesized so that the output remains a usable
IFC project. Generated GUIDs are deterministic for a stable RDF resource IRI.

## Geometry

IFC-to-LBD conversion is not generally reversible. Typical LBD graphs contain
semantic topology and properties but not the original IFC representation tree,
placements, profiles, materials, or constructive geometry. When IFCtoLBD's OBJ
geometry is present, this converter preserves its vertices and triangle faces as
an IFC4 `IfcTriangulatedFaceSet` in a `Body`/`Tessellation` representation. The
result accurately preserves the available surface mesh, but it is not the
original parametric IFC solid.

Embedded Base64 OBJ is preferred. A linked artifact with media type `model/obj`
is loaded only from `--artifact-root` (the input file's directory by default),
and its SHA-256 is checked when supplied. Remote artifact fetching is not
performed. OBJ coordinates are interpreted as the metre-based, model-wide
coordinates emitted by IFCtoLBD; element placements remain identity transforms.
Meshes are bounded to 128 MiB, 10 million vertices, and 20 million triangles.
Malformed geometry is reported and the affected product remains semantic-only.

WKT wireframes and bounding boxes are not promoted to solid bodies. They are
less precise fallbacks and remain candidates for a later representation tier.

## Build and run

From the repository root:

```sh
./mvnw -f LBDtoIFC/pom.xml test
./mvnw -f LBDtoIFC/pom.xml package
java -jar LBDtoIFC/target/lbd-to-ifc-0.1.0-SNAPSHOT-all.jar \
  input.ttl output.ifc
```

Use `--force` to replace an output, `--project-name "My project"` to set the IFC
project name, and `--artifact-root geometry/` to resolve external OBJ artifacts.
Input syntax is selected from the filename extension; Turtle, JSON-LD, RDF/XML,
N-Triples, TriG, and other Apache Jena-supported RDF syntaxes are accepted.

## Java API

```java
Path input = Path.of("building.ttl");
Path output = Path.of("building.ifc");

ConversionReport report = new LbdToIfcConverter().convert(
    input,
    output,
    new LbdToIfcConverter.Options("My project")
);
report.warnings().forEach(System.err::println);
```

The path API writes through a temporary file and moves the completed result
into place. The overload accepting a Jena `Model` and `Writer` is suitable for
embedded use; ownership of those objects remains with the caller.

## Mapping notes

Literal LBD properties become an `IfcPropertySet` named `LBD Properties`.
Booleans, integers, and real numbers retain their scalar IFC value type; other
values become `IfcLabel` or `IfcText`. Resource-valued properties,
classifications, per-face materials, parametric geometry, and arbitrary OWL
restrictions are not currently reconstructed.
