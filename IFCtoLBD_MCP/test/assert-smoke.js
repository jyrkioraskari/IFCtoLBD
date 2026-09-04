"use strict";

const assert = require("assert");

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  input += chunk;
});

process.stdin.on("end", () => {
  const responses = input
    .trim()
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => JSON.parse(line));

  const byId = new Map(responses.map((response) => [response.id, response]));

  assert.strictEqual(byId.get(1).result.serverInfo.name, "ifctolbd-mcp");
  const toolNames = byId.get(2).result.tools.map((tool) => tool.name);
  assert(toolNames.includes("convert_ifc_to_lbd"));
  assert(toolNames.includes("list_ifc_elements_with_properties"));
  assert(toolNames.includes("list_ifc_elements_with_geometry"));
  assert(toolNames.includes("load_ifc"));
  assert(toolNames.includes("describe_model"));
  assert(toolNames.includes("close_model"));
  assert(toolNames.includes("compare_revisions"));
  assert(byId.get(3).result.content[0].text.includes('"triples"'));
  const firstLoad = byId.get(4).result.structuredContent;
  const secondLoad = byId.get(5).result.structuredContent;
  assert.strictEqual(firstLoad.modelId, secondLoad.modelId);
  assert.strictEqual(firstLoad.cacheHit, false);
  assert.strictEqual(secondLoad.cacheHit, true);
  assert.match(firstLoad.checksum, /^[a-f0-9]{64}$/);
  assert.notStrictEqual(firstLoad.schema, "unknown");
  assert.strictEqual(byId.get(6).result.structuredContent.rows.length, 2);
  assert.strictEqual(byId.get(7).result.contents[0].mimeType, "text/turtle");
  assert(byId.get(7).result.contents[0].text.length > 0);
  assert.match(byId.get(8).error.message, /SERVICE is disabled/);
  assert.match(byId.get(9).error.message, /outside configured roots/);
  assert.strictEqual(byId.get(10).result.structuredContent.dataModified, false);
  assert.strictEqual(typeof byId.get(10).result.structuredContent.conforms, "boolean");
  assert(Array.isArray(byId.get(11).result.structuredContent.violations));
  assert(byId.get(12).result.contents[0].text.includes("ValidationReport"));
  assert(byId.get(13).result.contents[0].text.includes("Conversion"));
  assert.strictEqual(byId.get(14).result.contents[0].mimeType, "text/turtle");
  assert.strictEqual(byId.get(15).result.structuredContent.closed, true);
});
