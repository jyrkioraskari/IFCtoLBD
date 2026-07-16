#!/usr/bin/env node

"use strict";

const path = require("path");

let input = "";

process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  input += chunk;
});

function requireResult(responses, id, label) {
  const response = responses.get(id);
  if (!response) {
    throw new Error(`Missing MCP response for ${label}`);
  }
  if (response.error) {
    throw new Error(`${label} failed: ${response.error.message}`);
  }
  return response.result;
}

function parseToolJson(result) {
  return JSON.parse(result.content[0].text);
}

process.stdin.on("end", () => {
  try {
    const responses = new Map(
      input
        .trim()
        .split(/\r?\n/)
        .filter(Boolean)
        .map((line) => {
          const response = JSON.parse(line);
          return [response.id, response];
        })
    );

    const init = requireResult(responses, 1, "initialize");
    const listed = requireResult(responses, 2, "tools/list");
    const summary = parseToolJson(requireResult(responses, 3, "summarize_ifc_lbd"));
    const query = parseToolJson(requireResult(responses, 4, "query_ifc_lbd"));
    const conversion = parseToolJson(requireResult(responses, 5, "convert_ifc_to_lbd"));

    console.log(`Connected to ${init.serverInfo.name} ${init.serverInfo.version}`);
    console.log("Tools:");
    for (const tool of listed.tools) {
      console.log(`- ${tool.name}`);
    }

    console.log(`\nSummary for ${path.basename(summary.ifcPath)}:`);
    console.log(`- triples: ${summary.triples}`);
    console.log(`- subjects: ${summary.subjects}`);
    console.log(`- first subject: ${summary.sampleSubjects[0]}`);

    console.log("\nSPARQL sample rows:");
    for (const row of query.rows) {
      console.log(`- ${row.s.value}`);
    }

    console.log(`\nWrote ${conversion.bytesWritten} bytes to ${conversion.outputPath}`);
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
});
