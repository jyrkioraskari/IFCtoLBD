import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { QueueFullError } from "./errors.js";

const require = createRequire(import.meta.url);

function findJvmLibrary(javaHome) {
  if (!javaHome) return undefined;
  const candidates = {
    win32: [
      path.join(javaHome, "bin", "server", "jvm.dll"),
      path.join(javaHome, "jre", "bin", "server", "jvm.dll"),
    ],
    darwin: [
      path.join(javaHome, "lib", "server", "libjvm.dylib"),
      path.join(javaHome, "jre", "lib", "server", "libjvm.dylib"),
    ],
    linux: [
      path.join(javaHome, "lib", "server", "libjvm.so"),
      path.join(javaHome, "jre", "lib", "server", "libjvm.so"),
    ],
  }[process.platform] || [];
  return candidates.find((candidate) => fs.existsSync(candidate));
}

export class ConverterService {
  #config;
  #runtime;
  #tail = Promise.resolve();
  #queued = 0;

  constructor(config) {
    this.#config = config;
  }

  get queueDepth() {
    return this.#queued;
  }

  async converterVersion() {
    const { Converter } = this.#initializeRuntime();
    return String(await Converter.converterVersion());
  }

  async convert({ inputPath, outputPath, output, options }) {
    return this.#exclusive(() => this.#convertNow({ inputPath, outputPath, output, options }));
  }

  async retrieve({ dataPath, externalLinksPath, provenancePath, externalLinkReportPath, task, options }) {
    return this.#exclusive(() => this.#retrieveNow({ dataPath, externalLinksPath, provenancePath, externalLinkReportPath, task, options }));
  }

  async close() {
    await this.#tail;
  }

  async #exclusive(operation) {
    if (this.#queued >= this.#config.maxQueuedConversions + 1) throw new QueueFullError();

    this.#queued += 1;
    const previous = this.#tail;
    let release;
    this.#tail = new Promise((resolve) => { release = resolve; });
    await previous;

    try {
      return await operation();
    } finally {
      this.#queued -= 1;
      release();
    }
  }

  #initializeRuntime() {
    if (this.#runtime) return this.#runtime;
    if (!fs.existsSync(this.#config.converterJar) || !fs.statSync(this.#config.converterJar).isFile()) {
      throw new Error(`Converter distribution is missing. Run npm run build:converter.`);
    }
    fs.mkdirSync(this.#config.tempRoot, { recursive: true });

    const bridge = require("java-bridge");
    const libPath = findJvmLibrary(this.#config.javaHome);
    bridge.ensureJvm({
      ...(libPath ? { libPath } : {}),
      opts: [`-Djava.io.tmpdir=${path.resolve(this.#config.tempRoot)}${path.sep}`],
    });
    bridge.appendClasspath(this.#config.converterJar);

    this.#runtime = {
      Converter: bridge.importClass("org.linkedbuildingdata.ifc2lbd.McpConversionBridge"),
      FileOutputStream: bridge.importClass("java.io.FileOutputStream"),
      RDFDataMgr: bridge.importClass("org.apache.jena.riot.RDFDataMgr"),
      RDFFormat: bridge.importClass("org.apache.jena.riot.RDFFormat"),
      ModelFactory: bridge.importClass("org.apache.jena.rdf.model.ModelFactory"),
    };
    return this.#runtime;
  }

  async #convertNow({ inputPath, outputPath, output, options }) {
    const { Converter, FileOutputStream, RDFDataMgr, RDFFormat } = this.#initializeRuntime();
    const converter = new Converter(options.baseUri, options.propertiesBlankNodes, options.propertyLevel, "", "");
    let result;
    let outputStream;

    try {
      result = await converter.convert(
        path.resolve(inputPath),
        options.profile,
        options.modelScope || null,
        options.validationPacks,
        options.identifierPolicy,
        options.projectNamespace || null,
        [],
        ["OBJ"],
      );

      outputStream = new FileOutputStream(path.resolve(outputPath));
      if (output.dataset) {
        const dataset = await result.getDataset();
        await RDFDataMgr.write(outputStream, dataset, RDFFormat.TRIG_PRETTY);
      } else {
        const model = await converter.dataModel(result);
        await model.write(outputStream, output.jenaFormat);
      }
      await outputStream.close();
      outputStream = undefined;
      return { converterVersion: String(await Converter.converterVersion()) };
    } finally {
      if (outputStream) {
        try { await outputStream.close(); } catch { /* retain the conversion error */ }
      }
      if (result) {
        try { await result.close(); } catch { /* retain the conversion error */ }
      }
      try { await converter.close(); } catch { /* retain the conversion error */ }
    }
  }

  async #retrieveNow({ dataPath, externalLinksPath, provenancePath, externalLinkReportPath, task, options }) {
    const { Converter, RDFDataMgr, ModelFactory } = this.#initializeRuntime();
    const converter = new Converter("https://lbd.example.com/", false, 1, "", "");
    const models = [];
    try {
      const data = await RDFDataMgr.loadModel(path.resolve(dataPath)); models.push(data);
      const externalLinks = externalLinksPath
        ? await RDFDataMgr.loadModel(path.resolve(externalLinksPath)) : await ModelFactory.createDefaultModel();
      models.push(externalLinks);
      const provenance = provenancePath
        ? await RDFDataMgr.loadModel(path.resolve(provenancePath)) : await ModelFactory.createDefaultModel();
      models.push(provenance);
      const linkReport = externalLinkReportPath
        ? await RDFDataMgr.loadModel(path.resolve(externalLinkReportPath)) : await ModelFactory.createDefaultModel();
      models.push(linkReport);
      let json;
      if (task === "find") json = await converter.findElementsWithReportJson(data, externalLinks, provenance, linkReport,
        options.identifier || null, options.type || null, options.spatialLocation || null, options.offset, options.limit);
      else if (task === "inspect") json = await converter.inspectElementWithReportJson(data, externalLinks, provenance, linkReport,
        options.identifier, options.limit);
      else if (task === "links") json = await converter.linkedRecordsWithReportJson(data, externalLinks, provenance, linkReport,
        options.identifier, options.offset, options.limit);
      else throw new TypeError(`Unsupported retrieval task: ${task}`);
      return JSON.parse(String(json));
    } finally {
      for (const model of models.reverse()) {
        try { await model.close(); } catch { /* retain the retrieval error */ }
      }
      try { await converter.close(); } catch { /* retain the retrieval error */ }
    }
  }
}

export { findJvmLibrary };
