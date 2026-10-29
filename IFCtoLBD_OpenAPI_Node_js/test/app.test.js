import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { buildApp } from "../src/app.js";

class FakeConverterService {
  calls = [];
  closed = false;

  async converterVersion() { return "test-version"; }

  async convert(request) {
    this.calls.push(request);
    const input = await fs.promises.readFile(request.inputPath, "utf8");
    await fs.promises.writeFile(request.outputPath, `# ${input}\n<urn:test> <urn:converted> true .\n`);
  }

  async retrieve(request) {
    this.calls.push(request);
    return { status: "FOUND", queryKind: "FIND_ELEMENTS", entities: [{ identifier: "urn:wall:1" }], truncated: false };
  }

  async close() { this.closed = true; }
}

async function fixture(t, overrides = {}) {
  const tempRoot = await fs.promises.mkdtemp(path.join(os.tmpdir(), "ifctolbd-api-test-"));
  const service = new FakeConverterService();
  const config = {
    maxUploadBytes: 1024 * 1024,
    maxQueuedConversions: 2,
    tempRoot,
    logLevel: "silent",
    trustProxy: false,
    converterJar: "/unused/test.jar",
    ...overrides,
  };
  const app = await buildApp({ config, converterService: service, logger: false });
  t.after(async () => {
    await app.close();
    await fs.promises.rm(tempRoot, { recursive: true, force: true });
  });
  return { app, service };
}

function multipart(fields = {}, file = { field: "ifcFile", name: "model.ifc", content: "ISO-10303-21;" }) {
  const boundary = "----ifctolbd-test-boundary";
  const chunks = [];
  for (const [name, value] of Object.entries(fields)) {
    chunks.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name=\"${name}\"\r\n\r\n${value}\r\n`));
  }
  for (const item of file ? (Array.isArray(file) ? file : [file]) : []) {
    chunks.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name=\"${item.field}\"; filename=\"${item.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n`));
    chunks.push(Buffer.from(item.content));
    chunks.push(Buffer.from("\r\n"));
  }
  chunks.push(Buffer.from(`--${boundary}--\r\n`));
  return { payload: Buffer.concat(chunks), headers: { "content-type": `multipart/form-data; boundary=${boundary}` } };
}

test("serves compatibility and OpenAPI endpoints", async (t) => {
  const { app } = await fixture(t);
  const hello = await app.inject({ method: "GET", url: "/hello" });
  assert.equal(hello.statusCode, 200);
  assert.equal(hello.body, "OK!");

  const specification = await app.inject({ method: "GET", url: "/openapi.json" });
  assert.equal(specification.statusCode, 200);
  assert.equal(specification.json().openapi, "3.1.0");
  assert.ok(specification.json().paths["/api/v1/convert"]);
  assert.ok(specification.json().paths["/api/v1/retrieve"]);

  const docs = await app.inject({ method: "GET", url: "/docs/" });
  assert.equal(docs.statusCode, 200);
});

test("runs bounded retrieval over RDF companion graphs", async (t) => {
  const { app, service } = await fixture(t);
  const form = multipart({ task: "find", type: "Wall", limit: "5" }, [
    { field: "dataFile", name: "model.ttl", content: "<urn:wall:1> a <https://w3id.org/bot#Element> ." },
    { field: "provenanceFile", name: "provenance.ttl", content: "<urn:wall:1> <urn:source> <urn:model> ." },
  ]);
  const response = await app.inject({ method: "POST", url: "/api/v1/retrieve", headers: form.headers, payload: form.payload });
  assert.equal(response.statusCode, 200);
  assert.equal(response.json().status, "FOUND");
  assert.equal(service.calls[0].task, "find");
  assert.equal(service.calls[0].options.limit, 5);
  assert.ok(service.calls[0].provenancePath.endsWith("provenanceFile.ttl"));
});

test("streams a conversion and passes validated options", async (t) => {
  const { app, service } = await fixture(t);
  const form = multipart({
    profile: "properties-simple",
    baseUri: "https://example.test/building/",
    propertiesBlankNodes: "true",
    propertyLevel: "2",
    validate: "true",
  });
  const response = await app.inject({
    method: "POST", url: "/api/v1/convert", headers: { ...form.headers, accept: "application/ld+json" }, payload: form.payload,
  });

  assert.equal(response.statusCode, 200);
  assert.match(response.headers["content-type"], /^application\/ld\+json/);
  assert.match(response.headers["content-disposition"], /ifc2lbd\.jsonld/);
  assert.match(response.body, /urn:converted/);
  assert.equal(service.calls.length, 1);
  assert.equal(service.calls[0].options.profile, "properties-simple");
  assert.equal(service.calls[0].options.propertiesBlankNodes, true);
  assert.equal(service.calls[0].options.propertyLevel, 2);
  assert.equal(service.calls[0].options.validationPacks.length, 9);
});

test("format field overrides Accept and legacy route remains available", async (t) => {
  const { app } = await fixture(t);
  const form = multipart({ format: "trig" });
  const response = await app.inject({
    method: "POST", url: "/convertIFCtoLBD", headers: { ...form.headers, accept: "text/turtle" }, payload: form.payload,
  });
  assert.equal(response.statusCode, 200);
  assert.match(response.headers["content-type"], /^application\/trig/);
});

test("returns structured client errors", async (t) => {
  const { app, service } = await fixture(t);
  const missing = multipart({}, null);
  const missingResponse = await app.inject({ method: "POST", url: "/api/v1/convert", headers: missing.headers, payload: missing.payload });
  assert.equal(missingResponse.statusCode, 400);
  assert.equal(missingResponse.json().code, "MISSING_FILE");

  const invalid = multipart({ identifierPolicy: "STABLE_PROJECT" });
  const invalidResponse = await app.inject({ method: "POST", url: "/api/v1/convert", headers: invalid.headers, payload: invalid.payload });
  assert.equal(invalidResponse.statusCode, 400);
  assert.equal(invalidResponse.json().code, "INVALID_ARGUMENT");
  assert.equal(service.calls.length, 0);
});

test("reports readiness and capabilities", async (t) => {
  const { app } = await fixture(t);
  const ready = await app.inject({ method: "GET", url: "/health/ready" });
  assert.deepEqual(ready.json(), { status: "ok", converterVersion: "test-version" });

  const capabilities = await app.inject({ method: "GET", url: "/api/v1/capabilities" });
  assert.equal(capabilities.statusCode, 200);
  assert.equal(capabilities.json().converterVersion, "test-version");
  assert.ok(capabilities.json().profiles.includes("properties-opm"));
});
