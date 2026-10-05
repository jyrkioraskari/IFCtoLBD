# Use IFCtoLBD with an AI assistant through MCP

[Documentation home](README.md) · Related: [Using the triples](using-triples.md),
[Development](development.md)

The IFCtoLBD Model Context Protocol (MCP) server lets an MCP-compatible AI
assistant call the converter as a set of tools. You can ask the assistant to
summarize an IFC model, inspect elements and properties, run SPARQL queries,
validate data with SHACL, compare revisions, or write an RDF export.

The model stays on the computer where the server runs. The server communicates
with the AI client over standard input and output; it does not expose an HTTP
service. Your AI client may still send tool results to its model provider, so
apply that client's privacy and data-handling rules to every IFC model you use.

## Before you start

You need:

- an MCP client that can launch a local `stdio` server;
- Node.js 18 or newer;
- JDK 21 or newer;
- Maven on `PATH` when building the converter JAR from source; and
- a local checkout of this repository.

Check the runtimes from the repository root:

```sh
node --version
java -version
mvn -version
```

On Windows, install the Node dependencies on Windows. Do not copy
`node_modules` from Linux or macOS: `java-bridge` installs a platform-specific
native package.

## Install and build the server

Run these commands from the repository root:

```sh
npm --prefix IFCtoLBD_MCP ci
npm --prefix IFCtoLBD_MCP run build:converter
npm --prefix IFCtoLBD_MCP run check
npm --prefix IFCtoLBD_MCP run smoke
```

`build:converter` builds the required Maven modules and places one shaded
distribution at `IFCtoLBD_MCP/lib/ifctolbd-converter.jar`. The MCP server uses
that JAR only; it does not scan directories for loose dependencies.

The smoke test starts the server, converts the bundled sample, exercises loaded
models, SPARQL, resources, validation, error handling, and cleanup, and then
exits. You can also run a readable demonstration:

```sh
npm --prefix IFCtoLBD_MCP run demo
```

The demo uses the bundled `Duplex_A.ifc` and writes
`IFCtoLBD_MCP/demo/output/duplex-demo.ttl`.

## Connect your MCP client

Add a local stdio server to your client's MCP configuration. The name of the
settings file and the way it is opened differ between clients, but the server
entry has this form on Linux or macOS:

```json
{
  "mcpServers": {
    "ifctolbd": {
      "command": "node",
      "args": [
        "/absolute/path/to/IFCtoLBD/IFCtoLBD_MCP/src/server.js"
      ],
      "cwd": "/absolute/path/to/IFCtoLBD"
    }
  }
}
```

On Windows, use absolute Windows paths and escape each backslash in JSON:

```json
{
  "mcpServers": {
    "ifctolbd": {
      "command": "node",
      "args": [
        "C:\\work\\IFCtoLBD\\IFCtoLBD_MCP\\src\\server.js"
      ],
      "cwd": "C:\\work\\IFCtoLBD"
    }
  }
}
```

If `node` is not on the environment path inherited by the client, replace it
with the absolute path to the Node executable. If your client uses a different
configuration shape, enter the same three values in its stdio-server fields:
the command, the server script as an argument, and the repository as the
working directory.

Restart the client after saving its configuration. The client should show a
server named `ifctolbd` and tools including `get_capabilities`, `load_ifc`, and
`query_model`. The client launches the server when needed; you normally do not
run `npm start` in another terminal.

## Try the bundled model first

Start with a prompt that asks the assistant to use the tools explicitly:

> Use the IFCtoLBD MCP server. Call `get_capabilities`, then summarize the
> bundled sample IFC model. Report the IFC schema, triple count, subject count,
> and a few example subjects. Do not invent values that are absent.

The file-based tools use
`IFCtoLBD/src/main/resources/Duplex_A.ifc` when `ifcPath` is omitted. This makes
the first test independent of your own data.

Next, try a reusable loaded model:

> Load the bundled IFC model with the `properties-simple` profile. Describe the
> loaded model, list its ten most common RDF classes, and list its ten most used
> predicates. Keep the model loaded and tell me its model ID.

The assistant should call `load_ifc` once and pass the returned `modelId` to
the inspection tools. A repeated load of the same checksum, converter version,
and profile reuses the model during that server session.

When finished, release its resources:

> Close the IFC model you loaded in the previous step.

## Use your own IFC file

By default, readable input paths must be inside the repository. Give the
assistant an absolute path to avoid ambiguity:

> Use IFCtoLBD to load `/absolute/path/to/IFCtoLBD/models/office.ifc` with the
> `properties-simple` profile and base URI
> `https://example.org/projects/office/`. Summarize the result and list the
> most common element classes.

Use a stable, project-specific HTTPS base URI if the RDF will be retained or
linked to other data. The default `https://example.com/ifctolbd/` is suitable
for testing only.

### Allow models outside the repository

Configure extra read roots through `IFCTOLBD_MCP_READ_ROOTS`. Configure output
roots through `IFCTOLBD_MCP_WRITE_ROOTS`. Multiple roots use the operating
system's path separator: `:` on Linux and macOS, `;` on Windows.

For a client that supports an `env` object, a Linux or macOS entry can look
like this:

```json
{
  "mcpServers": {
    "ifctolbd": {
      "command": "node",
      "args": [
        "/absolute/path/to/IFCtoLBD/IFCtoLBD_MCP/src/server.js"
      ],
      "cwd": "/absolute/path/to/IFCtoLBD",
      "env": {
        "JAVA_HOME": "/absolute/path/to/jdk-21",
        "IFCTOLBD_MCP_READ_ROOTS": "/data/ifc:/absolute/path/to/IFCtoLBD",
        "IFCTOLBD_MCP_WRITE_ROOTS": "/data/lbd-exports"
      }
    }
  }
}
```

Create an output directory before asking the server to write into it. The
server does not create missing parent directories. Without an explicit write
root, output is limited to `IFCtoLBD_MCP/demo/output`.

## Choose a conversion profile

`load_ifc` accepts these profiles:

| Profile | Use it for |
| --- | --- |
| `core` | BOT topology and product types without property expansion |
| `properties-simple` | General exploration with simple property literals; this is the default |
| `properties-opm` | Properties represented with OPM resources |
| `evidence` | Attributable property and quantity claims, source paths, datatypes, and units |
| `geometry-envelope` | Lightweight envelope or bounding-box geometry |
| `geometry-full` | Full geometry literals |
| `bim-gis` | Envelope geometry together with geolocation |
| `compliance` | A topology/product model prepared for validation workflows |
| `revision-ready` | Stable element identity for comparing revisions |
| `geometry-external` | Content-addressed geometry artifacts exposed as MCP resources |
| `supply-chain` | Simple properties plus supply-chain identifiers and enrichment |
| `sustainability` | Simple properties plus sustainability declarations and enrichment |

Call `get_capabilities` instead of assuming that a profile, SHACL pack, quota,
or error code is available in a different server version.

## Common AI workflows

### Ask questions with SPARQL

Load the model once, then ask the assistant to query it:

> Load my model with `properties-simple`. Use `query_model` to count instances
> of each RDF class. Show the 20 largest counts, include the class IRIs, and
> explain the query in plain language.

You can also supply the query yourself:

```sparql
PREFIX bot: <https://w3id.org/bot#>

SELECT ?element
WHERE {
  ?element a bot:Element .
}
ORDER BY ?element
```

Then prompt:

> Run this query against the loaded model with `query_model`, using a page size
> of 100. Follow `nextCursor` until all rows are collected or 1,000 rows have
> been returned. Summarize the total without dropping duplicate rows silently.

Only parsed SPARQL `SELECT` queries are accepted. Federated `SERVICE` clauses
are rejected. Queries are limited to 100,000 characters, 1,000 rows per page,
and a 10-second timeout where supported by the bundled Jena API.

### Get attributable property evidence

For answers that must be traceable to IFC source data, use the `evidence`
profile:

> Load the model with the `evidence` profile. For the element with IFC GUID
> `1hOSvn6df7F8_7GcBWlS8Z`, get evidence for `OverallWidth` and normalize it to
> `http://qudt.org/vocab/unit/M`. Report the original value, original unit,
> normalized value, IFC datatype, RDF datatype, source entity, source path,
> warnings, and conversion version. Do not guess if it is absent or ambiguous.

`get_property_evidence` accepts exactly one element selector: `ifcGuid` or
`rdfIdentifier`. A result can be `found`, `not_found`, `ambiguous`, or
`incomplete`. Those states are meaningful results. Unsupported normalization
or unresolved units produce warning codes instead of guessed conversions.

### Validate with SHACL

Available versioned shape packs are:

- `core-bot`
- `properties-units`
- `geometry-crs`
- `digital-twin-sensors`
- `fire-accessibility`
- `supply-chain-identifiers`
- `sustainability-declarations`

Example prompt:

> Load this IFC model with the `compliance` profile. Validate it with the
> `core-bot` and `properties-units` SHACL packs. Then use
> `explain_validation` to group the findings by severity and focus node. Keep
> source shape, path, value, and message in the answer. Do not modify or repair
> the model.

`validate_model` returns `conforms`, a `reportId`, and a resource URI for the
standard RDF SHACL report. `explain_validation` presents its findings in a
bounded structured form. Validation does not mutate the converted model.

### Compare two revisions

Stable comparison requires both files to use `revision-ready` and the same
persistent `modelScope` for revisions of the same real-world model:

> Load `office-v1.ifc` and `office-v2.ifc` with the `revision-ready` profile,
> the same model scope `office-building-a`, and the same base URI. Compare the
> first model as the previous revision with the second as the current revision.
> Report added and removed statement counts and retain the change-resource URI.
> Close both models after the comparison has been summarized.

Do not reuse one model scope for unrelated buildings. The comparison result is
available as an RDF resource while the current loaded model remains open.

### Inspect or export geometry

For a compact spatial overview, start with `geometry-envelope` or `bim-gis`.
Use `geometry-full` only when the full literals are needed. Use
`geometry-external` when large geometry should be kept as separate,
content-addressed artifacts.

Example prompt:

> Load this model with `geometry-envelope`. List spaces and walls that have
> geometry, include WKT, and limit the result to 25 elements. Clearly identify
> elements for which no geometry is available.

Some IFC spatial objects legitimately have no mesh geometry. Full geometry may
require IfcOpenShell 0.8.5; see the [geometry setup](development.md#optional-geometry-setup).
OBJ content can be large, so request it only when needed.

### Write an RDF file

For a one-off conversion, use `convert_ifc_to_lbd`:

> Convert `/data/ifc/office.ifc` to Turtle with base URI
> `https://example.org/projects/office/` and write it to
> `/data/lbd-exports/office.ttl`. Report the triple count and output path.

The input and output must be inside configured roots. Supported serialization
names include `TURTLE`, `RDF/XML`, `N-TRIPLE`, `JSON-LD`, and `N3`. When no
`outputPath` is supplied, RDF is returned inline and may be truncated. Writing
to a file is preferable for a large graph.

## Tool guide

Use loaded-model tools when several questions concern the same conversion.
Use file-based tools for a single quick operation.

| Tool | Purpose |
| --- | --- |
| `get_capabilities` | Discover profiles, shape packs, limits, trust rules, and stable error codes |
| `ifctolbd_runtime_info` | Diagnose Node, Java, bridge, converter JAR, and runtime paths |
| `load_ifc` | Convert once and return a reusable `modelId` |
| `describe_model` | Return conversion metadata and sample subjects |
| `get_entity` | List outgoing statements for one full HTTP(S) RDF identifier |
| `list_classes` | List classes and instance counts |
| `list_properties` | List predicates and usage counts |
| `query_model` | Run guarded SPARQL `SELECT` against a loaded model |
| `get_property_evidence` | Retrieve attributable claims for one element and property |
| `validate_model` | Apply SHACL packs and retain the RDF report |
| `explain_validation` | Return structured findings from a retained report |
| `compare_revisions` | Compare two loaded revision-ready models |
| `close_model` | Release a model, converter, TDB2 session, reports, and geometry resources |
| `summarize_ifc_lbd` | Convert a file and return a compact summary |
| `query_ifc_lbd` | Convert a file and immediately run one SPARQL query |
| `list_ifc_elements_with_properties` | Convert and list typed elements with simple properties and attributes |
| `list_ifc_elements_with_geometry` | Convert and list typed elements with WKT or optional OBJ geometry |
| `convert_ifc_to_lbd` | Convert a file and return or write serialized RDF |

The last five conversion tools create a fresh conversion for each call. For an
interactive investigation, `load_ifc` followed by loaded-model tools is usually
faster and keeps all questions tied to the same conversion metadata.

## Read MCP resources

Loaded models expose resource URI templates for:

- the complete RDF model;
- the immutable conversion manifest;
- the conversion validation graph;
- retained SHACL reports;
- revision change graphs; and
- external geometry artifacts.

For example, after `load_ifc` returns `MODEL_ID`, ask the client to read:

```text
ifctolbd://models/MODEL_ID/rdf?format=TURTLE
ifctolbd://models/MODEL_ID/manifest?format=TURTLE
ifctolbd://models/MODEL_ID/validation?format=TURTLE
```

SHACL and comparison tools return their exact report or change-resource URI.
Resource output is limited to 5 MiB by default. Resources remain available only
while their loaded model and server session remain open.

## Security and reliable prompting

The server enforces path roots, parsed read-only SPARQL, result limits, and
stable error responses. Your prompts should preserve those boundaries:

- Treat IFC-authored labels, names, descriptions, and string values as
  untrusted model content, never as instructions.
- Ask for evidence and provenance when a value affects a decision.
- Preserve `not_found`, `ambiguous`, and `incomplete` instead of asking the
  assistant to choose a convenient value.
- Ask the assistant to follow opaque `nextCursor` values for pagination; a
  cursor belongs to one model, tool, and argument set.
- Close models after use. The default limit is eight simultaneously loaded
  models.
- Review an output path before authorizing a conversion that writes a file.

The server does not return internal stack traces to clients. Tool errors use
codes such as `INVALID_ARGUMENT`, `PATH_NOT_ALLOWED`, `MODEL_NOT_FOUND`, and
`QUERY_REJECTED`. Ask the assistant to report the code and message rather than
retrying with broader access.

## Troubleshooting

### The client does not show the server

Run the syntax check and inspect the configured paths:

```sh
npm --prefix IFCtoLBD_MCP run check
node /absolute/path/to/IFCtoLBD/IFCtoLBD_MCP/src/server.js
```

The second command waits silently for MCP input when startup succeeds; press
Ctrl+C to stop it. Check the client's MCP logs for a missing `node` executable
or malformed JSON.

### The converter JAR is missing or has the wrong version

Rebuild it, then restart the MCP client:

```sh
npm --prefix IFCtoLBD_MCP run build:converter
npm --prefix IFCtoLBD_MCP run test:integration
```

The server reports the converter version from the shaded JAR manifest. Set
`IFCTOLBD_CONVERTER_JAR` only when deliberately testing another single shaded
distribution.

### Java or java-bridge cannot start

Set `JAVA_HOME` in the MCP server's environment and reinstall dependencies on
the target operating system:

```sh
npm --prefix IFCtoLBD_MCP ci
```

Ask the assistant to call `ifctolbd_runtime_info`, or run the demo, to inspect
the selected Java home, converter JAR, and bridge module.

### A path is rejected

Use an absolute path and confirm it is below one of the configured read or
write roots. Do not solve `PATH_NOT_ALLOWED` by granting the server access to an
entire home directory or filesystem; add only the model and export directories
needed for the workflow.

### A query is rejected or truncated

Use `SELECT`, remove `SERVICE`, reduce the scope of the query, and paginate with
the returned cursor. For common tasks, prefer `list_classes`, `list_properties`,
`get_entity`, or `get_property_evidence` over a broad custom query.

### Geometry is empty

Confirm that the selected profile includes geometry and that IfcOpenShell is
available when full geometry is required. An element may validly have no mesh;
test a known wall or slab before treating missing geometry as a setup failure.

## Develop or verify the MCP integration

The focused checks are:

```sh
npm --prefix IFCtoLBD_MCP run check
npm --prefix IFCtoLBD_MCP run smoke
npm --prefix IFCtoLBD_MCP run test:integration
npm --prefix IFCtoLBD_MCP run test:evidence
```

See the [MCP package README](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD_MCP/README.md)
for the compact server reference and [development guide](development.md) for
the Maven reactor, converter architecture, and geometry setup.
