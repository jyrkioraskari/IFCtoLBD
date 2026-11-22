# Import a reviewed interface between two IFC models

`existing.ifc` has one wall and `renovation.ifc` has two insulation coverings.
One touches the wall; the other has a 0.2 m surface gap.
They deliberately reuse the same IFC GlobalId. The separate `existing` and
`renovation` model scopes give their output elements distinct IRIs.
`reviewed-pairs.json` records one site-inspected contact, identifies each
endpoint by model scope, source SHA-256 revision, and element IRI, and retains
the review reference. The list is local and does not make a network request.

From the repository root, run the Java example test:

```sh
JAVA_HOME="$PWD/.tools/jdk" PATH="$PWD/.tools/jdk/bin:$PATH" \
  ./mvnw -Dmaven.repo.local="$PWD/.m2/repository" -pl IFCtoLBD -am \
  -Dtest=ReviewedInterfaceImporterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The test copies both IFC files into a fresh temporary directory, converts them
with `REVISION_READY` and distinct model scopes, imports the checked local
pair list, writes a companion Turtle graph, reloads it, and runs `query.rq`
against the two conversion graphs plus the companion. The returned insulation,
wall, and review reference must match `expected.txt`. Reversed imports of the
same physical pair produce one BOT interface. Missing endpoints and identical
element IRIs across models produce diagnostics; neither case writes an
interface. To resolve an IRI collision, reconvert with distinct `modelScope`
values and update the reviewed pair list to the new IRIs and source revisions.

The importer trusts the reviewed contact and keeps it distinct from the
optional geometric candidate graph. `SurfaceClearanceIfcExampleTest` loads the
same files through IfcOpenShell 0.8.5, checks the contact and 0.2 m gap, and
finds one geometric candidate without creating another verified BOT interface.
If alignment is missing, candidate discovery returns a diagnostic and no
candidate. Follow the [clearance example](../clearance/README.md) for the
measurement and its limits.
