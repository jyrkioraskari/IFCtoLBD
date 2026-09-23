# IFCtoLBD MCP Server

This folder contains a Model Context Protocol (MCP) stdio server that exposes the IFCtoLBD Java converter as tools for MCP clients.

The server uses `java-bridge` with one shaded converter distribution. It does not scan loose JAR directories.

## Requirements

- Node.js 18 or newer
- A Java runtime compatible with the IFCtoLBD jars
- The MCP package dependencies installed on the target machine
- Maven, when building the converter distribution from source

The server defaults to `../.tools/jdk` when `JAVA_HOME` is not set, and falls back to `./.tools/jdk` inside this MCP package.

Install dependencies in this folder on the same operating system where the MCP server will run:

```bash
cd IFCtoLBD_MCP
npm install
npm run build:converter
```

This matters for Windows: `java-bridge` uses a native optional package such as `java-bridge-win32-x64-msvc`. A `node_modules` directory copied from Linux will not contain that Windows package.

## Run

```bash
cd IFCtoLBD_MCP
npm install
npm run build:converter
npm run check
npm start
```

## Demo App

Run the demo MCP client:

```bash
cd IFCtoLBD_MCP
npm install
npm run demo
```

The demo app sends the JSON-RPC request stream in `demo/demo-input.jsonl` to `src/server.js` over stdio, renders the MCP responses with `demo/print-demo-results.js`, lists tools, summarizes the bundled `Duplex_A.ifc`, runs a small SPARQL query, and writes Turtle output to:

```text
IFCtoLBD_MCP/demo/output/duplex-demo.ttl
```

## MCP Client Configuration

Example stdio configuration:

```json
{
  "mcpServers": {
    "ifctolbd": {
      "command": "node",
      "args": ["/home/jyrkio/IFCtoLBD/IFCtoLBD_MCP/src/server.js"],
      "cwd": "/home/jyrkio/IFCtoLBD"
    }
  }
}
```

## Tools

- `get_capabilities`: reports conversion profiles, evidence fields, supported selectors, limits, SHACL packs, trust handling, pagination, and stable error codes.
- `load_ifc`: converts once and returns a reusable model ID, IFC checksum, schema, converter version, and conversion profile. Identical checksum/version/profile requests reuse the loaded model.
- `get_property_evidence`: returns attributable property, quantity, or attribute claims for one element selected by IFC GUID or RDF identifier. Use the `evidence` profile to retain source locations, IFC datatypes, and unit-resolution metadata.
- `compare_revisions`: compares two compatible `revision-ready` models and exposes the RDF change graph as a resource.
- `describe_model`, `get_entity`, `list_classes`, and `list_properties`: inspect a loaded model without reconversion.
- `query_model`: runs a parsed, row-limited SPARQL `SELECT` query against a loaded model. Federated `SERVICE` clauses are rejected.
- `validate_model`: applies one or more versioned SHACL packs and retains the standard RDF validation report without changing the model.
- `explain_validation`: returns the report's focus nodes, paths, values, severities, source shapes, and messages in structured form. It does not repair data.
- `close_model`: releases the model, converter, TDB2 session, and geometry resources.
- `convert_ifc_to_lbd`: converts an IFC file to RDF. It returns serialized RDF inline, or writes it to `outputPath`.
- `summarize_ifc_lbd`: converts an IFC file and returns triple count, subject count, and sample subjects.
- `query_ifc_lbd`: converts an IFC file and runs a SPARQL `SELECT` query against the in-memory Jena model.
- `list_ifc_elements_with_properties`: lists elements of an RDF type, such as `bot:Element` or `beo:Wall`, with IFCtoLBD simple properties and attributes grouped per element.
- `list_ifc_elements_with_geometry`: lists elements of an RDF type with generated geometry literals. WKT is included by default; OBJ can be included with `includeObj`.
- `ifctolbd_runtime_info`: returns runtime paths and Java bridge settings.

File-conversion tools accept `ifcPath`; when omitted, the sample `IFCtoLBD/src/main/resources/Duplex_A.ifc` is used. Loaded-model tools accept the `modelId` returned by `load_ifc`.

### Evidence workflow

Load once with `profile: "evidence"`, then call `get_property_evidence` with exactly one element selector and a property name. The result has a `found`, `not_found`, `ambiguous`, or `incomplete` status. Absence and ambiguity are normal results, so clients should not infer or select a value silently. Optional `normalizeTo` supports a bounded set of compatible QUDT length, area, volume, and mass units; unsupported or unresolved conversions produce warning codes rather than guessed values.

IFC-authored labels, names, descriptions, and string values are returned as data marked `untrusted_model_content`. Clients must never treat that content as instructions. Server-generated status, warning, and error fields are separate from model-authored text.

`get_entity`, `list_classes`, `list_properties`, `query_model`, and `get_property_evidence` return opaque `nextCursor` values when another page is available. A cursor is scoped to its model, tool, and query and cannot be reused with different arguments.

## Security and resource limits

Input files are restricted to the repository root by default. Output files are restricted to `IFCtoLBD_MCP/demo/output`. Configure narrower or additional roots with the platform-separated `IFCTOLBD_MCP_READ_ROOTS` and `IFCTOLBD_MCP_WRITE_ROOTS` environment variables. `IFCTOLBD_MCP_MAX_MODELS` controls the number of simultaneously loaded models (default: 8).

SPARQL is parsed by Jena and limited to `SELECT`; `SERVICE` is disabled, queries are capped at 100,000 characters, results at 1,000 rows, and a 10-second timeout is applied when supported by the bundled Jena API. Prefer the domain-specific inspection tools for agent workflows.

Tool failures use MCP `isError` results with stable codes such as `INVALID_ARGUMENT`, `PATH_NOT_ALLOWED`, `MODEL_NOT_FOUND`, and `QUERY_REJECTED`. Internal stack traces are not returned to clients. Call `get_capabilities` for the complete code list and active quotas.

The available SHACL packs are `core-bot`, `properties-units`, `geometry-crs`, `digital-twin-sensors`, `fire-accessibility`, `supply-chain-identifiers`, and `sustainability-declarations`, currently at version 1.0.0. Reports are available as `ifctolbd://models/{modelId}/validation/{reportId}` resources.

`load_ifc` accepts the named profiles, including `evidence`, `revision-ready`, `compliance`, and
`geometry-external`. The `evidence` profile uses OPM property resources, preserves IFC source links, and enables unit resolution. Its `ConversionResult` graphs are resources at
`ifctolbd://models/{modelId}/manifest` and `ifctolbd://models/{modelId}/validation`.
External geometry is available at the artifact URI recorded in the RDF manifest. Set
`IFCTOLBD_CONVERTER_JAR` only to select another single shaded distribution; the reported
converter version always comes from that JAR's `Implementation-Version` manifest entry.
