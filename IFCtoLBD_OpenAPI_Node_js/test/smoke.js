import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { loadConfig, REPOSITORY_ROOT } from "../src/config.js";
import { ConverterService } from "../src/converter-service.js";
import { OUTPUTS } from "../src/formats.js";

const directory = await fs.promises.mkdtemp(path.join(os.tmpdir(), "ifctolbd-api-smoke-"));
const service = new ConverterService(loadConfig({ tempRoot: directory }));
try {
  for (const [name, output] of [["turtle", OUTPUTS.turtle], ["trig", OUTPUTS.trig]]) {
    const outputPath = path.join(directory, `duplex.${output.extension}`);
    await service.convert({
      inputPath: path.join(REPOSITORY_ROOT, "IFCtoLBD", "src", "main", "resources", "Duplex_A.ifc"),
      outputPath,
      output,
      options: {
        baseUri: "https://example.com/ifctolbd/",
        propertiesBlankNodes: false,
        propertyLevel: 1,
        profile: "core",
        modelScope: undefined,
        validationPacks: [],
        identifierPolicy: "EXISTING",
        projectNamespace: undefined,
      },
    });
    const rdf = await fs.promises.readFile(outputPath, "utf8");
    assert.match(rdf, /Building|bot:/);
    process.stdout.write(`${name} smoke conversion succeeded (${Buffer.byteLength(rdf)} bytes).\n`);
  }
  const retrieval = await service.retrieve({
    dataPath: path.join(REPOSITORY_ROOT, "examples", "graph-assisted-retrieval", "data", "building.ttl"),
    externalLinksPath: path.join(REPOSITORY_ROOT, "examples", "graph-assisted-retrieval", "data", "external-links.ttl"),
    provenancePath: path.join(REPOSITORY_ROOT, "examples", "graph-assisted-retrieval", "data", "provenance.ttl"),
    externalLinkReportPath: path.join(REPOSITORY_ROOT, "examples", "graph-assisted-retrieval", "data", "external-link-report.ttl"),
    task: "inspect",
    options: { identifier: "2O2Fr$t4X7Zf8NOew3FLOH", offset: 0, limit: 100 },
  });
  assert.equal(retrieval.status, "FOUND");
  assert(retrieval.entities[0].materials.some((statement) => statement.object.lexicalForm === "Clay brick"));
  process.stdout.write("focused retrieval smoke succeeded.\n");
} finally {
  await service.close();
  await fs.promises.rm(directory, { recursive: true, force: true });
}
