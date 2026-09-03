# Supply-chain and sustainability output

The structured conversion API provides `ConversionProfiles.SUPPLY_CHAIN` and
`ConversionProfiles.SUSTAINABILITY`. Both preserve IFC property values while adding normalized
resources; the sustainability profile also enables unit output.

`IfcRelAssociatesClassification` is emitted as a classification assertion containing the original
system, edition, code, label and source location. IFCtoLBD never derives an authoritative identifier
from a label. To resolve identifiers, attach a `ClassificationResolver` to the request. The supplied
`BsddClassificationResolver` performs exact-code bSDD lookups for explicitly configured dictionary
URIs. `EclassClassificationResolver` and `EtimClassificationResolver` provide system-filtering
adapters for bSDD-hosted ECLASS and ETIM dictionaries. Wrap an adapter in
`VersionedClassificationResolverCache` for reproducible local caching.

```java
var online = new BsddClassificationResolver(Map.of(
    "ECLASS", "https://identifier.buildingsmart.org/uri/...",
    "ETIM", "https://identifier.buildingsmart.org/uri/..."));
var resolver = new VersionedClassificationResolverCache(
    Path.of(".ifctolbd/classification-cache"), "bsdd-v1", online);
var request = ConversionRequest.builder(ifcPath)
    .profile(ConversionProfiles.SUSTAINABILITY)
    .classificationResolver(resolver)
    .build();
```

Recognized product identity fields include manufacturer, article/model, batch/lot, serial, warranty
and GTIN. Valid GTINs receive a canonical GS1 Digital Link. Environmental declarations include the
declaration identifier, operator, validity, declared unit/reference quantity and lifecycle indicators
for A1-A3, A4, C3 and D. Known declared units are represented with QUDT unit IRIs; unknown units stay
in their original IFC property and are not guessed. Normalized values and mappings carry provenance,
method and confidence.

Registry access is opt-in. Unit and fixture tests are offline; live registry checks belong in the
explicit Maven `integration` profile.
