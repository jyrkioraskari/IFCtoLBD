# Measure surface clearance between selected elements

`SurfaceClearance` is an optional Java operation. Pass two model-scoped element
IRIs, their source revisions and IFC GlobalIds, two `GeometryResult` objects
from the existing deterministic geometry provider, an explicit affine alignment
from the second model into the first, and a tolerance in metres. Use
`SurfaceClearance.Alignment.identity()` only when the models share engineering
coordinates and length units have been resolved. The example uses 0.001 m.

From the repository root, run both checks:

```sh
JAVA_HOME="$PWD/.tools/jdk" PATH="$PWD/.tools/jdk/bin:$PATH" \
  ./mvnw -Dmaven.repo.local="$PWD/.m2/repository" -pl IFCtoLBD -am \
  -Dtest=SurfaceClearanceTest,SurfaceClearanceIfcExampleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`SurfaceClearanceIfcExampleTest` copies `duct.ifc` and `slab.ifc` into a fresh
temporary directory, converts them, and loads their meshes through IfcOpenShell.
It checks the 0.2 m duct-to-slab gap and the `query.rq` row in `expected.txt`.
The same test also checks the contact and gap in the two models from
`../verified-interfaces`. `SurfaceClearanceTest` checks separated, contact,
intersection, containment, missing geometry, an open mesh, endpoint reversal,
and equivalent millimetre geometry with a local placement. It runs
`query-all.rq` against a synthetic companion graph and checks
`expected-synthetic.txt`.

The result reports both IRIs and revisions, status, method, tolerance, and
distance in metres when computable. It does not turn missing geometry into
zero. A bounding box is never used as the final distance. Open and non-manifold
meshes have indeterminate penetration status when surfaces meet. The current
closed-solid test uses triangle intersections and ray parity; highly degenerate
or self-intersecting meshes need further validation. Triangle pairs are checked
exhaustively, so large meshes can take time. Alignment must be supplied by the
caller; this operation does not infer a registration between models.
