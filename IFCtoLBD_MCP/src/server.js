#!/usr/bin/env node

"use strict";

const fs = require("fs");
const { fork } = require("child_process");
const os = require("os");
const path = require("path");
const readline = require("readline");

const SERVER_NAME = "ifctolbd-mcp";
const SERVER_VERSION = "0.1.0";
const REPO_ROOT = path.resolve(__dirname, "..", "..");
const DEFAULT_IFC_FILE = path.join(
  REPO_ROOT,
  "IFCtoLBD",
  "src",
  "main",
  "resources",
  "Duplex_A.ifc"
);
const DEFAULT_JAVA_HOME = path.join(REPO_ROOT, ".tools", "jdk");
const DEFAULT_JAR_DIR = path.join(REPO_ROOT, "IFCtoLBD_NodeJS", "java_libraries");
const DEFAULT_BASE_URI = "https://example.com/ifctolbd/";
const DEFAULT_FORMAT = "TURTLE";
const MAX_INLINE_CHARS = 200000;

let javaBridge = null;
let jvmReady = false;
let classpathReady = false;
let converterClass = null;
let byteArrayOutputStreamClass = null;

const RDF_FORMATS = new Set([
  "TURTLE",
  "TTL",
  "RDF/XML",
  "RDFXML",
  "N-TRIPLE",
  "N-TRIPLES",
  "NT",
  "JSON-LD",
  "JSONLD",
  "N3",
]);

const tools = [
  {
    name: "convert_ifc_to_lbd",
    description:
      "Convert an IFC STEP file to Linked Building Data RDF. Returns serialized RDF inline or writes it to outputPath.",
    inputSchema: {
      type: "object",
      properties: {
        ifcPath: {
          type: "string",
          description:
            "Path to an IFC file. Defaults to the repository sample Duplex_A.ifc.",
        },
        outputPath: {
          type: "string",
          description:
            "Optional path for the serialized RDF output. Parent directory must already exist.",
        },
        baseUri: {
          type: "string",
          description: "Base URI used for generated RDF resources.",
          default: DEFAULT_BASE_URI,
        },
        format: {
          type: "string",
          description:
            "Apache Jena serialization format such as TURTLE, RDF/XML, N-TRIPLE, JSON-LD, or N3.",
          default: DEFAULT_FORMAT,
        },
        propertiesBlankNodes: {
          type: "boolean",
          description: "Use blank nodes for property values.",
          default: true,
        },
        propsLevel: {
          type: "integer",
          description: "LBD property level passed to IFCtoLBD.",
          minimum: 1,
          maximum: 3,
          default: 1,
        },
        maxInlineChars: {
          type: "integer",
          description:
            "Maximum number of serialized RDF characters returned inline when outputPath is omitted.",
          minimum: 1000,
          default: MAX_INLINE_CHARS,
        },
      },
      additionalProperties: false,
    },
  },
  {
    name: "summarize_ifc_lbd",
    description:
      "Convert an IFC file to an RDF model and return a compact summary: triple count, subject count, and sample subjects.",
    inputSchema: {
      type: "object",
      properties: {
        ifcPath: {
          type: "string",
          description:
            "Path to an IFC file. Defaults to the repository sample Duplex_A.ifc.",
        },
        baseUri: {
          type: "string",
          description: "Base URI used for generated RDF resources.",
          default: DEFAULT_BASE_URI,
        },
        propertiesBlankNodes: {
          type: "boolean",
          description: "Use blank nodes for property values.",
          default: true,
        },
        propsLevel: {
          type: "integer",
          description: "LBD property level passed to IFCtoLBD.",
          minimum: 1,
          maximum: 3,
          default: 1,
        },
        sampleSize: {
          type: "integer",
          description: "Number of subject IRIs returned in the sample.",
          minimum: 0,
          maximum: 100,
          default: 20,
        },
      },
      additionalProperties: false,
    },
  },
  {
    name: "query_ifc_lbd",
    description:
      "Convert an IFC file to LBD RDF and execute a SPARQL SELECT query against the in-memory Jena model.",
    inputSchema: {
      type: "object",
      required: ["sparql"],
      properties: {
        ifcPath: {
          type: "string",
          description:
            "Path to an IFC file. Defaults to the repository sample Duplex_A.ifc.",
        },
        sparql: {
          type: "string",
          description: "SPARQL SELECT query to run against the converted model.",
        },
        baseUri: {
          type: "string",
          description: "Base URI used for generated RDF resources.",
          default: DEFAULT_BASE_URI,
        },
        propertiesBlankNodes: {
          type: "boolean",
          description: "Use blank nodes for property values.",
          default: true,
        },
        propsLevel: {
          type: "integer",
          description: "LBD property level passed to IFCtoLBD.",
          minimum: 1,
          maximum: 3,
          default: 1,
        },
        limit: {
          type: "integer",
          description: "Maximum number of result rows returned.",
          minimum: 1,
          maximum: 1000,
          default: 100,
        },
      },
      additionalProperties: false,
    },
  },
  {
    name: "ifctolbd_runtime_info",
    description:
      "Return paths and runtime settings used by this MCP server and the IFCtoLBD Java bridge.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
];

function send(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function result(id, payload) {
  send({ jsonrpc: "2.0", id, result: payload });
}

function error(id, code, message, data) {
  const payload = { jsonrpc: "2.0", id, error: { code, message } };
  if (data !== undefined) {
    payload.error.data = data;
  }
  send(payload);
}

function textContent(text) {
  return { content: [{ type: "text", text }] };
}

function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function asBoolean(value, defaultValue) {
  return typeof value === "boolean" ? value : defaultValue;
}

function asInteger(value, defaultValue, min, max) {
  if (value === undefined || value === null) {
    return defaultValue;
  }
  if (!Number.isInteger(value)) {
    throw new Error(`Expected integer, received ${JSON.stringify(value)}`);
  }
  if (value < min || value > max) {
    throw new Error(`Expected integer between ${min} and ${max}, received ${value}`);
  }
  return value;
}

function defaultTmpDir() {
  const directory = path.join(os.tmpdir(), `ifctolbd-mcp-${process.pid}`);
  fs.mkdirSync(directory, { recursive: true });
  return directory;
}

function withTrailingSeparator(directory) {
  return directory.endsWith(path.sep) ? directory : directory + path.sep;
}

function findJvmLibrary(javaHome) {
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

function findJars(jarDir = DEFAULT_JAR_DIR) {
  return fs
    .readdirSync(jarDir)
    .filter((fileName) => fileName.toLowerCase().endsWith(".jar"))
    .map((fileName) => path.join(jarDir, fileName));
}

function javaBridgeResolvePaths() {
  return [
    path.resolve(__dirname, ".."),
    path.resolve(REPO_ROOT, "IFCtoLBD_NodeJS"),
  ];
}

function loadJavaBridge() {
  if (javaBridge) {
    return javaBridge;
  }

  try {
    const modulePath = require.resolve("java-bridge", {
      paths: javaBridgeResolvePaths(),
    });
    javaBridge = require(modulePath);
    return javaBridge;
  } catch (err) {
    throw new Error(
      [
        "Unable to load java-bridge for this platform.",
        "Run `npm install` inside IFCtoLBD_MCP on the target machine so npm installs the native optional package, for example `java-bridge-win32-x64-msvc` on Windows.",
        `Original error: ${err.message}`,
      ].join(" ")
    );
  }
}

function initializeJvm(options = {}) {
  if (jvmReady) {
    return;
  }

  const bridge = loadJavaBridge();
  const javaHome = options.javaHome || process.env.JAVA_HOME || DEFAULT_JAVA_HOME;
  const tmpDir = options.tmpDir || process.env.TMPDIR || defaultTmpDir();
  const libPath = options.libPath || findJvmLibrary(javaHome);
  const opts = [
    `-Djava.io.tmpdir=${withTrailingSeparator(tmpDir)}`,
    ...(options.jvmOptions || []),
  ];

  bridge.ensureJvm({
    ...(libPath ? { libPath } : {}),
    opts,
  });
  jvmReady = true;
}

function initializeClasspath(options = {}) {
  if (classpathReady) {
    return;
  }

  initializeJvm(options);
  const bridge = loadJavaBridge();
  bridge.appendClasspath(findJars(options.jarDir));
  classpathReady = true;
}

function importJavaClass(className) {
  initializeClasspath();
  return loadJavaBridge().importClass(className);
}

function getConverterClass() {
  initializeClasspath();
  if (!converterClass) {
    converterClass = importJavaClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter");
  }
  return converterClass;
}

function getByteArrayOutputStreamClass() {
  initializeClasspath();
  if (!byteArrayOutputStreamClass) {
    byteArrayOutputStreamClass = importJavaClass("java.io.ByteArrayOutputStream");
  }
  return byteArrayOutputStreamClass;
}

class IFCtoLBD {
  constructor(options = {}) {
    this.options = {
      baseUri: DEFAULT_BASE_URI,
      propertiesBlankNodes: true,
      levels: [1],
      ...options,
    };

    const Converter = getConverterClass();
    this.converter = new Converter(
      this.options.baseUri,
      this.options.propertiesBlankNodes,
      this.options.levels
    );
  }

  convert(ifcFile = DEFAULT_IFC_FILE) {
    return this.converter.convertSync(path.resolve(ifcFile));
  }

  listSubjects(model) {
    const subjects = model.listSubjectsSync().toListSync();

    if (Array.isArray(subjects)) {
      return subjects;
    }

    if (
      subjects &&
      typeof subjects.sizeSync === "function" &&
      typeof subjects.getSync === "function"
    ) {
      const size = subjects.sizeSync();
      const values = [];

      for (let index = 0; index < size; index += 1) {
        values.push(String(subjects.getSync(index)));
      }

      return values;
    }

    return [String(subjects)];
  }

  serializeModel(model, format = DEFAULT_FORMAT) {
    const ByteArrayOutputStream = getByteArrayOutputStreamClass();
    const outputStream = new ByteArrayOutputStream();

    model.writeSync(outputStream, format);

    return outputStream.toStringSync("UTF-8");
  }
}

function normalizeFormat(format) {
  const normalized = String(format || DEFAULT_FORMAT).trim().toUpperCase();
  if (!RDF_FORMATS.has(normalized)) {
    throw new Error(`Unsupported RDF format: ${format}`);
  }
  if (normalized === "TTL") {
    return "TURTLE";
  }
  if (normalized === "RDFXML") {
    return "RDF/XML";
  }
  if (normalized === "N-TRIPLES" || normalized === "NT") {
    return "N-TRIPLE";
  }
  if (normalized === "JSONLD") {
    return "JSON-LD";
  }
  return normalized;
}

function resolveIfcPath(ifcPath) {
  const candidate = path.resolve(ifcPath || DEFAULT_IFC_FILE);
  if (!fs.existsSync(candidate)) {
    throw new Error(`IFC file does not exist: ${candidate}`);
  }
  if (!fs.statSync(candidate).isFile()) {
    throw new Error(`IFC path is not a file: ${candidate}`);
  }
  return candidate;
}

function resolveOutputPath(outputPath) {
  if (!outputPath) {
    return undefined;
  }
  const candidate = path.resolve(outputPath);
  const parent = path.dirname(candidate);
  if (!fs.existsSync(parent) || !fs.statSync(parent).isDirectory()) {
    throw new Error(`Output directory does not exist: ${parent}`);
  }
  return candidate;
}

function createConverter(args) {
  const baseUri = args.baseUri || DEFAULT_BASE_URI;
  const propsLevel = asInteger(args.propsLevel, 1, 1, 3);
  const propertiesBlankNodes = asBoolean(args.propertiesBlankNodes, true);

  return new IFCtoLBD({
    baseUri,
    propertiesBlankNodes,
    levels: [propsLevel],
  });
}

function convertToModel(args) {
  const ifcPath = resolveIfcPath(args.ifcPath);
  const converter = createConverter(args);
  return {
    ifcPath,
    converter,
    model: converter.convert(ifcPath),
  };
}

function modelSize(model) {
  const size = model.sizeSync();
  return typeof size === "number" ? size : Number(size);
}

function callConvert(args) {
  const format = normalizeFormat(args.format);
  const outputPath = resolveOutputPath(args.outputPath);
  const maxInlineChars = asInteger(
    args.maxInlineChars,
    MAX_INLINE_CHARS,
    1000,
    50 * 1024 * 1024
  );
  const { ifcPath, converter, model } = convertToModel(args);
  const serialized = converter.serializeModel(model, format);
  const triples = modelSize(model);

  if (outputPath) {
    fs.writeFileSync(outputPath, serialized, "utf8");
    return textContent(
      JSON.stringify(
        {
          ifcPath,
          outputPath,
          format,
          triples,
          bytesWritten: Buffer.byteLength(serialized, "utf8"),
        },
        null,
        2
      )
    );
  }

  const truncated = serialized.length > maxInlineChars;
  const rdf = truncated ? serialized.slice(0, maxInlineChars) : serialized;
  return textContent(
    JSON.stringify(
      {
        ifcPath,
        format,
        triples,
        truncated,
        length: serialized.length,
        rdf,
      },
      null,
      2
    )
  );
}

function callSummary(args) {
  const sampleSize = asInteger(args.sampleSize, 20, 0, 100);
  const { ifcPath, converter, model } = convertToModel(args);
  const subjects = converter.listSubjects(model).map((subject) => String(subject));
  const uniqueSubjects = Array.from(new Set(subjects));

  return textContent(
    JSON.stringify(
      {
        ifcPath,
        triples: modelSize(model),
        subjects: uniqueSubjects.length,
        sampleSubjects: uniqueSubjects.slice(0, sampleSize),
      },
      null,
      2
    )
  );
}

function withJavaStdoutCaptured(fn) {
  const System = importJavaClass("java.lang.System");
  const ByteArrayOutputStream = importJavaClass("java.io.ByteArrayOutputStream");
  const PrintStream = importJavaClass("java.io.PrintStream");
  const originalOut = System.out;
  const buffer = new ByteArrayOutputStream();
  const capture = new PrintStream(buffer);

  System.setOutSync(capture);
  try {
    return fn();
  } finally {
    capture.flushSync();
    System.setOutSync(originalOut);
  }
}

function rdfNodeToJson(node) {
  if (node.isURIResourceSync && node.isURIResourceSync()) {
    return { type: "uri", value: node.asResourceSync().getURISync() };
  }
  if (node.isLiteralSync && node.isLiteralSync()) {
    const literal = node.asLiteralSync();
    return {
      type: "literal",
      value: literal.getLexicalFormSync(),
      datatype: literal.getDatatypeURISync
        ? literal.getDatatypeURISync()
        : undefined,
      language: literal.getLanguageSync ? literal.getLanguageSync() : "",
    };
  }
  if (node.isAnonSync && node.isAnonSync()) {
    return { type: "blank", value: String(node) };
  }
  return { type: "unknown", value: String(node) };
}

function callQuery(args) {
  if (!args.sparql || typeof args.sparql !== "string") {
    throw new Error("sparql is required and must be a string");
  }
  const limit = asInteger(args.limit, 100, 1, 1000);
  const { ifcPath, model } = convertToModel(args);
  const QueryExecutionFactory = importJavaClass(
    "org.apache.jena.query.QueryExecutionFactory"
  );
  const queryExecution = QueryExecutionFactory.createSync(args.sparql, model);

  try {
    const resultSet = queryExecution.execSelectSync();
    const variables = resultSet.getResultVarsSync().toArraySync().map(String);
    const rows = [];

    while (resultSet.hasNextSync() && rows.length < limit) {
      const solution = resultSet.nextSolutionSync();
      const row = {};
      for (const variable of variables) {
        if (solution.containsSync(variable)) {
          row[variable] = rdfNodeToJson(solution.getSync(variable));
        }
      }
      rows.push(row);
    }

    return textContent(
      JSON.stringify(
        {
          ifcPath,
          variables,
          rows,
          limit,
          truncated: resultSet.hasNextSync(),
        },
        null,
        2
      )
    );
  } finally {
    if (queryExecution.closeSync) {
      queryExecution.closeSync();
    }
  }
}

function callRuntimeInfo() {
  let javaBridgeModule = null;
  try {
    javaBridgeModule = require.resolve("java-bridge", {
      paths: javaBridgeResolvePaths(),
    });
  } catch (err) {
    javaBridgeModule = `not found: ${err.message}`;
  }

  return textContent(
    JSON.stringify(
      {
        server: `${SERVER_NAME} ${SERVER_VERSION}`,
        node: process.version,
        platform: process.platform,
        cwd: process.cwd(),
        tmpdir: os.tmpdir(),
        defaultIfcFile: DEFAULT_IFC_FILE,
        defaultJavaHome: DEFAULT_JAVA_HOME,
        defaultJarDir: DEFAULT_JAR_DIR,
        javaHome: process.env.JAVA_HOME || DEFAULT_JAVA_HOME,
        javaBridgeModule,
      },
      null,
      2
    )
  );
}

function handleToolCallDirect(name, args) {
  const input = isObject(args) ? args : {};

  if (name === "ifctolbd_runtime_info") {
    return callRuntimeInfo();
  }

  switch (name) {
    case "convert_ifc_to_lbd":
      return callConvert(input);
    case "summarize_ifc_lbd":
      return callSummary(input);
    case "query_ifc_lbd":
      return callQuery(input);
    default:
      throw new Error(`Unknown tool: ${name}`);
  }
}

function handleToolCall(name, args) {
  return new Promise((resolve, reject) => {
    const child = fork(__filename, ["--worker"], {
      cwd: process.cwd(),
      silent: true,
    });

    child.stdout.on("data", () => {});
    child.stderr.on("data", (chunk) => {
      if (process.env.IFCTOLBD_MCP_DEBUG) {
        process.stderr.write(chunk);
      }
    });
    child.on("message", (message) => {
      if (message.ok) {
        resolve(message.result);
      } else {
        const err = new Error(message.error || "Worker tool call failed");
        err.stack = message.stack;
        reject(err);
      }
      child.kill();
    });
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code !== 0 && code !== null) {
        reject(new Error(`Worker exited with code ${code}`));
      }
    });
    child.send({ name, args });
  });
}

async function handle(message) {
  const id = message.id;

  try {
    switch (message.method) {
      case "initialize":
        result(id, {
          protocolVersion:
            message.params && message.params.protocolVersion
              ? message.params.protocolVersion
              : "2024-11-05",
          capabilities: {
            tools: {},
          },
          serverInfo: {
            name: SERVER_NAME,
            version: SERVER_VERSION,
          },
        });
        break;
      case "notifications/initialized":
        break;
      case "ping":
        result(id, {});
        break;
      case "tools/list":
        result(id, { tools });
        break;
      case "tools/call": {
        const params = message.params || {};
        result(id, await handleToolCall(params.name, params.arguments));
        break;
      }
      case "resources/list":
        result(id, { resources: [] });
        break;
      case "prompts/list":
        result(id, { prompts: [] });
        break;
      default:
        if (id !== undefined) {
          error(id, -32601, `Method not found: ${message.method}`);
        }
    }
  } catch (err) {
    error(id, -32000, err.message, err.stack);
  }
}

if (process.argv.includes("--worker")) {
  process.on("message", (message) => {
    try {
      process.send({
        ok: true,
        result: withJavaStdoutCaptured(() =>
          handleToolCallDirect(message.name, message.args)
        ),
      });
    } catch (err) {
      process.send({
        ok: false,
        error: err.message,
        stack: err.stack,
      });
    }
  });
  return;
}

const rl = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity,
});
process.stdin.resume();

let inputClosed = false;
let pendingRequests = 0;
const keepAlive = setInterval(() => {}, 1 << 30);

function maybeExit() {
  if (inputClosed && pendingRequests === 0) {
    clearInterval(keepAlive);
  }
}

rl.on("line", (line) => {
  const trimmed = line.trim();
  if (!trimmed) {
    return;
  }

  let message;
  try {
    message = JSON.parse(trimmed);
  } catch (err) {
    error(null, -32700, `Parse error: ${err.message}`);
    return;
  }

  pendingRequests += 1;
  handle(message).finally(() => {
    pendingRequests -= 1;
    maybeExit();
  });
});

rl.on("close", () => {
  inputClosed = true;
  maybeExit();
});
