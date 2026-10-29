# Optional identity, external links, and provenance

[Documentation home](README.md) · [Existing conversion workflow](quick-start.md)

These source-development features wrap the existing converter. They are optional and are not present in
the pinned 2.49.0 desktop release. With no new option, IFCtoLBD retains its historical identifiers,
graphs, CLI flags, profiles, property modes, and geometry behavior. Conversion never needs network access.

## Identifier policy

`EXISTING` is the default. `STABLE_PROJECT` must be explicitly selected with a persistent absolute,
non-`file:` project namespace:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --identifier-policy STABLE_PROJECT \
  --project-namespace https://example.org/projects/campus-a \
  --target_file building.ttl model.ifc
```

The stable element IRI is `<project-namespace>/ifc-globalid/<encoded-global-id>`. The GlobalId is encoded
as UTF-8 with RFC 3986 percent encoding (uppercase hexadecimal); no name, geometry, file name, order, or
STEP line number participates. Different namespaces therefore separate identical GlobalIds.

Stable identity applies to mapped IFC objects that have one usable 22-character IFC GlobalId. It does not
promise stable blank nodes, property states, property-set resources, geometry nodes, or inferred resources.
Missing and duplicate GlobalIds are recorded in the identity report. Duplicate objects receive distinct
conversion-local IRIs and make a matching link row ambiguous, so they are never silently merged. A changed
GlobalId is a new identity and requires explicit reconciliation outside this policy.

Java callers use `request.withStableProjectIdentity(namespace)`. Old requests and saved desktop presets
default to `EXISTING`; the desktop Settings view labels its single namespace field **Project URI** and places
the identity-policy selector under **Product / BOT**. The optional external-link mapping and its file selector
are at the bottom of **Filters**. Older desktop presets that stored a separate base URI remain loadable. The
older `revision-ready`/`modelScope` mechanism remains accepted for compatibility.

## External-link mappings

Pass one or more `--link-mapping` CSV or JSON files. Each row has `globalId`, `externalResourceIri`,
`predicateIri`, and optional `source`, `method`, `confidence` (0 through 1), `validFrom`, `validTo`,
and `recordedAt`. Temporal values use ISO-8601 instants. CSV aliases
`ifcGlobalId`, `externalIri`, and `relationshipPredicateIri` are accepted. Rows are resolved only against
the conversion receiving the mapping; no registry lookup or cross-model GlobalId search occurs.

```sh
--link-mapping links.json \
--link-output building.links.ttl \
--link-report building.links-report.ttl
```

The source conversion graph is unchanged. The link graph preserves legitimate multiple predicates and
targets and never substitutes `owl:sameAs`. Exact duplicate rows are emitted once. The report gives every
row a `matched`, `unmatched`, `ambiguous`, `invalid`, or `duplicate` status plus supplied evidence. External
IRIs are validated but never fetched.

Resolved links remain in a companion graph, so ordinary reconversion cannot silently delete the maintained
mapping input. Reapply that reviewed input to the new conversion. Unmatched and ambiguous rows remain report
findings and are never attached to another element. IFC GlobalId matching is project-scoped here; it is not a
universal identity guarantee.

Structured Java results expose `getExternalLinkModel()`, `getExternalLinkReportModel()`, and
`getIdentityReportModel()`. MCP `load_ifc` accepts `identifierPolicy`, `projectNamespace`, and
`mappingPaths`; additive tools inspect coverage, list unresolved rows, explain link evidence, and report the
identifier policy. Existing MCP tool names and required inputs are unchanged.

## Manifest and revisions

The structured result retains an immutable manifest containing the source SHA-256, converter version,
profile/options fingerprint, policy and namespace, mapping checksums, bundled dictionary versions,
generation time, validation/audit status, and graph references. Paths are not included. CLI export remains
optional through `--provenance-output`.

With the opt-in `evidence` profile, the companion manifest also contains explicit element-level source
entities and mapping kind. See [traceable graph retrieval](traceable-retrieval.md) for retrieval contracts,
validation, and interface examples.

Stable-project revision comparison requires exactly matching persistent namespaces and policies. It reports
retained, added, and removed GlobalIds and counts links whose former RDF subjects are absent. It refuses to
claim continuity for existing identity mode or incompatible namespaces.

## Runnable sensor example

See [the offline BIM–sensor example](../examples/bim-sensor-integration/README.md). It demonstrates the
question “Which modeled HVAC terminals have linked temperature sensors, and what are their latest readings?”, separates
BOT/SOSA/Brick discovery from the local measurement API, compares two revisions, and repeats mapping with
existing identity mode.

## Optional geometry artifact formats

Embedded geometry remains OBJ by default. The external-artifact workflow additionally supports binary
glTF (`GLB`, media type `model/gltf-binary`) and self-contained glTF 2.0 (`GLTF`, media type
`model/gltf+json`). These are generated directly from the backend-neutral tessellated mesh, without a new
native converter or network service. Java callers use
`withGeometryArtifactFormats(GeometryArtifactFormat.GLB)`.

For CLI export, select both a content-addressed directory and its public IRI:

```sh
--profile geometry-external \
--geometry-artifact-directory geometry \
--geometry-artifact-base-uri https://example.org/models/a/geometry/ \
--geometry-artifact-format GLB
```

The format option may be repeated. MCP `load_ifc` accepts `geometryFormats` with `OBJ`, `GLB`, or `GLTF`.
Omitting it preserves existing OBJ artifact and cache-key behavior.

To open embedded or external geometry and inspect it with orbit, grounded walk,
fly, or assisted-walk controls, continue with the
[LBDBabylon walkthrough](lbd-babylon.md). Its collision policy uses RDF types:
physical elements can block movement, while spaces, openings, voids, spatial
containers, and unknown helper geometry remain non-blocking.
