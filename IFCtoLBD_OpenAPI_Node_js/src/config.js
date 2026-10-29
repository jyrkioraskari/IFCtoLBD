import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const REPOSITORY_ROOT = path.resolve(PROJECT_ROOT, "..");

function integer(name, fallback, minimum, maximum) {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return fallback;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer between ${minimum} and ${maximum}`);
  }
  return value;
}

function findDefaultJavaHome() {
  const candidates = [
    path.join(REPOSITORY_ROOT, ".tools", "jdk"),
    path.join(REPOSITORY_ROOT, "IFCtoLBD_MCP", ".tools", "jdk"),
  ];
  return candidates.find((candidate) => fs.existsSync(candidate));
}

export function loadConfig(overrides = {}) {
  const converterJar = path.resolve(
    process.env.IFCTOLBD_API_CONVERTER_JAR
      || path.join(REPOSITORY_ROOT, "IFCtoLBD_MCP", "lib", "ifctolbd-converter.jar"),
  );

  return {
    host: process.env.HOST || "127.0.0.1",
    port: integer("PORT", 8080, 1, 65535),
    maxUploadBytes: integer("IFCTOLBD_API_MAX_UPLOAD_BYTES", 256 * 1024 * 1024, 1024, 2 * 1024 * 1024 * 1024),
    maxQueuedConversions: integer("IFCTOLBD_API_MAX_QUEUE", 8, 0, 1000),
    converterJar,
    javaHome: process.env.JAVA_HOME || findDefaultJavaHome(),
    tempRoot: path.resolve(process.env.IFCTOLBD_API_TEMP_DIR || os.tmpdir()),
    logLevel: process.env.LOG_LEVEL || "info",
    trustProxy: process.env.IFCTOLBD_API_TRUST_PROXY === "true",
    ...overrides,
  };
}

export { PROJECT_ROOT, REPOSITORY_ROOT };
