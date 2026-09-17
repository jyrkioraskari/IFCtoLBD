# Build, understand, and extend the converter

[Documentation home](README.md) · Related: [Java](java_examples.md), [Python](python_examples.md)

## Compile from the repository root

Install **JDK 21 or newer** and Maven.
[Follow the Java 21 installation guide](quick-start.md#install-java-21-for-the-jar-route)
if you do not yet have a JDK. Point `JAVA_HOME` at the JDK, and check
that Maven uses it with `mvn -version`. A JRE alone cannot compile the project.

```sh
git clone https://github.com/jyrkioraskari/IFCtoLBD.git
cd IFCtoLBD
java -version
mvn -version
mvn install
```

The root POM builds five modules in dependency order and installs their
artifacts into your local Maven repository. You do not need to build each
module manually. The first build needs access to dependency repositories.
Use `mvn verify` if you want to build and check without installing artifacts.

For only the converter and its required modules:

```sh
mvn -pl IFCtoLBD -am install
```

`-pl` selects a project and `-am` also builds the modules it depends on.
The default converter test selection excludes the `integration`, `shacl`, and
`slow` groups. Run broader checks when changing conversion behavior:

```sh
mvn verify -Pintegration
mvn verify -Pfull
```

The integration profile includes converter and SHACL tests while excluding slow
tests. The full profile includes the slow property-based tests as well.
`-DskipTests` is useful for packaging after verification; it skips running tests.

## Run what you built

Package the converter and run the modern command-line entry point explicitly:

```sh
mvn -pl IFCtoLBD -am package -DskipTests
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

Package and start the desktop:

```sh
mvn -pl IFCtoLBD_Desktop_2026 -am package -DskipTests
java -jar IFCtoLBD_Desktop_2026/target/IFCtoLBD-Desktop_Java_21.jar
```

Keep `IFCtoLBD-Desktop_Java_21_lib` beside the desktop JAR. The distribution copies
runtime dependencies into that folder. The desktop entry point is
`org.linkedbuildingdata.ifc2lbd.desktop.Main`.

Generate fresh converter Javadoc after installing its companion modules:

```sh
mvn -f IFCtoLBD/pom.xml javadoc:javadoc
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
| `IFCtoLBD_NodeJS` | Node.js integration examples | No; separate npm setup |
| `IFCtoLBD_OpenAPI` | Web API application | No; separate Maven project |
| `IFCtoLBD_MCP` | MCP server and bundled converter distribution | No; receives a JAR from converter packaging |
| `ifc-to-lbd-scala` | Scala integration example | No; separate Maven project |
| `maven_demo` | Historical Maven consumer example | No; update its versions and Java settings before reuse |

See the [Node.js README](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_NodeJS/README.md),
[MCP README](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_MCP/README.md),
and [Excel deployment guide](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCProps2ExcelOnline/DEPLOYMENT.md)
for those applications. The root reactor does not build every directory in the
repository. Install the core modules before working on a separate Java consumer,
and check that consumer’s POM for its own dependency and runtime requirements.

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
| Consistent element identity across revisions | `StableGuidUriPolicy` and a persistent model scope |
| RDF quality rules | `ValidationShapePack`, `ValidationStage`, and SHACL resources |
| IFC relationship or property mapping fix | `IFCtoLBDConverterCore`, `IfcOWLUtils`, and `core/valuesets` |
| Desktop workflow change | `IFCtoLBDController` and `src/main/resources/.../IFCtoLBD.fxml` in the desktop module |

When stable identity is enabled, supply a model scope with
`ConversionRequest.withModelScope(...)`. Keep the scope fixed for revisions of
one model and distinct for unrelated models. A file path alone is not a
persistent model identity.

Use the existing modules and tests as examples. Tests in `IFCtoLBD/src/test/java`
include `ConversionApiIntegrationTest`, `StableGuidUriPolicyTest`,
`SupplyChainStageTest`, and `ValidationStageTest`. For a mapping change, add a
small IFC fixture and assert the expected RDF relationships or SHACL result.
Run the default checks and the integration profile for affected conversion
behavior. Document new vocabulary terms and add an example showing how users
query them. Keep external registry access explicit; offline fixtures make
conversion tests reproducible.

## Optional geometry setup

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

## Optional native CLI

The converter POM also has a `native` profile for GraalVM. After installing the
core modules, use a compatible GraalVM JDK and native build tools:

```sh
mvn -f IFCtoLBD/pom.xml -Pnative -DskipTests package
```

Native compilation needs additional platform tools, including a C++ toolchain
on Windows. Start with the ordinary JVM build and verify your changes there
before exploring native packaging.
