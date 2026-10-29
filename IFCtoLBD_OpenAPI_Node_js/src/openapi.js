const profiles = [
  "core", "properties-simple", "properties-opm", "evidence", "geometry-envelope",
  "geometry-full", "bim-gis", "compliance", "revision-ready", "materials-bmo",
  "supply-chain", "sustainability",
];

export const validationPacks = Object.freeze([
  "core-bot", "properties-units", "geometry-crs", "digital-twin-sensors",
  "fire-accessibility", "supply-chain-identifiers", "sustainability-declarations",
  "materials-bmo",
  "traceability",
]);

const problem = {
  type: "object",
  required: ["type", "title", "status", "code"],
  properties: {
    type: { type: "string", format: "uri-reference" },
    title: { type: "string" },
    status: { type: "integer" },
    code: { type: "string" },
    detail: { type: "string" },
    instance: { type: "string" },
  },
};

export const openapiDocument = {
  openapi: "3.1.0",
  info: {
    title: "IFCtoLBD_OpenAPI_Node_js",
    version: "2.56.0",
    description: "Convert uploaded IFC models into Linked Building Data RDF. Uploaded files are temporary and are removed after each response.",
    license: { name: "Apache-2.0", url: "https://www.apache.org/licenses/LICENSE-2.0" },
  },
  tags: [
    { name: "Conversion", description: "IFC-to-RDF conversion" },
    { name: "Retrieval", description: "Bounded inspection of converted LBD graphs" },
    { name: "Operations", description: "Health and runtime metadata" },
  ],
  paths: {
    "/hello": {
      get: {
        tags: ["Operations"], summary: "Legacy liveness check", operationId: "hello",
        responses: { "200": { description: "Service is alive", content: { "text/plain": { schema: { type: "string", example: "OK!" } } } } },
      },
    },
    "/health/live": {
      get: {
        tags: ["Operations"], summary: "Process liveness", operationId: "getLiveness",
        responses: { "200": { description: "Process is alive", content: { "application/json": { schema: { $ref: "#/components/schemas/Health" } } } } },
      },
    },
    "/health/ready": {
      get: {
        tags: ["Operations"], summary: "Converter readiness", operationId: "getReadiness",
        responses: {
          "200": { description: "Converter is ready", content: { "application/json": { schema: { $ref: "#/components/schemas/Health" } } } },
          "503": { $ref: "#/components/responses/Problem" },
        },
      },
    },
    "/api/v1/capabilities": {
      get: {
        tags: ["Operations"], summary: "Supported conversion options", operationId: "getCapabilities",
        responses: { "200": { description: "Capabilities", content: { "application/json": { schema: { type: "object", additionalProperties: true } } } } },
      },
    },
    "/api/v1/convert": { post: conversionOperation("convertIfcToLbd") },
    "/api/v1/retrieve": { post: retrievalOperation() },
    "/convertIFCtoLBD": { post: { ...conversionOperation("convertIfcToLbdLegacy"), deprecated: true } },
  },
  components: {
    schemas: {
      Health: {
        type: "object", required: ["status"],
        properties: { status: { type: "string", enum: ["ok", "unavailable"] }, converterVersion: { type: "string" } },
      },
      Problem: problem,
    },
    responses: {
      Problem: { description: "Problem details", content: { "application/problem+json": { schema: { $ref: "#/components/schemas/Problem" } } } },
    },
  },
};

function conversionOperation(operationId) {
  return {
    tags: ["Conversion"],
    summary: "Convert an IFC model to Linked Building Data",
    operationId,
    parameters: [{
      name: "Accept", in: "header", required: false,
      schema: { type: "string", enum: ["text/turtle", "application/ld+json", "application/rdf+xml", "application/trig"] },
      description: "Desired RDF media type. The optional format form field takes precedence.",
    }],
    requestBody: {
      required: true,
      content: {
        "multipart/form-data": {
          schema: {
            type: "object", required: ["ifcFile"], additionalProperties: false,
            properties: {
              ifcFile: { type: "string", format: "binary", description: "IFC STEP, IFC/XML, or IFC/JSON input." },
              profile: { type: "string", enum: profiles, default: "properties-opm" },
              baseUri: { type: "string", format: "uri", default: "https://lbd.example.com/" },
              validate: { type: "boolean", default: false, description: "Apply every bundled standard SHACL pack." },
              validationPacks: { type: "string", description: "Comma-separated SHACL pack IDs. Overrides validate when supplied." },
              propertiesBlankNodes: { type: "boolean", default: false },
              propertyLevel: { type: "integer", minimum: 1, maximum: 3, default: 3 },
              modelScope: { type: "string" },
              identifierPolicy: { type: "string", enum: ["EXISTING", "STABLE_PROJECT"], default: "EXISTING" },
              projectNamespace: { type: "string", format: "uri" },
              format: { type: "string", enum: ["turtle", "jsonld", "rdfxml", "trig"] },
            },
          },
        },
      },
    },
    responses: {
      "200": {
        description: "Converted RDF file",
        headers: { "Content-Disposition": { schema: { type: "string" } } },
        content: {
          "text/turtle": { schema: { type: "string" } },
          "application/ld+json": { schema: {} },
          "application/rdf+xml": { schema: { type: "string" } },
          "application/trig": { schema: { type: "string" } },
        },
      },
      "400": { $ref: "#/components/responses/Problem" },
      "413": { $ref: "#/components/responses/Problem" },
      "422": { $ref: "#/components/responses/Problem" },
      "503": { $ref: "#/components/responses/Problem" },
    },
  };
}

function retrievalOperation() {
  return {
    tags: ["Retrieval"], summary: "Find or inspect entities in converted LBD data", operationId: "retrieveLbd",
    description: "Runs deterministic, read-only retrieval. Companion external-link and provenance graphs are optional and are never fetched.",
    requestBody: { required: true, content: { "multipart/form-data": { schema: {
      type: "object", required: ["dataFile", "task"], additionalProperties: false,
      properties: {
        dataFile: { type: "string", format: "binary", description: "Converted Turtle, N-Triples, or RDF/XML graph." },
        externalLinksFile: { type: "string", format: "binary", description: "Optional separately managed link graph." },
        provenanceFile: { type: "string", format: "binary", description: "Optional provenance companion graph." },
        externalLinkReportFile: { type: "string", format: "binary", description: "Optional mapping report with source and temporal metadata." },
        task: { type: "string", enum: ["find", "inspect", "links"] },
        identifier: { type: "string", description: "Entity IRI or IFC GlobalId." },
        type: { type: "string", description: "Full type IRI or local type name; find only." },
        spatialLocation: { type: "string", description: "Spatial entity IRI or IFC GlobalId; find only." },
        offset: { type: "integer", minimum: 0, maximum: 100000, default: 0 },
        limit: { type: "integer", minimum: 1, maximum: 1000, default: 100 },
      },
    } } } },
    responses: {
      "200": { description: "Structured retrieval result", content: { "application/json": { schema: { type: "object", additionalProperties: true } } } },
      "400": { $ref: "#/components/responses/Problem" }, "413": { $ref: "#/components/responses/Problem" },
      "415": { $ref: "#/components/responses/Problem" }, "422": { $ref: "#/components/responses/Problem" },
    },
  };
}

export { profiles };
