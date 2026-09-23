"use strict";

const assert = require("assert");

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => { input += chunk; });
process.stdin.on("end", () => {
  const responses = input.trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
  const byId = new Map(responses.map((response) => [response.id, response]));
  const loaded = byId.get(2).result.structuredContent;
  assert.strictEqual(loaded.profile.profile, "evidence");
  assert.strictEqual(loaded.profile.propsLevel, 2);

  const result = byId.get(3).result;
  assert(!result.isError, result.content && result.content[0].text);
  const evidence = result.structuredContent;
  assert(["found", "ambiguous", "incomplete"].includes(evidence.status), evidence.status);
  assert(evidence.claims.length > 0);
  assert.strictEqual(evidence.subject.ifcGuid.value, "1hOSvn6df7F8_7GcBWlS8Z");
  assert.strictEqual(evidence.claims[0].propertyName.trust, "untrusted_model_content");
  assert.strictEqual(evidence.claims[0].source.element.stepEntity, "#16009");
  assert.match(evidence.claims[0].original.ifcDatatype, /IfcLengthMeasure$/);
  assert.strictEqual(evidence.claims[0].unitResolution.method, "IFC_PROJECT_UNIT");
  assert.strictEqual(evidence.claims[0].unitResolution.resolvedUnit, "http://qudt.org/vocab/unit/M");
  assert.strictEqual(evidence.claims[0].normalized.unit, "http://qudt.org/vocab/unit/M");
  assert.strictEqual(evidence.contentPolicy.treatAsInstructions, false);
  assert.strictEqual(evidence.conversion.profile, "evidence");
  assert.strictEqual(byId.get(4).result.structuredContent.closed, true);
  console.log("Evidence integration passed.");
});
