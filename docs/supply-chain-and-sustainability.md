# Supply-chain and sustainability output

[Documentation home](README.md) · [Java guide](java_examples.md) · [Development](development.md)

The structured conversion API provides `ConversionProfiles.SUPPLY_CHAIN` and
`ConversionProfiles.SUSTAINABILITY`. Both preserve IFC property values while adding normalized
resources; the sustainability profile also enables unit output.

`IfcRelAssociatesClassification` is emitted as a classification assertion containing the original
system, edition, code, label and source location. IFCtoLBD never derives an authoritative identifier
from a label. To resolve identifiers, attach a `ClassificationResolver` to the request. The supplied
`BsddClassificationResolver` performs exact-code bSDD lookups for explicitly configured dictionary
URIs. `EclassClassificationResolver` and `EtimClassificationResolver` provide system-filtering
adapters for bSDD-hosted ECLASS and ETIM dictionaries. For resolved classes the bSDD resolver also
retrieves class properties, reusable properties, allowed values, units, class relationships and
related IFC entity names. Wrap an adapter in `VersionedClassificationResolverCache` to cache that
complete versioned definition as well as the code resolution. Conversion requests reject resolvers
that can access the network: registry refresh and conversion are deliberately separate operations.
Resolver implementations are considered network-capable by default and must explicitly implement
the offline contract before a conversion request accepts them.

The bSDD adapter follows the published Dictionaries API routes: exact class metadata comes from
`/api/Class/v1?Uri=...`, reusable property metadata is retained from each returned ClassProperty
(the standalone registry route is `/api/Property/v5?uri=...`), and IFC candidates come from
`/api/Dictionary/v1/Classes?...&RelatedIfcEntity=...`. It does not dereference
`identifier.buildingsmart.org` identifiers as JSON endpoints.

### Optional online cache loading

The CLI offers an explicit loading step. Network access occurs before conversion and only when one
of the `--load-etim*` options is present. The converter then reopens the resulting cache without a
registry client:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --profile supply-chain \
  --etim-dictionary https://identifier.buildingsmart.org/uri/etim/etim/9.0 \
  --classification-cache .ifctolbd/classification-cache \
  --load-etim-metadata \
  --load-etim EC000005 \
  --target_file enriched.trig --hasTriG \
  model.ifc
```

`--load-etim` and `--load-etim-candidates IfcDoor` may be repeated. Omit every `--load-etim*`
option to perform a cache-only conversion. `--classification-cache-version` can isolate separately
managed cache snapshots; otherwise it is derived from the ETIM dictionary edition. Use `--hasTriG`
when the conversion manifest must be serialized as its own named graph.

```java
var registry = new EtimClassificationResolver(
    "https://identifier.buildingsmart.org/uri/.../etim/10");
var cachePath = Path.of(".ifctolbd/classification-cache");
var refresh = new VersionedClassificationResolverCache(cachePath, "bsdd-v1", registry);
refresh.refresh(new ClassificationResolver.Request(
    "ETIM", "10", "EC000005", "Cable end sleeve",
    "https://identifier.buildingsmart.org/uri/.../class/EC000005"));

// Conversion opens the cache without a registry client and cannot use the Internet.
var resolver = new VersionedClassificationResolverCache(cachePath, "bsdd-v1");
var request = new ConversionRequest(ifcPath, ConversionProfiles.SUPPLY_CHAIN)
    .withClassificationResolver(resolver);
```

`resolve`, `definition`, and `candidates` always read the cache and return an empty result on a miss;
they never call the delegate. `refresh` and `refreshCandidates` are the only operations which may
call a registry, together with the explicit `refreshDictionary` metadata loader, and must be invoked
before constructing the conversion request. The legacy
`CACHE_THEN_DELEGATE` mode is rejected. Passing `BsddClassificationResolver`,
`EtimClassificationResolver`, or another network-capable resolver directly to `ConversionRequest`
is also rejected.

A source HTTP location is retained as `supply:suppliedIdentifier` with
`identifierVerification "supplied-unverified"`; it is not treated as a bSDD-verified identifier.
Exact registry results use `supply:resolvedConcept` and `identifierVerification
"registry-verified"`. `identifierResolutionConfidence` describes only identifier resolution, while
`classificationCorrectness` records the separate supplied, unconfirmed, or reviewed status. If the
source edition conflicts with the configured dictionary URI version, the registry match is retained
as `supply:registryMatchedIdentifier`, an `editionConflict` is reported, and no resolved concept or
ETIM property enrichment is asserted.

Registry identifiers are used verbatim. A reusable bSDD `Property` and its class-specific
`ClassProperty` remain separate RDF resources. `ClassProperty.propertySet` is emitted as a grouping
label; it is not turned into a URI or an independently identified concept. Allowed values without a
registry URI remain blank nodes rather than receiving label-derived identifiers.

For every cached dictionary used by enrichment, RDF contains a
`supply:ClassificationDictionary`. Registry-returned ownership, version and status are retained.
The owner is also emitted as `dcterms:rightsHolder`, the rights statement as `dcterms:rights`, and
an HTTP license-information identifier as `dcterms:license`. The conversion manifest links the same resource with
`manifest:usesClassificationDictionary`. IFCtoLBD does not reinterpret a rights-reserved statement
as permission to redistribute the full dictionary.

For elements carrying an explicit ETIM classification, the supply-chain profile can also add
property-level ETIM annotations. Automatic matches require an identifier attached to the IFC
property through an IFC external-reference or classification relationship. An HTTP(S) URI occupying
the complete `IfcProperty.Description` value is also accepted for authoring tools that use that
convention; surrounding prose is not. Property labels are not used as a similarity heuristic. Every
candidate is checked against the class property's datatype, units and allowed values before an
annotation is emitted.

Mappings for sources that do not carry property identifiers must be reviewed and context-specific.
Configure them with the ETIM class URI, optional IFC entity, exact IFC property-set and property
names, a bSDD `ClassProperty` or reusable `Property` URI, and a review reference:

```java
var mapping = new EtimPropertyMapping(
    "https://identifier.buildingsmart.org/uri/.../class/EC0001",
    "IfcPump",
    "Pset_PumpTypeCommon",
    "NominalDiameter",
    "https://identifier.buildingsmart.org/uri/.../classproperty/EC0001-EF0001",
    "https://identifier.buildingsmart.org/uri/.../property/EF0001",
    "review:engineering-team/2026-10-15");

var request = new ConversionRequest(ifcPath, ConversionProfiles.SUPPLY_CHAIN)
    .withClassificationResolver(resolver)
    .withEtimPropertyMappings(List.of(mapping));
```

The annotation is added alongside the normal property output. It retains the original IFC
property-set resource, set and property names, observed value, unit evidence, and whether the value
came from the occurrence or its type. When both are present, the occurrence annotation links to the
applicable type annotation with `supply:overrides`. The IFC property set itself is never typed as, or
equated with, the ETIM product class.

## Suggest missing ETIM classifications

Candidate generation is separately opt-in. Configure both the ETIM product dictionary and, when
available, the ETIM Modelling Classes dictionary, then enable suggestions on the request:

```java
var registry = new EtimClassificationResolver(
    "https://identifier.buildingsmart.org/uri/etim/etim/10.0",
    "https://identifier.buildingsmart.org/uri/etim/etim-mc/2025");
var cachePath = Path.of(".ifctolbd/classification-cache");
var refresh = new VersionedClassificationResolverCache(
    cachePath, "bsdd-etim-10-mc-2025", registry);
refresh.refreshCandidates(new ClassificationResolver.CandidateRequest("ETIM", "IfcDoor"));

var resolver = new VersionedClassificationResolverCache(cachePath, "bsdd-etim-10-mc-2025");

var request = new ConversionRequest(ifcPath, ConversionProfiles.SUPPLY_CHAIN)
    .withClassificationResolver(resolver)
    .withEtimClassificationSuggestions();
```

Discovery starts from bSDD's published IFC-entity relationships. If an ETIM Modelling Class is
published for the IFC entity, its forward and reverse class links can contribute product-class
candidates. Candidate identifiers always come from bSDD. Candidate lists and their definitions are
stored in the versioned resolver cache.

Ranking is deterministic and uses raw IFC observations made before enrichment: element names and
descriptions, explicit property identifiers, exact property names, values, datatypes and units.
Published relationships establish the search space but do not suffice by themselves. Weak or absent
independent evidence produces a `classificationStatus "abstained"` assessment. Retained suggestions
include their score, rank, evidence, contradictions and missing information. Reviewed
`EtimPropertyMapping` entries are intentionally excluded from class scoring because their target
class is already assumed; using them would be circular evidence. No LLM is required.

Suggestions use `classificationStatus "suggested"` and `supply:suggestedConcept`; they do not add an
ETIM RDF type or a definitive classification assertion. To record a human-reviewed decision, accept
the exact candidate URI for the converted element URI:

```java
var accepted = new EtimClassificationAcceptance(
    "https://example.com/building/product-guid",
    "https://identifier.buildingsmart.org/uri/etim/etim/10.0/class/EC000001",
    "review:classification-board/decision-42");

request = request.withAcceptedEtimClassifications(List.of(accepted));
```

Supplied IFC classifications, generated suggestions and reviewed acceptances are represented with
the distinct statuses `supplied`, `suggested` and `accepted`. An acceptance is only emitted when the
same class was generated as a sufficiently supported candidate.

## RDF additions and runnable conversion

The supply-chain profile adds `supply:ClassificationAssertion` resources through
`supply:hasClassification`. Registry-verified identifiers use `supply:resolvedConcept`; source URIs
that have not been checked use `supply:suppliedIdentifier`. Edition details and identifier
verification are recorded on the assertion. `supply:classificationCorrectness` remains an independent
field: supplied classifications are `not-assessed`, suggestions are `unconfirmed`, and reviewed
acceptances are `reviewed-accepted`.

ETIM property enrichment adds `supply:EtimPropertyAnnotation` resources through
`supply:hasPropertyAnnotation` in the property graph. An annotation links the authoritative
`supply:classProperty` and reusable `supply:property` identifiers, preserves the observed IFC value,
unit and source property set, and records its mapping method and occurrence/type provenance. An
occurrence value links to an overridden type value with `supply:overrides`. It does not replace the
original IFC property.

Dictionary rights in either the ordinary enriched graph or the TriG manifest can be inspected with:

```sparql
PREFIX supply: <https://w3id.org/ifctolbd/supply-chain#>
PREFIX dcterms: <http://purl.org/dc/terms/>

SELECT ?dictionary ?version ?owner ?rights ?license WHERE {
  ?dictionary a supply:ClassificationDictionary ;
              supply:version ?version ;
              dcterms:rightsHolder ?owner ;
              dcterms:rights ?rights .
  OPTIONAL { ?dictionary dcterms:license ?license }
}
```

The same program is checked in as
[`examples.Example15`](../IFCtoLBD/src/main/java/examples/Example15.java). Run its `main` method with
`<model.ifc>` for cached offline operation. Refresh the cache separately with the API shown above.
The standalone form below can be copied into a project that includes the converter dependency shown
in [the Java guide](java_examples.md). No network client is constructed during conversion.

```java
import java.nio.file.Path;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.linkedbuildingdata.ifc2lbd.*;

public class EtimConvert {
    public static void main(String[] args) throws Exception {
        var resolver = new VersionedClassificationResolverCache(
            Path.of(".ifctolbd/classification-cache"), "etim-10-mc-2025");
        try (var session = new ConversionSession();
             var converter = new IFCtoLBDConverter(session, "https://example.com/building/")) {
            var request = new ConversionRequest(Path.of(args[0]).toAbsolutePath().toString(),
                    ConversionProfiles.SUPPLY_CHAIN)
                .withClassificationResolver(resolver)
                .withEtimClassificationSuggestions();
            try (var result = converter.convert(request)) {
                var model = result.getModel();
                String query = """
                    PREFIX supply: <https://w3id.org/ifctolbd/supply-chain#>
                    SELECT ?element ?status ?concept ?evidence ?property ?value WHERE {
                      ?element supply:hasClassificationSuggestion ?suggestion .
                      ?suggestion a supply:ClassificationSuggestion ;
                                  supply:classificationStatus ?status ;
                                  supply:suggestedConcept ?concept .
                      OPTIONAL { ?suggestion supply:evidence ?evidence }
                      OPTIONAL {
                        ?element supply:hasPropertyAnnotation ?annotation .
                        ?annotation supply:property ?property ; supply:observedValue ?value .
                      }
                    }
                    ORDER BY ?element ?status ?property
                    """;
                try (var execution = QueryExecutionFactory.create(query, model)) {
                    ResultSetFormatter.out(System.out, execution.execSelect());
                }
                RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
            }
        }
    }
}
```

The checked-in offline fixtures use ETIM EC000005 (Cable end sleeve) and EC003535 (Interior door,
complete with frame). EC003535 distinguishes door leaf width (EF011812), doorframe width (EF011817),
and doorframe module width (EF011815). An IFC `IfcDoor` type alone does not establish EC003535, and
an unlabeled `OverallWidth` is not substituted for any of those ETIM measurements.

Recognized product identity fields include manufacturer, article/model, batch/lot, serial, warranty
and GTIN. Valid GTINs receive a canonical GS1 Digital Link. Environmental declarations include the
declaration identifier, operator, validity, declared unit/reference quantity and lifecycle indicators
for A1-A3, A4, C3 and D. Known declared units are represented with QUDT unit IRIs; unknown units stay
in their original IFC property and are not guessed. Normalized values and mappings carry provenance,
method and confidence.

Conversion is always offline. Registry access is confined to explicit cache refresh operations.
Unit and fixture tests are offline; the environment-gated live compatibility check belongs in the
explicit Maven `integration` profile.
