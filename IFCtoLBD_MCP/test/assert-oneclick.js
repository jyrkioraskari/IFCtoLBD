"use strict";

const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const path = require("node:path");

const cli = path.resolve(__dirname, "../../OneClick/cli.mjs");

function cliOutput(ifcPath, selection) {
  return execFileSync(process.execPath,
    [cli, "--ifc", ifcPath, "--selection", selection, "--stdout"],
    { encoding: "utf8", timeout: 150000, maxBuffer: 100 * 1024 * 1024 });
}

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => { input += chunk; });
process.stdin.on("end", () => {
  const byId = new Map(input.trim().split(/\r?\n/).filter(Boolean)
    .map((line) => JSON.parse(line)).map((response) => [response.id, response]));
  const names = byId.get(1).result.tools.map((tool) => tool.name);
  assert(names.includes("convert_ifc_to_lbd"));
  assert(names.includes("export_oneclick_lbd"));
  const tool = byId.get(1).result.tools.find((item) => item.name === "export_oneclick_lbd");
  const options = execFileSync(process.execPath, [cli, "--help"], { encoding: "utf8" })
    .match(/^Options: (.+)$/m)[1].split(", ");
  assert.deepEqual(tool.inputSchema.properties.selection.enum, options);

  for (const [id, selection] of [[2, "floorplan"], [3, "spaces"], [4, "products:building"]]) {
    const response = byId.get(id).result;
    assert.notEqual(response.isError, true, response.content?.[0]?.text);
    const result = response.structuredContent;
    assert.equal(result.selection, selection);
    assert.equal(result.format, "TURTLE");
    assert.equal(result.truncated, false);
    assert.equal(result.rdf, cliOutput(result.ifcPath, selection));
    assert.equal(result.length, result.rdf.length);
  }
  assert.match(byId.get(2).result.structuredContent.rdf, /bot:Storey/);
  assert.match(byId.get(3).result.structuredContent.rdf, /0\.2085 -17\.5915 0/);
  assert.match(byId.get(3).result.structuredContent.rdf, /6\.418 -6\.25 5\.719/);
  assert.match(byId.get(4).result.structuredContent.rdf, /oc:hasAttributes/);
  assert.match(byId.get(4).result.structuredContent.rdf, /oc:hasPropertySet/);
});
