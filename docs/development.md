# Build, understand, and extend the converter

[Documentation home](README.md) · Related: [Java](java_examples.md), [Python](python_examples.md)

## Compile from the repository root

Install **JDK 21 or newer**. The repository includes a Maven wrapper, so a
separate Maven installation is optional.
[CVE-2026-91403](https://www.oracle.com/security-alerts/alert-cve-2026-91403.html)
affects Java TLS certificate validation in JDK 21.0.13 and earlier. Use a
vendor build that explicitly includes the CVE-2026-91403 fix (for example,
Azul Zulu 21.54.107 / OpenJDK 21.0.13+9 or Oracle JDK 21.0.13.0.2), not an
unpatched base 21.0.13 build. The Maven wrapper enables CRL checking as an
additional defense through `.mvn/jvm.config`; build environments must permit
access to certificate revocation endpoints.
[Follow the Java 21 installation guide](quick-start.md#install-java-21-for-the-jar-route)
if you do not yet have a JDK. Point `JAVA_HOME` at the JDK, and check
that Maven uses it with `mvn -version`. A JRE alone cannot compile the project.

Java 21 is the single baseline for the root reactor and the separate Java,
Scala, legacy OpenAPI, MCP, Python/JPype, and Maven example integrations. Their
POMs use `maven.compiler.release=21`, so a newer JDK still emits Java
21-compatible bytecode. The modern `IFCtoLBD_OpenAPI_Node_js` service also
requires Node.js 20 or newer. Packaged runtime images include their documented
Java runtime.

GraalVM users should select a GraalVM distribution for JDK 21 when building the
`native` profile. Production code must stay on non-preview Java 21 APIs: records,
sealed types, pattern-matching `switch`, and sequenced collections are suitable,
but preview APIs require flags that the JVM and native-image builds deliberately
do not enable. IFC/XML input continues to use the standard JAXP APIs. JavaFX
FXML controllers and resources require the native-image metadata already kept
under each module's `META-INF/native-image` resources; update that metadata when
adding reflectively loaded controllers, fields, methods, or resource types.

```sh
git clone https://github.com/jyrkioraskari/IFCtoLBD.git
cd IFCtoLBD
java -version
./mvnw -version
./mvnw install
```

The root POM builds five modules in dependency order and installs their
artifacts into your local Maven repository. You do not need to build each
module manually. The first build needs access to dependency repositories.
Use `./mvnw verify` if you want to build and check without installing artifacts.

Before tagging a source release, run the same gates used by CI:

```sh
node .github/scripts/check-version-consistency.cjs
node .github/scripts/check-doc-links.cjs
./mvnw install
./mvnw verify -Pintegration
./mvnw -pl IFCtoLBD -am -P release -DskipTests package
npm --prefix IFCtoLBD_MCP ci
npm --prefix IFCtoLBD_MCP run check
npm --prefix IFCtoLBD_MCP run test:integration
npm --prefix IFCtoLBD_MCP run smoke
npm --prefix IFCtoLBD_OpenAPI_Node_js ci
npm --prefix IFCtoLBD_OpenAPI_Node_js run check
npm --prefix IFCtoLBD_OpenAPI_Node_js test
npm --prefix IFCtoLBD_OpenAPI_Node_js run smoke
```

The `release` profile builds the converter, source, and Javadoc artifacts for
inspection; it does not publish them. Publishing is a separate credentialed
operation. The configured Maven deployment target is GitHub Packages. Do not
restore the retired `oss.sonatype.org` staging endpoint; Maven Central now uses
the Central Publisher Portal.

For only the converter and its required modules:

```sh
./mvnw -pl IFCtoLBD -am install
```

`-pl` selects a project and `-am` also builds the modules it depends on.
The default converter test selection excludes the `integration`, `shacl`, and
`slow` groups. Run broader checks when changing conversion behavior:

```sh
./mvnw verify -Pintegration
./mvnw verify -Pfull
```

The integration profile includes converter and SHACL tests while excluding slow
tests. The full profile includes the slow property-based tests as well.
`-DskipTests` is useful for packaging after verification; it skips running tests.

## Run what you built

Package the converter and run the modern command-line entry point explicitly:

```sh
./mvnw -pl IFCtoLBD -am package -DskipTests
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --url https://example.com/building/ --target_file output.ttl \
  IFCtoLBD/src/main/resources/Duplex_A.ifc
```

This bundled JAR contains the converter and its dependencies; the shade plugin
writes it into the MCP distribution directory. Its manifest currently points to
the legacy converter entry point, so use the explicit class above for the modern
CLI. Replace the conversion arguments with `--help` to inspect current options.
This is also a suitable bundled classpath for the [Python wrapper](python_examples.md).

For product elements in a source-built CLI export, enable them explicitly:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --hasBuildingElements=true --hasIfc_based_elements=false \
  --target_file elements.ttl IFCtoLBD/src/main/resources/Duplex_A.ifc
```

`--hasBuildingElements=true` exports recognized building elements and their
product types. `--hasIfc_based_elements` separately controls elements whose IFC
class has no mapped LBD product type; setting it to `false` does not remove
recognized building elements. Both options default to `false` in the Java CLI.
For a filtered export, repeat `--selectedType` with the desired mapped type names.

Package and start the desktop:

```sh
./mvnw -pl IFCtoLBD_Desktop_2026 -am package -DskipTests
java -jar IFCtoLBD_Desktop_2026/target/IFCtoLBD-Desktop_Java_21.jar
```

Keep `IFCtoLBD-Desktop_Java_21_lib` beside the desktop JAR. The distribution copies
runtime dependencies into that folder. The desktop entry point is
`org.linkedbuildingdata.ifc2lbd.desktop.Main`.

To build a platform-specific application image with its own Java runtime, use a
JDK that provides `jpackage` and run:

```sh
./mvnw clean verify -pl IFCtoLBD_Desktop_2026 -am -Pruntime-image -DskipTests
```

The result is under `IFCtoLBD_Desktop_2026/target/runtime-image`. Build it once
on each target operating system and architecture; `jpackage` does not
cross-package. The output also includes the three getting-started examples for
release archives. Code signing and native installer formats remain release
pipeline responsibilities; this profile deliberately creates a portable
application image.

## Artifact names

Use one name for each piece everywhere: build scripts, release assets, checksums,
and documentation. `<version>` is the Maven project version; native packages also
include `<os>-<arch>`.

| Piece | Canonical release name |
| --- | --- |
| Java library | `ifc-to-lbd-<version>.jar` |
| Java desktop distribution | `ifctolbd-desktop-<version>-java21.zip` |
| Bundled desktop application | `ifctolbd-desktop-<version>-<os>-<arch>.zip` |
| Native CLI | `ifctolbd-cli-<version>-<os>-<arch>` (`.exe` on Windows) |
| MCP server | `ifctolbd-mcp-<version>.tgz` |

The unversioned `IFCtoLBD-Desktop_Java_21.jar` and
`IFCtoLBD-Desktop_Java_21_lib` names remain the internal launcher layout. The
MCP runtime likewise expects its internal converter at
`IFCtoLBD_MCP/lib/ifctolbd-converter.jar`. Do not publish those internal names
as several different release products.

Release 2.49.0 predates this policy and keeps its published names
(`IFCtoLBD.zip`, `IFCtoLBD-Desktop_for_Windows.zip`, and
`IFCtoLBDConverter_CLI.exe`) so existing links remain valid. Apply the canonical
scheme to new release assets rather than renaming historical downloads.

Generate fresh converter Javadoc after installing its companion modules:

```sh
./mvnw -f IFCtoLBD/pom.xml javadoc:javadoc
```

Open the generated `index.html` under `IFCtoLBD/target/site/apidocs` or
`IFCtoLBD/target/reports/apidocs`, depending on the Maven Javadoc plugin version.

## Understand the subprojects

Paths below are relative to the repository root. Browse the
[source tree](https://github.com/jyrkioraskari/IFCtoLBD).

| Subproject | Responsibility | In the root build? |
| --- | --- | --- |
| `IFCtoRDF` | Parses IFC and produces the IFC-oriented RDF representation used by the converter | Yes |
| `IFCtoLBD_Geometry` | Geometry extraction and IfcOpenShell integration | Yes |
| `IFCtoLBD` | LBD mapping, properties, structured API, validation, and CLI | Yes |
| `IFCProps2ExcelOnline` | Web application exporting IFC properties to Excel | Yes |
| `IFCtoLBD_Desktop_2026` | JavaFX desktop interface, queries, validation, and preview | Yes |
| `IFCtoLBD_Python` | JPype wrapper and Python examples | No; scripts |
| `ProductExtractor` | Python tool and local web UI summarising building elements (materials, layers, property sets, IFC attributes) from LBD Turtle as JSON | No; separate pip setup |
| `LBDBabylon` | Browser-local Babylon.js viewer with RDF-linked geometry, semantic collision roles, and grounded walkthrough controls | No; separate npm setup |
| `IFCtoLBD_NodeJS` | Node.js integration examples | No; separate npm setup |
| `IFCtoLBD_OpenAPI_Node_js` | Modern Fastify/OpenAPI HTTP conversion service | No; separate npm setup using the shaded converter JAR |
| `IFCtoLBD_OpenAPI` | Legacy Java/Tomcat Web API application | No; separate Maven project |
| `IFCtoLBD_MCP` | MCP server and bundled converter distribution | No; receives a JAR from converter packaging |
| `OneClick` | Separate web, Node CLI, and MCP selected-scope Turtle workflow using Python IfcOpenShell | No; separate npm and Python setup |
| `ifc-to-lbd-scala` | Scala integration example | No; separate Maven project |
| `maven_demo` | Historical Maven consumer example | No; update its versions and Java settings before reuse |

Run the standalone LBDBabylon viewer and its checks from the repository root:

```sh
npm --prefix LBDBabylon ci
npm --prefix LBDBabylon run check
npm --prefix LBDBabylon test
npm --prefix LBDBabylon run build
npm --prefix LBDBabylon run dev
```

Movement classification is allowlisted from RDF types. Physical products can
block the walker; slabs, floors, ramps, stairs, landings, and coverings can be
walkable. Spaces, openings, voids, spatial containers, annotations, and unknown
helper geometry remain non-blocking. See the
[LBDBabylon walkthrough](lbd-babylon.md) before changing navigation, geometry
units, or collision roles.

See the [modern OpenAPI service guide](../IFCtoLBD_OpenAPI_Node_js/README.md),
[Node.js examples README](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_NodeJS/README.md),
[MCP README](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_MCP/README.md),
and [Excel deployment guide](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCProps2ExcelOnline/DEPLOYMENT.md)
for those applications. The root reactor does not build every directory in the
repository. Install the core modules before working on a separate Java consumer,
and check that consumer’s POM for its own dependency and runtime requirements.

OneClick's CLI is `node OneClick/cli.mjs --ifc model.ifc --selection floorplan`.
The MCP tool `export_oneclick_lbd` invokes that CLI, while
`convert_ifc_to_lbd` continues to use the shaded Java converter. OneClick's
Python extractor requires IfcOpenShell and Shapely; see the
[OneClick guide](../OneClick/README.md#command-line-export).

### Run the modern HTTP/OpenAPI service

The modern service is an npm project outside the root Maven reactor. It uses
the shaded converter distribution written to
`IFCtoLBD_MCP/lib/ifctolbd-converter.jar` by the `IFCtoLBD` package phase.

```sh
npm --prefix IFCtoLBD_OpenAPI_Node_js ci
npm --prefix IFCtoLBD_OpenAPI_Node_js run build:converter
npm --prefix IFCtoLBD_OpenAPI_Node_js test
npm --prefix IFCtoLBD_OpenAPI_Node_js start
```

Open <http://127.0.0.1:8080/docs/> for the interactive OpenAPI documentation.
Use `npm --prefix IFCtoLBD_OpenAPI_Node_js run smoke` for a real Turtle and
TriG conversion after the converter JAR has been built. The service guide
documents multipart fields, response formats, environment variables, health
probes, reverse-proxy requirements, and the container build.

## Follow a conversion through the code

Start in `IFCtoLBD/src/main/java/org/linkedbuildingdata/ifc2lbd`:

1. `IFCtoLBDConverter_CLI` or the desktop controller collects user options.
2. `IFCtoLBDConverter` coordinates reading and conversion. The desktop’s
   `convert_read_in_phase` prepares the model before the final conversion.
3. `core/IFCtoRDF` and the `IFCtoRDF` module provide the IFC graph.
4. `core/IFCtoLBDConverterCore` maps topology, elements, attributes, and properties.
   `core/utils/IfcOWLUtils` follows IFC relationships; `core/valuesets` represents
   attributes, property sets, and quantities.
5. `ConversionRequest` selects properties, profiles, filters, and mappings.
   Profile modules configure, map, enrich, and validate via `ConversionContext`.
6. `ConversionResult` exposes general, product, property, manifest, and validation
   graphs. Serialization produces Turtle, JSON-LD, or dataset output as configured.

`ConversionSession` owns conversion resources and temporary storage. Injected
`GeometryProvider` and `UriPolicy` implementations let structured callers control
geometry and resource identity. Close results, converters, and any sessions you
own after use; copy or serialize data you need to retain first.

Conversion sessions use a transactional in-memory staging graph by default. This
avoids TDB2 index and memory-mapping overhead for ordinary models. Applications
that convert models larger than their available JVM heap can select the
disk-backed store explicitly with `new ConversionSession(ConversionSession.Storage.TDB2)`.
Do not share either kind of session between concurrent conversions.

## Make your first extension

Choose one concrete IFC example and state the triple or behavior you want to
add. Run its conversion first and inspect the existing graph. Then choose the
smallest relevant extension point:

| Desired change | Start here |
| --- | --- |
| Different property identifiers or export selections | `ConversionRequest` property replacements, selected types, and selected property sets |
| Additional domain output | `ConversionModule`, `ConversionContext`, and `ConversionProfile` |
| Classification or product enrichment | `ClassificationResolver`, `SupplyChainStage`, `SustainabilityStage` |
| New geometry backend | `GeometryProvider` injected through `ConversionSession` |
| Consistent element identity across revisions | `StableProjectUriPolicy` and a persistent project namespace; `StableGuidUriPolicy` remains for model-scope compatibility |
| RDF quality rules | `ValidationShapePack`, `ValidationStage`, and SHACL resources |
| IFC relationship or property mapping fix | `IFCtoLBDConverterCore`, `IfcOWLUtils`, and `core/valuesets` |
| Desktop workflow change | `IFCtoLBDController` and `src/main/resources/.../IFCtoLBD.fxml` in the desktop module |

For new stable-identity integrations, call
`ConversionRequest.withStableProjectIdentity(...)` with a persistent absolute,
non-`file:` project namespace. Keep the namespace fixed for revisions of one
project and distinct for unrelated projects. The older
`ConversionRequest.withModelScope(...)` mechanism remains available for
compatibility. A file path alone is not a persistent model identity.

Use the existing modules and tests as examples. Tests in `IFCtoLBD/src/test/java`
include `ConversionApiIntegrationTest`, `StableGuidUriPolicyTest`,
`SupplyChainStageTest`, and `ValidationStageTest`. For a mapping change, add a
small IFC fixture and assert the expected RDF relationships or SHACL result.
Run the default checks and the integration profile for affected conversion
behavior. Document new vocabulary terms and add an example showing how users
query them. Keep external registry access explicit; offline fixtures make
conversion tests reproducible.

### Convert IFC materials to BMO

For CLI usage, a complete RDF example, and an end-user query, see
[IFC materials with BMO](materials-bmo.md).

The optional `materials-bmo` profile maps direct `IfcMaterial` assignments and
`IfcMaterialLayerSet`/`IfcMaterialLayerSetUsage` assignments to the Digital
Construction [BuildingMaterials 0.1 source ontology](https://github.com/digitalconstruction/BuildingMaterials/blob/b30a0a29e04c4f5d4a63e26a50987afce7d8492f/v/0.1/bm.ttl).
The vocabulary namespace is
`https://w3id.org/digitalconstruction/BuildingMaterials#`. The source is pinned
to revision `b30a0a29e04c4f5d4a63e26a50987afce7d8492f`, and the SHA-256 of
`v/0.1/bm.ttl` is
`b8ed0e82828ccc2700cc1c30252e0c1383bb2149821e63b43ed85ad925da7ca9`.
An unchanged verification copy is bundled at
`IFCtoLBD/src/main/resources/ontology/bmo-0.1.ttl`, so vocabulary checks and
conversion remain offline. The converter does not load the ontology during
conversion, copy its axioms into output, or enable inference.

The profile automatically runs the offline
`shacl/materials-bmo-v1.0.0.ttl` application-profile checks. They validate
assignment and material target types, one-based layer memberships and unique
positions within a layer set, material links, metre-normalized thickness
quantities, and the code/message content of material conversion diagnostics.

Verified BMO terms express `LayerSet` membership through `bmo:hasLayers`, layer
material through `bmo:hasMaterial`, and thickness through `bmo:hasThickness`.
The BMO source defines no element-to-assignment or ordered-membership terms, so
the profile uses this minimal extension in the project-controlled
`https://w3id.org/ifctolbd/mapping#` namespace:

| Term | Meaning |
| --- | --- |
| `materialAssignment` | Existing LBD element to selected assignment |
| `assignedMaterialDefinition` | Assignment or usage to BMO material definition |
| `assignedMaterial` | Assignment to directly selected BMO material |
| `sourceIfcAssociation` | Assignment to source `IfcRelAssociatesMaterial` |
| `sourceIfcTypeResource` | Inherited assignment to its source IFC type object |
| `layerSetUsage` | Assignment to occurrence-specific layer-set usage |
| `layerMembership` | BMO layer set to ordered membership |
| `layer`, `position` | Membership to BMO layer and its 1-based list position |
| `thicknessQuantity` | Layer to normalized QUDT thickness value and source-value provenance |
| `layerSetDirection`, `directionSense`, `offsetFromReferenceLine` | Preserved IFC usage values |

`ConversionProfiles.MATERIALS_BMO` composes the existing `bot-topology` and
`product-ontology` modules with the dedicated `MaterialsBmoModule`. It is
opt-in and does not enable geometry. The module reads the retained IFC graph
through `ConversionContext.ifcModel()` and reuses the element identities in
`mappedResources()`. Material structure is stored in the existing product
graph; scalar thickness, its QUDT value, and numeric usage assertions are stored
in the property graph. `ConversionResult.getModel()` supplies the combined lazy
view, while the physical graphs contain no duplicate material statements.

The membership resource is scoped beneath the layer-set identifier. Positions
start at **1**, matching IFC list order, and do not attach position state to a
reusable layer. The converter also emits `bmo:hasLayers` directly from the set
to every layer. It does not infer interior/exterior orientation from list order.
A direct `IfcMaterial` assignment gets a BMO `MaterialDefinition` node with
`bmo:hasMaterial`, preserving that property's declared domain and range. The
assignment also links to the directly selected material through
`ifctolbd:assignedMaterial`. The converter does not use the invented
`bmo:hasLayerSet` term and never places `bmo:hasMaterial` directly on an LBD
element.

Material traversal is extracted before RDF emission and uses the active ifcOWL
namespace. Both the IFC4 `relatedObjects_IfcRelDefinesByType` spelling and the
IFC2x3 `relatedObjects_IfcRelDefines` spelling are supported. An occurrence
assignment takes precedence over an assignment inherited through its IFC type;
the inherited assignment is considered only when the occurrence has no material
association. This rule also applies when the occurrence selects an unsupported
structure, so unsupported data is reported rather than silently replaced.

Each selected IFC association has a deterministic `ifctolbd:MaterialAssignment`
provenance resource. `ifctolbd:sourceIfcAssociation` and, for inherited
assignments, `ifctolbd:sourceIfcTypeResource` retain the IFC sources, while
`ifctolbd:materialAssignmentInherited` records whether the type supplied the
assignment. A layer-set usage is kept separate from its shared layer-set
definition, and preserves `LayerSetDirection`, `DirectionSense`, and
`OffsetFromReferenceLine` when supplied. IFC material names are
emitted as `rdfs:label`, descriptions as `dcterms:description`, and categories
as literal `ifctolbd:materialCategory` values; categories are not treated as
classifications.

Materials, layers, layer sets, usages, and assignments are cached by their IFC
source resource, never by display name. Their identifiers are generated from
the active model/base identity policy and IFC source identity; membership IRIs
are derived from the layer-set IRI and position. Non-root material structures
normally rely on STEP entity identity, which is deterministic within a model
conversion but is not claimed to remain stable across different IFC revisions.

The source ontology's `bmo:hasUnit` has domain `opm:Property`, not `bmo:Layer`.
Consequently, the profile never attaches `bmo:hasUnit` to a layer. In this
application profile the scalar `bmo:hasThickness` is always a decimal measured
in **metres**. The converter resolves the IFC project's length unit, including
SI prefixes and linear `IfcConversionBasedUnit` conversion factors, before
emitting that scalar. The layer also links through `ifctolbd:thicknessQuantity`
to a `qudt:QuantityValue` whose `qudt:unit` is `unit:M`. The value node retains
the IFC numeric lexical value as `unitmeta:originalValue`, its source unit as
`unitmeta:sourceUnit` when that QUDT unit is known, the original IFC unit code,
and mapping provenance.

If the project length unit or its conversion factor cannot be resolved, the
converter retains the source numeric value on the linked provenance/quantity
node and emits an `UNRESOLVED_LAYER_THICKNESS_UNIT` warning in the diagnostics
graph. It does not emit `bmo:hasThickness` or claim `unit:M` for that value.
Material-name classification is also deferred: `bmo:hasMaterialName` points to
a `MaterialType`, and values such as `bmo:Concrete` and `bmo:Steel` are named
individuals rather than classes.

Other IFC material-select structures, including material lists, constituents,
and profiles, are intentionally not inferred in this first increment. Each is
reported as a structured warning in the result's validation graph. Material
classification, bSDD enrichment, EPD linking, constituents, and material
profiles remain outside this profile.

The pinned 0.1 ontology contains defects that consumers must not repair through
unrestricted reasoning. `bmo:hasConstituents` is declared twice with different
ranges (`bmo:Material` and `bmo:Constituent`), which gives both ranges effect in
OWL. Several named individuals also have questionable material-type assertions:
cement stone, ceramic, and glass are typed as `OrganicaMaterial`, while polymer
and wood are typed as `InorganicNonMetallicMaterial`. IFCtoLBD neither imports
these axioms nor infers classifications from material names.

```sparql
PREFIX bmo: <https://w3id.org/digitalconstruction/BuildingMaterials#>
PREFIX ifctolbd: <https://w3id.org/ifctolbd/mapping#>
PREFIX qudt: <http://qudt.org/schema/qudt/>
PREFIX unit: <http://qudt.org/vocab/unit/>

SELECT ?element ?position ?layer ?material ?thickness ?quantity
WHERE {
  ?element ifctolbd:materialAssignment ?assignment .
  ?assignment ifctolbd:assignedMaterialDefinition ?set .
  ?set ifctolbd:layerMembership ?membership ;
       bmo:hasLayers ?layer .
  ?membership ifctolbd:position ?position ;
              ifctolbd:layer ?layer .
  ?layer bmo:hasMaterial ?material ;
         bmo:hasThickness ?thickness ;
         ifctolbd:thicknessQuantity ?quantity .
  ?quantity qudt:numericValue ?thickness ; qudt:unit unit:M .
}
ORDER BY ?element ?position
```

## Optional geometry setup

### Coordinate spaces and georeferencing

Geometry providers must declare their output coordinate space, length unit,
unit-to-metre factor, and whether object placements or map conversion have
already been applied. The IfcOpenShell 0.8.5 iterator and legacy fallback
produce world-placement geometry in metres in the IFC local engineering frame;
they do not apply `IfcCoordinateOperation`. OBJ payloads therefore remain in
that local frame and are labelled with the model's `ifc-local` CRS. They are not
labelled as projected geometry.

For IFC4 and later, the converter follows each operation's inherited
`SourceCRS_IfcCoordinateOperation` and `TargetCRS_IfcCoordinateOperation`
links. `SourceCRS` associates the operation with its engineering representation
context; `TargetCRS` supplies projected CRS metadata. A projected WKT literal
is emitted only when one context/operation association is unambiguous, the
operation is a complete and valid `IfcMapConversion` or
`IfcMapConversionScaled`, provider and project units are known, and the target
`Name` contains an explicit absolute CRS identifier or EPSG identifier. Unknown
target names and all source operation values are retained as geometry metadata.
Digits in a datum, map zone, or free-text name are never treated as an EPSG code.

Map conversion consumes coordinates in the operation source unit. Provider
metres are converted back to that source unit exactly once before IFC `Scale`
and scaled-conversion factors are applied. Eastings, northings, and orthogonal
height are translations in the target `MapUnit` and are never multiplied by a
project-unit conversion. Direction ratios are normalized before rotation; no
additional `TrueNorth` rotation is applied. Bounding boxes transform all eight
corners before a new target-space axis-aligned envelope is calculated. WKT is
parsed structurally rather than rewritten as text.

Unsafe or incomplete transformations retain local geometry and carry warnings.
This includes multiple coordinate-operation contexts, missing required values,
zero directions, non-finite values, invalid scales, unresolved source units,
`IfcRigidOperation`, and legacy IFC2X3 `ePSet_MapConversion`/
`ePSet_ProjectedCRS` data whose representation-context association cannot be
established. Site reference latitude/longitude remains site metadata and is not
used as a substitute map conversion. The converter performs no datum shift or
general-purpose reprojection. These operations preserve IFC-declared values;
they are not a claim of survey-grade accuracy.

### Java release test gate

`ReleaseTests` is the comprehensive offline release gate for the Java
converter. It discovers every test in `org.linkedbuildingdata.ifc2lbd`, so new
unit, integration, SHACL, and property tests are included automatically. The
gate uses the JUnit 6 Platform Suite Engine, so Maven and Eclipse expose its
selected classes and individual checks in their normal test trees.
The property-based converter check uses 100 deterministic release cases; the
`full` profile retains its standalone 1,000-case stress run.
The suite orders the legacy `ConverterRunsUnitTests` class first because its
ontology-consistency checks depend on process-wide Jena compatibility state;
the class and all of its methods remain visible in the JUnit result tree.

```sh
./mvnw -pl IFCtoLBD -am \
  -Dtest=ReleaseTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

In Eclipse, first use **Maven > Update Project** after pulling dependency
changes, then use **Run As > JUnit Test** on `ReleaseTests.java`. Eclipse does
not need a Maven profile or VM argument, and the JUnit view lists the selected
test classes and methods beneath the suite.

#### Eclipse JUnit 6 classpath mismatch

The Eclipse project must use the **JUnit 6** library container. An older
workspace or imported project can retain the JUnit 5 container and place its
JUnit Platform launcher ahead of the Maven-managed JUnit 6 launcher. The
typical symptom is:

```text
java.lang.NoSuchMethodError:
  void org.junit.platform.launcher.Launcher.execute(
    org.junit.platform.launcher.LauncherExecutionRequest)
```

To repair the Eclipse workspace:

1. Confirm that **Project Properties > Java Build Path > Libraries** contains
   **JUnit 6**, not **JUnit 5**.
2. Run **Maven > Update Project** with **Force Update of Snapshots/Releases**
   selected.
3. Run **Project > Clean**.
4. Delete any saved `ReleaseTests` entry under
   **Run > Run Configurations > JUnit**.
5. Open `ReleaseTests.java` and create a new **Run As > JUnit Test** launch.

The repository's `.classpath` entry must be
`org.eclipse.jdt.junit.JUNIT_CONTAINER/6`. Do not add a second JUnit container
manually. If the error remains, inspect the launch configuration's classpath
for an older `junit-platform-launcher` and refresh or re-import the Maven
project.

For Maven, selecting `ReleaseTests` automatically activates the matching
profile so the ordinary fast-build tag exclusions do not hide integration or
SHACL children. Ordinary Maven test runs exclude the suite itself to avoid
executing the same package tests twice; this exclusion does not apply to the
command above or to Eclipse's JUnit launcher.

The offline gate deliberately excludes the live bSDD compatibility test, the
performance benchmark, and the structured-geometry checks that require the
separately distributed ISO specification archive. Run those checks explicitly
when their external service, input model, or archive is available; they are not
silently treated as release-gate successes.

The geometry namespace (`https://w3id.org/ifctolbd/geometry#`) distinguishes
`local-engineering`, `projected`, and viewer-only `render-rebased` spaces and
records source/target units, operation parameters, provider placement behavior,
and warnings. Consumers should use the per-representation `wktCoordinateSpace`
and `objCoordinateSpace` assertions when a geometry resource contains both WKT
and OBJ.

When geometry export is enabled, the geometry module first tries the Python
IfcOpenShell iterator and falls back to the bundled legacy IfcGeomServer if it
cannot start. The existing geometry setup uses IfcOpenShell 0.8.5:

```sh
python -m pip install ifcopenshell==0.8.5
```

On Windows, you can use `py -3 -m pip install ifcopenshell==0.8.5`. To select a
particular Python environment, put the system property **before** `-jar` or `-cp`:

```sh
java -Difctolbd.ifcopenshell.python=/absolute/path/to/python \
  -jar IFCtoLBD_Desktop_2026/target/IFCtoLBD-Desktop_Java_21.jar
```

`-Difctolbd.ifcopenshell.iterator=false` forces the legacy backend. Spatial
objects may legitimately have no mesh geometry. Geometry export is optional
for learning RDF or querying topology.

Structured callers can obtain backend-neutral triangle data through
`GeometryResult.getTessellatedMesh(guid)`. The returned `TessellatedMesh`
contains immutable positions, optional normals, triangle indices, materials,
per-triangle material indices, and a local-to-world transform. The Python
iterator currently emits world-space positions and therefore uses the identity
transform; the legacy backend preserves its separate placement transform.

`GeometryArtifactFormat` selects optional `OBJ`, `GLB`, or self-contained `GLTF` artifacts.
`GltfGeometryWriter` converts `TessellatedMesh` data directly to glTF 2.0, so format selection does not
change the geometry provider or require another native executable. `FileSystemGeometryArtifactStore`
writes content-addressed files and publishes only its configured base IRI in RDF. Embedded OBJ remains the
default, and the default request fingerprint is unchanged. GLB/glTF currently preserve positions, normals,
indices, local-to-world transforms, and basic colour/alpha; they do not emit textures, animation, external
buffers, or compression extensions.

The desktop creates its artifact store during the read-in phase. Consequently, changing its geometry
format, directory, or base IRI marks the loaded model stale and triggers a safe reread before conversion.
Old desktop presets omit these fields and resolve to embedded OBJ.

Every structured conversion also runs `SemanticOutputAudit`. Its RDF findings
are included in the validation named graph, while status and warning/error
counts are recorded in the conversion manifest. The audit detects generated
predicates without declarations, conflicting or misused object/datatype
properties, malformed and non-finite numeric literals, multiple simple values,
and multiple current OPM states. Findings are diagnostic and do not prevent
legacy conversions from completing.

## Optional native CLI

The converter POM also has a `native` profile for GraalVM. After installing the
core modules, use GraalVM for JDK 21 and the native build tools:

```sh
mvn -f IFCtoLBD/pom.xml -Pnative -DskipTests package
```

Native compilation needs additional platform tools, including a C++ toolchain
on Windows. Start with the ordinary JVM build and verify your changes there
before exploring native packaging.
