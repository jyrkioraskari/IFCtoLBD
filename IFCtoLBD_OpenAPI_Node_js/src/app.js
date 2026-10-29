import fs from "node:fs";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import Fastify from "fastify";
import multipart from "@fastify/multipart";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";
import { ConverterService } from "./converter-service.js";
import { ApiError } from "./errors.js";
import { selectOutput } from "./formats.js";
import { openapiDocument, profiles, validationPacks } from "./openapi.js";

const FORM_FIELDS = new Set([
  "profile", "baseUri", "validate", "validationPacks", "propertiesBlankNodes",
  "propertyLevel", "modelScope", "identifierPolicy", "projectNamespace", "format",
]);
const RETRIEVAL_FIELDS = new Set([
  "task", "identifier", "type", "spatialLocation", "offset", "limit",
]);

export async function buildApp({ config, converterService, logger } = {}) {
  if (!config) throw new Error("config is required");
  const service = converterService || new ConverterService(config);
  const app = Fastify({
    logger: logger === undefined ? { level: config.logLevel } : logger,
    trustProxy: config.trustProxy,
    bodyLimit: config.maxUploadBytes,
    requestIdHeader: "x-request-id",
  });

  await app.register(multipart, {
    limits: { files: 4, fields: 12, parts: 16, fileSize: config.maxUploadBytes },
    throwFileSizeLimit: true,
  });
  await app.register(swagger, { mode: "static", specification: { document: openapiDocument } });
  await app.register(swaggerUi, {
    routePrefix: "/docs",
    uiConfig: { deepLinking: true, displayRequestDuration: true, tryItOutEnabled: true },
    staticCSP: true,
  });

  app.addHook("onSend", async (_request, reply) => {
    reply.header("X-Content-Type-Options", "nosniff");
    reply.header("Referrer-Policy", "no-referrer");
    reply.header("X-Frame-Options", "DENY");
  });

  app.get("/openapi.json", async (_request, reply) => reply.send(app.swagger()));
  app.get("/hello", async (_request, reply) => reply.type("text/plain").send("OK!"));
  app.get("/health/live", async () => ({ status: "ok" }));
  app.get("/health/ready", async (_request, reply) => {
    try {
      const converterVersion = await service.converterVersion();
      return { status: "ok", converterVersion };
    } catch (error) {
      app.log.warn({ err: error }, "converter readiness check failed");
      return reply.code(503).type("application/problem+json").send(problem(503, "CONVERTER_UNAVAILABLE", "Converter unavailable"));
    }
  });
  app.get("/api/v1/capabilities", async () => ({
    apiVersion: openapiDocument.info.version,
    converterVersion: await service.converterVersion(),
    profiles,
    validationPacks,
    outputMediaTypes: ["text/turtle", "application/ld+json", "application/rdf+xml", "application/trig"],
    limits: { maxUploadBytes: config.maxUploadBytes, maxQueuedConversions: config.maxQueuedConversions },
  }));

  const convert = async (request, reply) => {
    await fs.promises.mkdir(config.tempRoot, { recursive: true });
    const workDirectory = await fs.promises.mkdtemp(path.join(config.tempRoot, "ifctolbd-api-"));
    let cleanupStarted = false;
    let conversionSettled = false;
    let responseEnded = false;
    const cleanup = async () => {
      if (cleanupStarted) return;
      cleanupStarted = true;
      await fs.promises.rm(workDirectory, { recursive: true, force: true });
    };
    const responseEnd = () => {
      responseEnded = true;
      if (conversionSettled) void cleanup().catch((error) => request.log.warn({ err: error }, "temporary-file cleanup failed"));
    };
    reply.raw.once("finish", responseEnd);
    reply.raw.once("close", responseEnd);

    try {
      const upload = await receiveUpload(request, workDirectory);
      const options = parseOptions(upload.fields);
      const output = selectOutput(upload.fields.format, request.headers.accept);
      const outputPath = path.join(workDirectory, `ifc2lbd.${output.extension}`);

      try {
        await service.convert({ inputPath: upload.inputPath, outputPath, output, options });
      } catch (error) {
        if (error instanceof ApiError) throw error;
        request.log.error({ err: error }, "IFC conversion failed");
        throw new ApiError(422, "CONVERSION_FAILED", "The uploaded IFC model could not be converted.");
      }

      conversionSettled = true;
      if (responseEnded) {
        await cleanup();
        return reply;
      }
      return reply
        .type(output.mediaType)
        .header("Content-Disposition", `attachment; filename=\"ifc2lbd.${output.extension}\"`)
        .header("Cache-Control", "no-store")
        .send(fs.createReadStream(outputPath));
    } catch (error) {
      conversionSettled = true;
      await cleanup();
      throw error;
    }
  };

  app.post("/api/v1/convert", convert);
  app.post("/convertIFCtoLBD", convert);

  app.post("/api/v1/retrieve", async (request, reply) => {
    await fs.promises.mkdir(config.tempRoot, { recursive: true });
    const workDirectory = await fs.promises.mkdtemp(path.join(config.tempRoot, "ifctolbd-retrieve-"));
    try {
      const upload = await receiveRetrievalUpload(request, workDirectory);
      const retrieval = parseRetrievalOptions(upload.fields);
      const result = await service.retrieve({ ...upload, ...retrieval });
      return reply.header("Cache-Control", "no-store").send(result);
    } catch (error) {
      if (error instanceof ApiError || error instanceof TypeError) throw error;
      request.log.error({ err: error }, "LBD retrieval failed");
      throw new ApiError(422, "RETRIEVAL_FAILED", "The uploaded RDF graphs could not be queried.");
    } finally {
      await fs.promises.rm(workDirectory, { recursive: true, force: true });
    }
  });

  app.setNotFoundHandler((request, reply) => {
    reply.code(404).type("application/problem+json").send(problem(404, "NOT_FOUND", "Route not found", request.url));
  });
  app.setErrorHandler((error, request, reply) => {
    if (reply.sent) return;
    const statusCode = error instanceof ApiError
      ? error.statusCode
      : Number.isInteger(error.statusCode) && error.statusCode >= 400 && error.statusCode < 500
        ? error.statusCode
        : error instanceof TypeError ? 400 : 500;
    const code = error instanceof ApiError
      ? error.code
      : error.code === "FST_REQ_FILE_TOO_LARGE" ? "UPLOAD_TOO_LARGE"
        : error.code || (statusCode === 400 ? "INVALID_ARGUMENT" : "INTERNAL_ERROR");
    const safeMessage = statusCode < 500 ? error.message : "An unexpected error occurred.";
    if (statusCode >= 500) request.log.error({ err: error }, "request failed");
    reply.code(statusCode).type("application/problem+json").send(problem(statusCode, code, safeMessage, request.url));
  });
  app.addHook("onClose", async () => service.close());

  return app;
}

async function receiveUpload(request, workDirectory) {
  if (!request.isMultipart()) throw new ApiError(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be multipart/form-data.");
  const fields = {};
  let inputPath;

  for await (const part of request.parts()) {
    if (part.type === "file") {
      if (part.fieldname !== "ifcFile") {
        part.file.resume();
        throw new ApiError(400, "INVALID_FILE_FIELD", "The file field must be named ifcFile.");
      }
      if (inputPath) {
        part.file.resume();
        throw new ApiError(400, "MULTIPLE_FILES", "Exactly one IFC file is allowed.");
      }
      const extension = safeIfcExtension(part.filename);
      inputPath = path.join(workDirectory, `upload${extension}`);
      await pipeline(part.file, fs.createWriteStream(inputPath, { flags: "wx", mode: 0o600 }));
      if (part.file.truncated) throw new ApiError(413, "UPLOAD_TOO_LARGE", "The uploaded IFC file exceeds the configured size limit.");
      continue;
    }

    if (!FORM_FIELDS.has(part.fieldname)) {
      throw new ApiError(400, "UNKNOWN_FIELD", `Unknown multipart field: ${part.fieldname}`);
    }
    if (Object.hasOwn(fields, part.fieldname)) {
      throw new ApiError(400, "DUPLICATE_FIELD", `Multipart field was supplied more than once: ${part.fieldname}`);
    }
    fields[part.fieldname] = part.value;
  }

  if (!inputPath) throw new ApiError(400, "MISSING_FILE", "Missing multipart field: ifcFile");
  return { fields, inputPath };
}

async function receiveRetrievalUpload(request, workDirectory) {
  if (!request.isMultipart()) throw new ApiError(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be multipart/form-data.");
  const fields = {};
  const paths = {};
  const allowedFiles = new Map([
    ["dataFile", "dataPath"], ["externalLinksFile", "externalLinksPath"], ["provenanceFile", "provenancePath"],
    ["externalLinkReportFile", "externalLinkReportPath"],
  ]);
  for await (const part of request.parts()) {
    if (part.type === "file") {
      const key = allowedFiles.get(part.fieldname);
      if (!key) {
        part.file.resume();
        throw new ApiError(400, "INVALID_FILE_FIELD", "RDF file fields are dataFile, externalLinksFile, provenanceFile, and externalLinkReportFile.");
      }
      if (paths[key]) {
        part.file.resume();
        throw new ApiError(400, "MULTIPLE_FILES", `File field was supplied more than once: ${part.fieldname}`);
      }
      const extension = safeRdfExtension(part.filename);
      paths[key] = path.join(workDirectory, `${part.fieldname}${extension}`);
      await pipeline(part.file, fs.createWriteStream(paths[key], { flags: "wx", mode: 0o600 }));
      if (part.file.truncated) throw new ApiError(413, "UPLOAD_TOO_LARGE", "An uploaded RDF file exceeds the configured size limit.");
      continue;
    }
    if (!RETRIEVAL_FIELDS.has(part.fieldname)) {
      throw new ApiError(400, "UNKNOWN_FIELD", `Unknown multipart field: ${part.fieldname}`);
    }
    if (Object.hasOwn(fields, part.fieldname)) {
      throw new ApiError(400, "DUPLICATE_FIELD", `Multipart field was supplied more than once: ${part.fieldname}`);
    }
    fields[part.fieldname] = part.value;
  }
  if (!paths.dataPath) throw new ApiError(400, "MISSING_FILE", "Missing multipart field: dataFile");
  return { fields, ...paths };
}

function parseRetrievalOptions(fields) {
  const task = text(fields.task, "find").toLowerCase();
  if (!["find", "inspect", "links"].includes(task)) throw new TypeError("task must be find, inspect, or links");
  const options = {
    identifier: optionalText(fields.identifier),
    type: optionalText(fields.type),
    spatialLocation: optionalText(fields.spatialLocation),
    offset: numberInRange(fields.offset, 0, 0, 100000, "offset"),
    limit: numberInRange(fields.limit, 100, 1, 1000, "limit"),
  };
  if (task === "find" && !options.identifier && !options.type && !options.spatialLocation) {
    throw new TypeError("find requires identifier, type, or spatialLocation");
  }
  if ((task === "inspect" || task === "links") && !options.identifier) {
    throw new TypeError(`${task} requires identifier`);
  }
  return { task, options };
}

function parseOptions(fields) {
  const profile = text(fields.profile, "properties-opm").toLowerCase();
  if (!profiles.includes(profile)) throw new TypeError(`Unknown conversion profile: ${profile}`);

  const baseUri = text(fields.baseUri, "https://lbd.example.com/");
  let baseUrl;
  try { baseUrl = new URL(baseUri); } catch { throw new TypeError("baseUri must be an absolute HTTP(S) URI"); }
  if (!["http:", "https:"].includes(baseUrl.protocol)) throw new TypeError("baseUri must be an absolute HTTP(S) URI");

  const propertyLevel = numberInRange(fields.propertyLevel, 3, 1, 3, "propertyLevel");
  const identifierPolicy = text(fields.identifierPolicy, "EXISTING").toUpperCase();
  if (!["EXISTING", "STABLE_PROJECT"].includes(identifierPolicy)) {
    throw new TypeError("identifierPolicy must be EXISTING or STABLE_PROJECT");
  }
  const projectNamespace = optionalText(fields.projectNamespace);
  if (identifierPolicy === "STABLE_PROJECT" && !projectNamespace) {
    throw new TypeError("projectNamespace is required for STABLE_PROJECT identifierPolicy");
  }
  if (identifierPolicy === "EXISTING" && projectNamespace) {
    throw new TypeError("projectNamespace requires STABLE_PROJECT identifierPolicy");
  }

  let packs;
  if (fields.validationPacks !== undefined) {
    packs = String(fields.validationPacks).split(",").map((value) => value.trim()).filter(Boolean);
    const unknown = packs.find((pack) => !validationPacks.includes(pack));
    if (unknown) throw new TypeError(`Unknown validation pack: ${unknown}`);
  } else {
    packs = boolean(fields.validate, false, "validate") ? [...validationPacks] : [];
  }

  return {
    profile,
    baseUri: baseUrl.toString(),
    propertiesBlankNodes: boolean(fields.propertiesBlankNodes, false, "propertiesBlankNodes"),
    propertyLevel,
    modelScope: optionalText(fields.modelScope),
    identifierPolicy,
    projectNamespace,
    validationPacks: packs,
  };
}

function safeIfcExtension(filename) {
  const extension = path.extname(String(filename || "")).toLowerCase();
  return [".ifc", ".ifcxml", ".ifcjson", ".xml", ".json"].includes(extension) ? extension : ".ifc";
}

function safeRdfExtension(filename) {
  const extension = path.extname(String(filename || "")).toLowerCase();
  if (![".ttl", ".nt", ".rdf", ".xml"].includes(extension)) {
    throw new TypeError("RDF uploads must use .ttl, .nt, .rdf, or .xml");
  }
  return extension;
}

function text(value, fallback) {
  const result = value === undefined ? fallback : String(value).trim();
  if (!result) throw new TypeError("Multipart option values must not be empty");
  return result;
}

function optionalText(value) {
  if (value === undefined || value === null || String(value).trim() === "") return undefined;
  return String(value).trim();
}

function boolean(value, fallback, name) {
  if (value === undefined) return fallback;
  if (value === true || String(value).toLowerCase() === "true") return true;
  if (value === false || String(value).toLowerCase() === "false") return false;
  throw new TypeError(`${name} must be true or false`);
}

function numberInRange(value, fallback, minimum, maximum, name) {
  if (value === undefined) return fallback;
  const result = Number(value);
  if (!Number.isInteger(result) || result < minimum || result > maximum) {
    throw new TypeError(`${name} must be an integer between ${minimum} and ${maximum}`);
  }
  return result;
}

function problem(status, code, detail, instance) {
  return {
    type: "about:blank",
    title: code.split("_").map((word) => word[0] + word.slice(1).toLowerCase()).join(" "),
    status,
    code,
    detail,
    ...(instance ? { instance } : {}),
  };
}

export { parseOptions, parseRetrievalOptions, receiveUpload, receiveRetrievalUpload };
