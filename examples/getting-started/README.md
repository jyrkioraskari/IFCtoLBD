# Three useful results in ten minutes

These examples turn a small, bundled IFC model into an answer you can check.
Each folder contains the input model, a saved converter configuration
(`configuration.args`), the SPARQL question (`query.rq`), and the expected
answer (`expected.txt`). No geometry installation or network service is used.

With the desktop application, choose a folder's `.ifc` file, convert it, paste
its `query.rq` into **Query**, and compare the table with `expected.txt`.
`configuration.args` is the saved, one-command version for the converter CLI.

## Run an example

Use the converter JAR from a release, or build it once from the repository root:

```sh
./mvnw -pl IFCtoLBD -am package -DskipTests
```

Then run one saved configuration from the repository root. Java expands the
`@...` argument file before the converter starts:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  @examples/getting-started/building-inventory/configuration.args
```

Open `examples/getting-started/building-inventory/result.txt`. It should match
`expected.txt`. The generated `output.ttl` remains available if you want to
inspect the RDF, but the useful result is the short table.

Run the other examples by changing `building-inventory` to
`data-completeness` or `revision-comparison`. On Windows, put the command on one
line and use the same `/` paths. To verify a result on macOS or Linux:

```sh
diff -u examples/getting-started/building-inventory/expected.txt \
  examples/getting-started/building-inventory/result.txt
```

## What each example shows

1. **Building inventory** groups the model's elements by storey and product
   type. The answer says that the one storey contains two walls.
2. **Data completeness** checks `LoadBearing` and `IsExternal` on every
   element. One wall deliberately lacks `LoadBearing`, so it appears in the
   result. An empty result would mean the selected fields are complete.
3. **Revision comparison** converts two revisions with one stable model scope.
   The second revision renames one wall. The result explains the removed and
   added labels; `changes.ttl` retains the complete machine-readable change
   graph.

The saved configurations use relative paths and therefore must be run from the
repository root. Copy a folder before experimenting if you want to keep its
checked expected result unchanged.
