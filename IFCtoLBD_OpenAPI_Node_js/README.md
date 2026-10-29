# IFCtoLBD_OpenAPI_Node_js

`IFCtoLBD_OpenAPI_Node_js` is the modern HTTP API for IFCtoLBD. It keeps the
legacy `GET /hello` and `POST /convertIFCtoLBD` routes and adds a versioned API,
OpenAPI 3.1 documentation, health probes, bounded multipart uploads, structured
problem responses, and graceful shutdown.

The Node.js layer owns HTTP concerns. Conversion still runs in the Java 21
IFCtoLBD core through the same shaded distribution used by `IFCtoLBD_MCP`, so
the API does not duplicate IFC or RDF mapping logic.

## Requirements

- Node.js 20 or newer
- Java 21 or newer
- Maven when building the converter from source

## Install and run

From the repository root:

```sh
cd IFCtoLBD_OpenAPI_Node_js
npm install
npm run build:converter
npm test
npm start
```

The default address is <http://127.0.0.1:8080>. OpenAPI UI is at
<http://127.0.0.1:8080/docs/> and the machine-readable document is at
<http://127.0.0.1:8080/openapi.json>.

Convert an IFC file to Turtle:

```sh
curl --fail-with-body \
  -H 'Accept: text/turtle' \
  -F 'ifcFile=@../IFCtoLBD/src/main/resources/Duplex_A.ifc' \
  -F 'profile=properties-opm' \
  -o ifc2lbd.ttl \
  http://127.0.0.1:8080/api/v1/convert
```

Supported response formats are Turtle, JSON-LD, RDF/XML, and TriG. Select one
with `Accept` or the `format` multipart field. TriG preserves the converter's
named graph partitioning; the other formats serialize the combined data model.

The compatibility route accepts the same form:

```text
POST /convertIFCtoLBD
```

Its historical defaults remain `properties-opm`, base URI
`https://lbd.example.com/`, non-blank-node properties, and property level 3.

## API routes

| Route | Purpose |
| --- | --- |
| `POST /api/v1/convert` | Versioned multipart conversion API |
| `POST /api/v1/retrieve` | Bounded find, inspect, or linked-record retrieval over uploaded RDF |
| `POST /convertIFCtoLBD` | Legacy-compatible alias |
| `GET /api/v1/capabilities` | Profiles, validation packs, formats, versions, and limits |
| `GET /health/live` | Process liveness probe |
| `GET /health/ready` | JVM and converter readiness probe |
| `GET /hello` | Legacy liveness response (`OK!`) |
| `GET /docs/` | Interactive OpenAPI documentation |
| `GET /openapi.json` | OpenAPI 3.1 document |

Uploads are written with owner-only permissions beneath the operating-system
temporary directory and removed after the response. Conversion is serialized by
default because each request owns substantial native JVM/Jena resources. The
queue is bounded; overload returns HTTP 503 instead of allowing unbounded memory
growth.

## Configuration

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | Listen address; use `0.0.0.0` in a container |
| `PORT` | `8080` | Listen port |
| `JAVA_HOME` | repository-local JDK when found | Java 21 installation |
| `IFCTOLBD_API_CONVERTER_JAR` | `../IFCtoLBD_MCP/lib/ifctolbd-converter.jar` | Shaded converter distribution |
| `IFCTOLBD_API_MAX_UPLOAD_BYTES` | `268435456` | Maximum IFC upload size |
| `IFCTOLBD_API_MAX_QUEUE` | `8` | Waiting conversions allowed in addition to the active conversion |
| `IFCTOLBD_API_TEMP_DIR` | OS temporary directory | Temporary upload/output root |
| `IFCTOLBD_API_TRUST_PROXY` | `false` | Trust reverse-proxy forwarding headers |
| `LOG_LEVEL` | `info` | Fastify/Pino log level |

This service intentionally does not enable permissive CORS, authentication, or
TLS. Put it behind an authenticated HTTPS reverse proxy for network deployment,
limit request sizes there as well, and keep it off the public Internet unless
the deployment has appropriate access controls and resource quotas.

## Validation and stable identifiers

Set `validate=true` to run all bundled standard SHACL packs, or provide a
comma-separated `validationPacks` value. Validation graphs are preserved in
TriG output. `identifierPolicy=STABLE_PROJECT` requires an explicit absolute
`projectNamespace`; no project namespace is inferred from an uploaded filename.

## Focused retrieval

Retrieval is separate from conversion. Upload converted RDF plus optional companion graphs; link targets are not fetched:

```sh
curl --fail-with-body \
  -F 'dataFile=@building.ttl' \
  -F 'externalLinksFile=@building.links.ttl' \
  -F 'externalLinkReportFile=@building.links-report.ttl' \
  -F 'provenanceFile=@building.provenance.ttl' \
  -F 'task=inspect' \
  -F 'identifier=2O2Fr$t4X7Zf8NOew3FLOH' \
  -F 'limit=200' \
  http://127.0.0.1:8080/api/v1/retrieve
```

Use `task=find` with `identifier`, `type`, or `spatialLocation`, or `task=links` with `identifier`.
Results distinguish empty, missing, ambiguous, unsupported, and execution-error outcomes and report
truncation explicitly. Accepted inputs are Turtle, N-Triples, and RDF/XML.

## Checks

```sh
npm run check       # JavaScript syntax
npm test            # HTTP and option-validation tests; no JVM required
npm run smoke       # real conversion using the shaded Java distribution
```

## Container image

Build the converter first, then build from the repository root so Docker can
copy the shaded JAR and repository legal notices:

```sh
npm --prefix IFCtoLBD_OpenAPI_Node_js run build:converter
docker build -f IFCtoLBD_OpenAPI_Node_js/Dockerfile -t ifctolbd_openapi_node_js .
docker run --rm -p 8080:8080 ifctolbd_openapi_node_js
```

The image includes Node.js 22 and a Java 21 runtime. Dependency licences remain
in `node_modules`; direct dependency credits are summarized in
[`THIRD_PARTY.md`](THIRD_PARTY.md), and the converter JAR carries its aggregated
licence and notice resources.
