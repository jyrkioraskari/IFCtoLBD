import assert from "node:assert/strict";
import test from "node:test";
import { selectOutput } from "../src/formats.js";

test("selects the highest-quality supported Accept media type", () => {
  const output = selectOutput(undefined, "application/rdf+xml;q=0.4, application/trig;q=0.9, text/turtle;q=0.2");
  assert.equal(output.mediaType, "application/trig");
  assert.equal(output.dataset, true);
});

test("defaults unsupported Accept headers to Turtle", () => {
  assert.equal(selectOutput(undefined, "application/json").mediaType, "text/turtle");
});

test("rejects an explicit unsupported format", () => {
  assert.throws(() => selectOutput("yaml", "*/*"), /Unsupported RDF format/);
});
