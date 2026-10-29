#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { performance } from "node:perf_hooks";

const root = path.dirname(fileURLToPath(import.meta.url));
const value = (name, fallback) => {
  const index = process.argv.indexOf(name);
  return index < 0 ? fallback : process.argv[index + 1];
};
const jar = path.resolve(value("--jar", path.join(root, "../../IFCtoLBD_MCP/lib/ifctolbd-converter.jar")));
const output = value("--output", null);
const data = path.join(root, "data/building.ttl");
const links = path.join(root, "data/external-links.ttl");
const provenance = path.join(root, "data/provenance.ttl");
const linkReport = path.join(root, "data/external-link-report.ttl");
const questions = JSON.parse(fs.readFileSync(path.join(root, "data/questions.json"), "utf8"));
const documents = JSON.parse(fs.readFileSync(path.join(root, "data/documents.json"), "utf8"));

if (!fs.existsSync(jar)) throw new Error(`Converter distribution not found: ${jar}. Run npm --prefix IFCtoLBD_MCP run build:converter.`);

const words = (text) => new Set(String(text).toLowerCase().match(/[a-z0-9]+/g) || []);
function documentOnly(question) {
  const query = words(question.question);
  const ranked = documents.map((document) => ({ document, score: [...query].filter((term) => words(`${document.title} ${document.text}`).has(term)).length }))
    .filter(({ score }) => score >= 2).sort((a, b) => b.score - a.score || a.document.uri.localeCompare(b.document.uri)).slice(0, 2);
  return { status: ranked.length ? "FOUND" : "EMPTY", text: ranked.map(({ document }) => `${document.uri} ${document.text}`).join("\n") };
}

function graphAssisted(question) {
  const args = ["-cp", jar, "org.linkedbuildingdata.ifc2lbd.GraphRetrieval_CLI", "--data", data,
    "--external-links", links, "--provenance", provenance, "--external-link-report", linkReport,
    "--task", question.graph.task, "--limit", "100"];
  for (const [key, item] of Object.entries(question.graph)) {
    if (key === "task") continue;
    args.push(`--${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`, item);
  }
  const run = spawnSync("java", args, { encoding: "utf8", timeout: 30000 });
  // Some confined launchers report EPERM even when the child completed; the exit status is authoritative.
  if (run.status !== 0) throw new Error(run.stderr.trim() || run.error?.message || `Java exited ${run.status}`);
  const result = JSON.parse(run.stdout.trim());
  const serialized = JSON.stringify(result);
  const linkedDocuments = documents.filter((document) => serialized.includes(document.uri));
  return { status: result.status, text: `${serialized}\n${linkedDocuments.map((document) => document.text).join("\n")}` };
}

function assess(question, retrieved) {
  const expectedMatches = question.expected.filter((term) => retrieved.text.toLowerCase().includes(term.toLowerCase())).length;
  const evidenceMatches = question.evidence.filter((term) => retrieved.text.includes(term)).length;
  const unanswerable = question.expected.length === 0;
  return {
    answerCorrectness: unanswerable ? ["EMPTY", "NOT_FOUND"].includes(retrieved.status) : expectedMatches / question.expected.length,
    evidenceCorrectness: question.evidence.length === 0 ? true : evidenceMatches / question.evidence.length,
    coverage: unanswerable ? 1 : expectedMatches / question.expected.length,
    unanswerableHandled: unanswerable ? ["EMPTY", "NOT_FOUND"].includes(retrieved.status) : null,
  };
}

const report = { configuration: { java: "system java", jar, questionCount: questions.length, llm: false, network: false }, approaches: {} };
for (const [name, retrieve] of [["documentOnly", documentOnly], ["graphAssisted", graphAssisted]]) {
  const started = performance.now();
  const results = [];
  for (const question of questions) {
    const questionStarted = performance.now();
    try {
      const retrieved = retrieve(question);
      results.push({ id: question.id, status: retrieved.status, ...assess(question, retrieved), milliseconds: performance.now() - questionStarted });
    } catch (error) {
      results.push({ id: question.id, error: error.message, milliseconds: performance.now() - questionStarted });
    }
  }
  const successful = results.filter((result) => !result.error);
  report.approaches[name] = {
    milliseconds: performance.now() - started,
    failures: results.filter((result) => result.error).length,
    meanCoverage: successful.length ? successful.reduce((sum, result) => sum + Number(result.coverage), 0) / successful.length : 0,
    results,
  };
}

const json = JSON.stringify(report, null, 2);
if (output) fs.writeFileSync(path.resolve(output), `${json}\n`, { mode: 0o600 });
console.log(json);
process.exitCode = Object.values(report.approaches).some((approach) => approach.failures) ? 1 : 0;
