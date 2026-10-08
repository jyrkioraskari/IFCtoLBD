"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

function expectedConverterVersion() {
  const rootPom = fs.readFileSync(path.resolve(__dirname, "../../pom.xml"), "utf8");
  const match = rootPom.match(/<project\b[\s\S]*?<version>([^<]+)<\/version>/);
  assert(match, "The root Maven project version could not be read");
  return match[1].trim();
}

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", chunk => { input += chunk; });
process.stdin.on("end", () => {
  const responses = input.trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
  const runtime = responses.find(response => response.id === 2);
  assert(runtime && !runtime.error, runtime && runtime.error && runtime.error.message);
  const info = JSON.parse(runtime.result.content[0].text);
  assert.strictEqual(info.converterVersion, expectedConverterVersion(),
    "MCP must report the version loaded from the Java distribution manifest");
  assert.match(info.converterJar, /ifctolbd-converter\.jar$/);
  console.log(`MCP loaded IFCtoLBD ${info.converterVersion} from its distribution manifest.`);
});
