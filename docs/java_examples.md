# Java: embed the converter

[Documentation home](README.md) · Background: [using triples](using-triples.md)

## Add the library

Use JDK 21 or newer for source version 2.55.0.
[Install Java 21 if needed](quick-start.md#install-java-21-for-the-jar-route). The converter returns Apache Jena
models, so you can query and serialize RDF directly in your application.

For a reproducible starting point, clone this repository and run `mvn install`
from its root as described in the [build guide](development.md). This installs
the current converter and its companion modules into your local Maven repository.
Then add this dependency to your own Maven project:

```xml
<dependency>
  <groupId>io.github.jyrkioraskari</groupId>
  <artifactId>ifc-to-lbd</artifactId>
  <version>2.55.0</version>
</dependency>
```

Set your project’s `maven.compiler.release` to `21` or newer. This example uses
the version installed from this checkout; it does not assume that this source
version is already published to a public package repository. Let Maven resolve
transitive dependencies rather than combining unrelated release JARs.

## Convert, query, and save

Save this as `ConvertModel.java` in your application’s Java source directory.
Pass the IFC path as the first program argument when running it from your IDE.

```java
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.linkedbuildingdata.ifc2lbd.ConversionProperties;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

public class ConvertModel {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Pass the path to an IFC file");
        }
        var properties = new ConversionProperties();
        properties.setHasGeometry(false);
        // Explicit IFC boundaries are on by default and need no geometry.
        properties.setIfcSpaceBoundaries(true);
        properties.setGeometryInferredInterfaces(false);
        properties.setIfcZones(true); // Optional zone/group membership export.
        // Base URI, use blank nodes for properties, property level.
        try (var converter = new IFCtoLBDConverter(
                "https://example.com/building/", false, 1)) {
            Model model = converter.convert(Path.of(args[0]).toAbsolutePath().toString(), properties);
            if (model == null) {
                throw new IllegalStateException("Conversion failed; inspect converter messages");
            }
            String query = """
                PREFIX bot: <https://w3id.org/bot#>
                SELECT ?building ?storey WHERE {
                    ?building a bot:Building ; bot:hasStorey ?storey .
                }
                """;
            try (var execution = QueryExecutionFactory.create(query, model)) {
                ResultSetFormatter.out(System.out, execution.execSelect());
            }
            try (OutputStream output = Files.newOutputStream(Path.of("output.ttl"))) {
                RDFDataMgr.write(output, model, RDFFormat.TURTLE_PRETTY);
            }
        }
    }
}
```

Query and serialize the model while the converter is open. Closing it releases
models, temporary storage, and geometry resources. Choose a base URI for your
project; changing it may change the generated identifiers.

## Move to the structured API

For profiles, separate graphs, validation, or enrichment, use `ConversionRequest`
and `ConversionResult` instead of adding long lists of boolean arguments:

```java
try (var session = new org.linkedbuildingdata.ifc2lbd.ConversionSession();
     var converter = new IFCtoLBDConverter(session, "https://example.com/building/", false, 1)) {
    var request = new org.linkedbuildingdata.ifc2lbd.ConversionRequest(
        "/absolute/path/model.ifc", new ConversionProperties());
    try (var result = converter.convert(request)) {
        Model combined = result.getModel();
        System.out.println(combined.size());
        System.out.println(converter.getLastTimings().orElseThrow());
        // Read or serialize the graphs here, before result and converter close.
    }
}
```

`getModel()` combines general, product, and property graphs. `getDataset()`
exposes the dataset; other accessors expose the manifest and validation graphs.
Use `ConversionProfiles.MATERIALS_BMO` to opt into IFC material conversion.
Material assignments, materials, layer sets, memberships, and layers are added
to `getProductModel()`. Thickness and other numeric material assertions are in
`getPropertyModel()`. The lazy `getModel()` union includes both without
materializing duplicate statements. Inspect `getValidationModel()` for
unsupported material-structure or unresolved-unit warnings.
See [supply-chain and sustainability](supply-chain-and-sustainability.md) for
profile and resolver examples, including the runnable `Example15` ETIM enrichment and SPARQL query,
and [development](development.md) for sessions
and identity policies.

Sessions stage IFC triples in memory by default. For a model that does not fit
comfortably in the JVM heap, construct the session with
`new ConversionSession(ConversionSession.Storage.TDB2)` instead.

Applications that use the two-phase `convert_read_in_phase` workflow can inspect
the loaded model before exporting it. `getLoadedExpressSchema()` returns the
detected IFC schema, while `getIfcModelStatistics()` returns immutable raw IFC
root-type counts, the IFC element total, and geometry availability. Existing
`getElementTypeCounts()` and `getPropertySetNames()` provide the mapped element
and property-set summaries used by the desktop dashboard.

## Find more examples and API details

The maintained source examples are in
[`IFCtoLBD/src/main/java/examples`](https://github.com/jyrkioraskari/IFCtoLBD/tree/master/IFCtoLBD/src/main/java/examples).
Begin with `Example1` (Turtle output), `Example2` (subjects), and `Example3`
(SPARQL). `Example6` inspects element types and property sets, `Example8` searches
by bounding box, and `Example14` extracts a 2D line graph from exported geometry.
Some examples illustrate older overloads; check them against the current API.

The checked-in [converter Javadoc](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD/docs/index.html)
can be opened locally at `IFCtoLBD/docs/index.html`. Generate fresh API documentation
with the [build guide](development.md) when working on new source changes.
