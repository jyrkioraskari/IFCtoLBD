# IFCtoLBD MCP Server

This folder contains a Model Context Protocol (MCP) stdio server that exposes the IFCtoLBD Java converter as tools for MCP clients.

The server reuses the existing `../IFCtoLBD_NodeJS/IFCtoLBD.js` bridge and bundled converter jars, so no MCP SDK package is required.

## Requirements

- Node.js 18 or newer
- A Java runtime compatible with the IFCtoLBD jars
- The MCP package dependencies installed on the target machine

The server defaults to `../.tools/jdk` when `JAVA_HOME` is not set, and falls back to `./.tools/jdk` inside this MCP package.

Install dependencies in this folder on the same operating system where the MCP server will run:

```bash
cd IFCtoLBD_MCP
npm install
```

This matters for Windows: `java-bridge` uses a native optional package such as `java-bridge-win32-x64-msvc`. A `node_modules` directory copied from Linux will not contain that Windows package.

## Run

```bash
cd IFCtoLBD_MCP
npm install
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

- `convert_ifc_to_lbd`: converts an IFC file to RDF. It returns serialized RDF inline, or writes it to `outputPath`.
- `summarize_ifc_lbd`: converts an IFC file and returns triple count, subject count, and sample subjects.
- `query_ifc_lbd`: converts an IFC file and runs a SPARQL `SELECT` query against the in-memory Jena model.
- `list_ifc_elements_with_properties`: lists elements of an RDF type, such as `bot:Element` or `beo:Wall`, with IFCtoLBD simple properties and attributes grouped per element.
- `list_ifc_elements_with_geometry`: lists elements of an RDF type with generated geometry literals. WKT is included by default; OBJ can be included with `includeObj`.
- `ifctolbd_runtime_info`: returns runtime paths and Java bridge settings.

All tools accept `ifcPath`; when omitted, the sample `IFCtoLBD/src/main/resources/Duplex_A.ifc` is used.
