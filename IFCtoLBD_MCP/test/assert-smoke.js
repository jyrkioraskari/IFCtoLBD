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
  assert(byId.get(3).result.content[0].text.includes('"triples"'));
});
