# Find IFC elements by model revision with SPOT

This example exports the two-wall IFC model as Turtle and prints the two wall
URIs that appear in the named model revision. Run it from the repository root
with Java 21. The build requires the dependencies described in
[the development guide](../../../docs/development.md).

```sh
./mvnw -pl IFCtoLBD -am package -DskipTests
java \
  -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --profile=spot-geometry \
  --spot-revision=https://example.org/models/two-walls/revision-1 \
  --spot-asset=https://example.org/assets/two-wall-building \
  --identifier-policy=STABLE_PROJECT \
  --project-namespace=https://example.org/projects/two-wall-building/ \
  --target_file=spot.ttl \
  --query=examples/getting-started/spot/query.rq \
  examples/getting-started/building-inventory/model.ifc
```

The CLI writes `spot.ttl` and prints two `?element` results. The
[saved query](query.rq) binds `?revision` to the explicit revision IRI above;
change both values together for another revision. The project namespace keeps
each element URI stable across revisions with the same IFC GlobalId. The
revision IRI identifies this particular model version and must change when the
model changes. If `--spot-revision` is omitted, `spot:represents` uses the
converter's `urn:sha256:` source resource, also recorded in the conversion
manifest. A project GUID or file name alone is not treated as a revision.

`--spot-asset` is optional and names the physical asset. Without it, the model
and element appearances are still exported; the validation graph reports
`AssetIdentityMissing`. No building is chosen from the IFC file. The default
Turtle includes SPOT triples in the general graph. `--hasTriG` writes a dataset
with the same general graph and separate named graphs for other conversion data.
`spot-geometry` enables the converter's existing element geometry output alongside
the SPOT appearance links. It needs a working IfcOpenShell geometry backend;
if unavailable, use `--profile=spot` for the same SPOT lookup without geometry.
The SPOT mapping itself does not assert placement matrices, bounding boxes, or
cross-document registration. If the IFC project
has multiple or missing 3D model contexts, the validation graph reports
`UnsupportedModelExtent` and no single `spot:VolumeSpace` is claimed.

Java callers can use `new ConversionRequest(path, ConversionProfiles.SPOT_GEOMETRY)`
with `.withSpotModelRevisionIri(revisionIri)` and, when known,
`.withSpotAssetIri(assetIri)`. The result's `getModel()` and dataset default
graph expose the same SPOT assertions and the converter's geometry links.

The [SPOT ontology](https://github.com/Design-Computation-RWTH/spot) was
authored by Anne Göbels, Oliver Schulz, and Fabian Rolke. This mapping uses
its published `https://w3id.org/spot#` terms and does not copy ontology code.
