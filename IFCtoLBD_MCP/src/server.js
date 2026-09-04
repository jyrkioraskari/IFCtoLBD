#!/usr/bin/env node

"use strict";

const fs = require("fs");
const crypto = require("crypto");
const { fork } = require("child_process");
const os = require("os");
const path = require("path");
const readline = require("readline");

const SERVER_NAME = "ifctolbd-mcp";
const SERVER_VERSION = "0.1.0";
const MCP_ROOT = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(__dirname, "..", "..");
const DEFAULT_IFC_FILE = path.join(
  REPO_ROOT,
  "IFCtoLBD",
  "src",
  "main",
  "resources",
  "Duplex_A.ifc"
);
const DEFAULT_PARENT_JAVA_HOME = path.join(REPO_ROOT, ".tools", "jdk");
const DEFAULT_LOCAL_JAVA_HOME = path.join(MCP_ROOT, ".tools", "jdk");
const DEFAULT_JAVA_HOME = fs.existsSync(DEFAULT_PARENT_JAVA_HOME)
  ? DEFAULT_PARENT_JAVA_HOME
  : DEFAULT_LOCAL_JAVA_HOME;
const DEFAULT_CONVERTER_JAR = path.join(
  MCP_ROOT,
  "lib",
  "ifctolbd-converter.jar"
);
const DEFAULT_BASE_URI = "https://example.com/ifctolbd/";
const DEFAULT_FORMAT = "TURTLE";
const MAX_INLINE_CHARS = 200000;
const MAX_RESOURCE_CHARS = Number(process.env.IFCTOLBD_MCP_MAX_RESOURCE_CHARS || 5 * 1024 * 1024);
const MAX_MODELS = Number(process.env.IFCTOLBD_MCP_MAX_MODELS || 8);
const CONVERTER_JAR = path.resolve(
  process.env.IFCTOLBD_CONVERTER_JAR || DEFAULT_CONVERTER_JAR
);
const GEOMETRY_ROOT = path.join(os.tmpdir(), `ifctolbd-mcp-geometry-${process.pid}`);
const READ_ROOTS = parseRoots(process.env.IFCTOLBD_MCP_READ_ROOTS, [REPO_ROOT]);
const WRITE_ROOTS = parseRoots(process.env.IFCTOLBD_MCP_WRITE_ROOTS, [path.join(MCP_ROOT, "demo", "output")]);
const SHAPE_PACKS = Object.freeze({
  "core-bot": "core-bot-v1.0.0.ttl",
  "properties-units": "properties-units-v1.0.0.ttl",
  "geometry-crs": "geometry-crs-v1.0.0.ttl",
  "digital-twin-sensors": "digital-twin-sensors-v1.0.0.ttl",
  "fire-accessibility": "fire-accessibility-v1.0.0.ttl",
  "supply-chain-identifiers": "supply-chain-identifiers-v1.0.0.ttl",
  "sustainability-declarations": "sustainability-declarations-v1.0.0.ttl",
});

let javaBridge = null;
let jvmReady = false;
let classpathReady = false;
let converterClass = null;
let converterVersion = null;
let byteArrayOutputStreamClass = null;
const loadedModels = new Map();
const modelIdsByCacheKey = new Map();

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
const CONVERSION_PROFILES = [
  "core", "properties-simple", "properties-opm", "geometry-envelope", "geometry-full",
  "bim-gis", "compliance", "revision-ready", "geometry-external", "supply-chain", "sustainability"
];

const tools = [
  {
    name: "load_ifc",
    description: "Load and convert an IFC file once, returning a reusable model ID.",
    inputSchema: {
      type: "object",
      properties: {
        ifcPath: { type: "string" },
        baseUri: { type: "string", default: DEFAULT_BASE_URI },
        propertiesBlankNodes: { type: "boolean", default: true },
        propsLevel: { type: "integer", minimum: 1, maximum: 3, default: 1 },
        profile: { type: "string", enum: CONVERSION_PROFILES, default: "properties-simple" },
        modelScope: { type: "string" },
        validationShapePacks: { type: "array", items: { type: "string", enum: Object.keys(SHAPE_PACKS) }, default: [] }
      },
      additionalProperties: false
    }
  },
  {
    name: "compare_revisions",
    description: "Compare two loaded revision-ready conversions and retain their RDF change graph.",
    inputSchema: {
      type: "object", required: ["previousModelId", "currentModelId"],
      properties: {
        previousModelId: { type: "string" }, currentModelId: { type: "string" }
      },
      additionalProperties: false
    }
  },
  {
    name: "describe_model",
    description: "Describe a previously loaded model without reconverting it.",
    inputSchema: {
      type: "object", required: ["modelId"],
      properties: { modelId: { type: "string" }, sampleSize: { type: "integer", minimum: 0, maximum: 100, default: 20 } },
      additionalProperties: false
    }
  },
  {
    name: "get_entity",
    description: "Return the outgoing RDF statements for one entity in a loaded model.",
    inputSchema: {
      type: "object", required: ["modelId", "entity"],
      properties: { modelId: { type: "string" }, entity: { type: "string" }, limit: { type: "integer", minimum: 1, maximum: 1000, default: 200 } },
      additionalProperties: false
    }
  },
  {
    name: "list_classes",
    description: "List RDF classes and instance counts in a loaded model.",
    inputSchema: {
      type: "object", required: ["modelId"],
      properties: { modelId: { type: "string" }, limit: { type: "integer", minimum: 1, maximum: 1000, default: 100 } },
      additionalProperties: false
    }
  },
  {
    name: "list_properties",
    description: "List RDF predicates and usage counts in a loaded model.",
    inputSchema: {
      type: "object", required: ["modelId"],
      properties: { modelId: { type: "string" }, limit: { type: "integer", minimum: 1, maximum: 1000, default: 100 } },
      additionalProperties: false
    }
  },
  {
    name: "close_model",
    description: "Close a loaded model and release its Java/Jena resources.",
    inputSchema: {
      type: "object", required: ["modelId"],
      properties: { modelId: { type: "string" } }, additionalProperties: false
    }
  },
  {
    name: "query_model",
    description: "Run a guarded SPARQL SELECT query against a loaded model. SERVICE is disabled.",
    inputSchema: {
      type: "object", required: ["modelId", "sparql"],
      properties: {
        modelId: { type: "string" }, sparql: { type: "string" },
        limit: { type: "integer", minimum: 1, maximum: 1000, default: 100 }
      },
      additionalProperties: false
    }
  },
  {
    name: "validate_model",
    description: "Validate a loaded model with versioned SHACL packs and retain the standard report as a resource.",
    inputSchema: {
      type: "object", required: ["modelId"],
      properties: {
        modelId: { type: "string" },
        shapePacks: { type: "array", items: { type: "string", enum: Object.keys(SHAPE_PACKS) }, default: ["core-bot"] }
      },
      additionalProperties: false
    }
  },
  {
    name: "explain_validation",
    description: "Return a structured, non-mutating explanation of a retained SHACL validation report.",
    inputSchema: {
      type: "object", required: ["modelId", "reportId"],
      properties: {
        modelId: { type: "string" }, reportId: { type: "string" },
        limit: { type: "integer", minimum: 1, maximum: 1000, default: 100 }
      },
      additionalProperties: false
    }
  },
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
    name: "list_ifc_elements_with_properties",
    description:
      "List IFC/LBD elements of a given RDF type with their IFCtoLBD simple properties and attributes.",
    inputSchema: {
      type: "object",
      properties: {
        ifcPath: {
          type: "string",
          description:
            "Path to an IFC file. Defaults to the repository sample Duplex_A.ifc.",
        },
        elementType: {
          type: "string",
          description:
            "RDF type to list, as a QName such as bot:Element or beo:Wall, or as a full IRI.",
          default: "bot:Element",
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
        includeProperties: {
          type: "boolean",
          description: "Include predicates ending in _property_simple.",
          default: true,
        },
        includeAttributes: {
          type: "boolean",
          description: "Include predicates ending in _attribute_simple.",
          default: true,
        },
        limit: {
          type: "integer",
          description: "Maximum number of elements returned.",
          minimum: 1,
          maximum: 1000,
          default: 100,
        },
      },
      additionalProperties: false,
    },
  },
  {
    name: "list_ifc_elements_with_geometry",
    description:
      "List IFC/LBD elements of a given RDF type with geometry literals generated by IFCtoLBD, including WKT and optionally OBJ.",
    inputSchema: {
      type: "object",
      properties: {
        ifcPath: {
          type: "string",
          description:
            "Path to an IFC file. Defaults to the repository sample Duplex_A.ifc.",
        },
        elementType: {
          type: "string",
          description:
            "RDF type to list, as a QName such as bot:Element, bot:Space, or beo:Wall, or as a full IRI.",
          default: "bot:Element",
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
        includeWkt: {
          type: "boolean",
          description: "Include GeoSPARQL WKT literals when present.",
          default: true,
        },
        includeObj: {
          type: "boolean",
          description:
            "Include fog:asObj_v3.0-obj geometry literals when present. These can be large.",
          default: false,
        },
        decodeObj: {
          type: "boolean",
          description:
            "Decode base64 OBJ literals into OBJ text before returning them.",
          default: true,
        },
        hasBoundingBoxWKT: {
          type: "boolean",
          description:
            "Ask IFCtoLBD to include bounding-box WKT geometry when supported by the bundled converter jars.",
          default: false,
        },
        maxGeometryChars: {
          type: "integer",
          description:
            "Maximum characters returned for each WKT or OBJ literal before truncation.",
          minimum: 1000,
          maximum: 5000000,
          default: 50000,
        },
        limit: {
          type: "integer",
          description: "Maximum number of elements returned.",
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

function structuredContent(value) {
  return {
    content: [{ type: "text", text: JSON.stringify(value, null, 2) }],
    structuredContent: value,
  };
}

function parseRoots(value, defaults) {
  return (value ? value.split(path.delimiter) : defaults)
    .filter(Boolean)
    .map((root) => path.resolve(root));
}

function isWithinRoot(candidate, root) {
  const relative = path.relative(root, candidate);
  return relative === "" || (!relative.startsWith(".." + path.sep) && relative !== "..");
}

function requireAllowedPath(candidate, roots, operation) {
  let canonical = path.resolve(candidate);
  try {
    canonical = fs.realpathSync(canonical);
  } catch (err) {
    const parent = fs.realpathSync(path.dirname(canonical));
    canonical = path.join(parent, path.basename(canonical));
  }
  if (!roots.some((root) => isWithinRoot(canonical, root))) {
    throw new Error(`${operation} path is outside configured roots: ${canonical}`);
  }
  return canonical;
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

function converterJar(jar = CONVERTER_JAR) {
  if (!fs.existsSync(jar) || !fs.statSync(jar).isFile()) {
    throw new Error(`IFCtoLBD distribution JAR is missing: ${jar}. Run npm run build:converter.`);
  }
  return jar;
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
  // A single shaded distribution makes class loading deterministic.
  bridge.appendClasspath(converterJar(options.converterJar));
  classpathReady = true;
}

function importJavaClass(className) {
  initializeClasspath();
  return loadJavaBridge().importClass(className);
}

function getConverterClass() {
  initializeClasspath();
  if (!converterClass) {
    converterClass = importJavaClass("org.linkedbuildingdata.ifc2lbd.McpConversionBridge");
  }
  return converterClass;
}

function getConverterVersion() {
  if (!converterVersion) converterVersion = String(getConverterClass().converterVersionSync());
  return converterVersion;
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
    this.artifactDirectory = this.options.artifactDirectory || "";
    this.converter = new Converter(
      this.options.baseUri,
      this.options.propertiesBlankNodes,
      this.options.levels[0],
      this.artifactDirectory,
      this.options.artifactBaseUri || "ifctolbd://artifacts/"
    );
  }

  convert(ifcFile = DEFAULT_IFC_FILE, options = {}) {
    const resolvedIfcFile = path.resolve(ifcFile);
    const validationPacks = options.validationShapePacks || [];
    const result = this.converter.convertSync(
      resolvedIfcFile, options.profile || "properties-simple", options.modelScope || null, validationPacks
    );
    return {
      result,
      model: this.converter.dataModelSync(result),
      manifestModel: result.getManifestModelSync(),
      validationModel: result.getValidationModelSync(),
    };
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
  const candidate = requireAllowedPath(ifcPath || DEFAULT_IFC_FILE, READ_ROOTS, "Read");
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
  const candidate = requireAllowedPath(outputPath, WRITE_ROOTS, "Write");
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
    artifactDirectory: args.artifactDirectory,
    artifactBaseUri: args.artifactBaseUri,
  });
}

function convertToModel(args) {
  const ifcPath = resolveIfcPath(args.ifcPath);
  const converter = createConverter(args);
  return { ifcPath, converter, ...converter.convert(ifcPath, args) };
}

function checksumFile(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function readIfcSchema(filePath) {
  const header = fs.readFileSync(filePath, { encoding: "utf8" }).slice(0, 256 * 1024);
  const match = header.match(/FILE_SCHEMA\s*\(\s*\(\s*['"]([^'"]+)['"]/i);
  return match ? match[1] : "unknown";
}

function conversionProfile(args) {
  return {
    baseUri: args.baseUri || DEFAULT_BASE_URI,
    propertiesBlankNodes: asBoolean(args.propertiesBlankNodes, true),
    propsLevel: asInteger(args.propsLevel, 1, 1, 3),
    profile: args.profile || "properties-simple",
    modelScope: args.modelScope || null,
    validationShapePacks: selectedShapePacks(args.validationShapePacks, true),
  };
}

function requireLoadedModel(modelId) {
  const entry = loadedModels.get(String(modelId || ""));
  if (!entry) {
    throw new Error(`Unknown or closed modelId: ${modelId}`);
  }
  entry.lastAccessedAt = new Date().toISOString();
  return entry;
}

function closeJavaObject(object) {
  if (object && typeof object.closeSync === "function") {
    object.closeSync();
  }
}

function callLoadIfc(args) {
  const ifcPath = resolveIfcPath(args.ifcPath);
  const checksum = checksumFile(ifcPath);
  const schema = readIfcSchema(ifcPath);
  const profile = conversionProfile(args);
  const actualConverterVersion = getConverterVersion();
  const cacheKey = crypto.createHash("sha256")
    .update(JSON.stringify({ checksum, converterVersion: actualConverterVersion, profile }))
    .digest("hex");
  const cachedId = modelIdsByCacheKey.get(cacheKey);
  if (cachedId && loadedModels.has(cachedId)) {
    const cached = requireLoadedModel(cachedId);
    return structuredContent(modelDescriptor(cached, true));
  }
  if (loadedModels.size >= MAX_MODELS) {
    throw new Error(`Loaded model limit (${MAX_MODELS}) reached; call close_model first`);
  }
  const modelId = `ifc-${cacheKey.slice(0, 16)}`;
  const artifactDirectory = path.join(GEOMETRY_ROOT, modelId);
  const converted = convertToModel({ ...args, ...profile, ifcPath, artifactDirectory,
    artifactBaseUri: `ifctolbd://models/${modelId}/geometry/` });
  const entry = {
    modelId, cacheKey, checksum, schema, profile, ifcPath,
    converterVersion: actualConverterVersion,
    converter: converted.converter,
    model: converted.model,
    result: converted.result,
    manifestModel: converted.manifestModel,
    validationModel: converted.validationModel,
    artifactDirectory,
    comparisons: new Map(),
    loadedAt: new Date().toISOString(),
    lastAccessedAt: new Date().toISOString(),
    validationReports: new Map(),
  };
  loadedModels.set(modelId, entry);
  modelIdsByCacheKey.set(cacheKey, modelId);
  return structuredContent(modelDescriptor(entry, false));
}

function modelDescriptor(entry, cacheHit) {
  return {
    modelId: entry.modelId,
    checksum: entry.checksum,
    checksumAlgorithm: "sha256",
    schema: entry.schema,
    converterVersion: entry.converterVersion,
    profile: entry.profile,
    triples: modelSize(entry.model),
    cacheHit,
    loadedAt: entry.loadedAt,
  };
}

function callDescribeModel(args) {
  const entry = requireLoadedModel(args.modelId);
  const sampleSize = asInteger(args.sampleSize, 20, 0, 100);
  const subjects = entry.converter.listSubjects(entry.model).map(String);
  return structuredContent({
    ...modelDescriptor(entry, true),
    subjects: new Set(subjects).size,
    sampleSubjects: Array.from(new Set(subjects)).slice(0, sampleSize),
  });
}

function callGetEntity(args) {
  const entry = requireLoadedModel(args.modelId);
  const entity = String(args.entity || "");
  if (!/^https?:\/\/[^\s<>"{}|^`\\]+$/.test(entity)) {
    throw new Error("entity must be a full http(s) IRI");
  }
  const limit = asInteger(args.limit, 200, 1, 1000);
  const data = executeSelect(entry.model,
    `SELECT ?predicate ?value WHERE { <${entity}> ?predicate ?value } ORDER BY ?predicate`, limit);
  return structuredContent({ modelId: entry.modelId, entity, ...data });
}

function callListClasses(args) {
  const entry = requireLoadedModel(args.modelId);
  const limit = asInteger(args.limit, 100, 1, 1000);
  const data = executeSelect(entry.model,
    "SELECT ?class (COUNT(DISTINCT ?instance) AS ?instances) WHERE { ?instance a ?class } GROUP BY ?class ORDER BY DESC(?instances) ?class", limit);
  return structuredContent({ modelId: entry.modelId, ...data });
}

function callListProperties(args) {
  const entry = requireLoadedModel(args.modelId);
  const limit = asInteger(args.limit, 100, 1, 1000);
  const data = executeSelect(entry.model,
    "SELECT ?property (COUNT(*) AS ?uses) WHERE { ?subject ?property ?value } GROUP BY ?property ORDER BY DESC(?uses) ?property", limit);
  return structuredContent({ modelId: entry.modelId, ...data });
}

function callCloseModel(args) {
  const entry = requireLoadedModel(args.modelId);
  loadedModels.delete(entry.modelId);
  modelIdsByCacheKey.delete(entry.cacheKey);
  for (const report of entry.validationReports.values()) closeJavaObject(report.model);
  for (const comparison of entry.comparisons.values()) closeJavaObject(comparison.diff);
  closeJavaObject(entry.result);
  closeJavaObject(entry.converter.converter);
  return structuredContent({ modelId: entry.modelId, closed: true });
}

function callCompareRevisions(args) {
  const previous = requireLoadedModel(args.previousModelId);
  const current = requireLoadedModel(args.currentModelId);
  const diff = current.converter.converter.compareSync(previous.result, current.result);
  const comparisonId = `diff-${crypto.createHash("sha256")
    .update(`${previous.modelId}|${current.modelId}`).digest("hex").slice(0, 16)}`;
  const model = diff.getChangeModelSync();
  const summary = {
    comparisonId,
    previousModelId: previous.modelId,
    currentModelId: current.modelId,
    added: Number(diff.getAddedCountSync()),
    removed: Number(diff.getRemovedCountSync()),
    changeResource: `ifctolbd://models/${current.modelId}/comparisons/${comparisonId}`,
  };
  current.comparisons.set(comparisonId, { diff, model, summary });
  return structuredContent(summary);
}

function callQueryModel(args) {
  if (!args.sparql || typeof args.sparql !== "string") {
    throw new Error("sparql is required and must be a string");
  }
  const entry = requireLoadedModel(args.modelId);
  const limit = asInteger(args.limit, 100, 1, 1000);
  return structuredContent({ modelId: entry.modelId, ...executeSelect(entry.model, args.sparql, limit) });
}

function selectedShapePacks(value, allowEmpty = false) {
  const packs = value === undefined ? (allowEmpty ? [] : ["core-bot"]) : value;
  if (!Array.isArray(packs) || (!allowEmpty && packs.length === 0)) {
    throw new Error(`shapePacks must be ${allowEmpty ? "an" : "a non-empty"} array`);
  }
  for (const pack of packs) {
    if (!SHAPE_PACKS[pack]) throw new Error(`Unknown SHACL shape pack: ${pack}`);
  }
  return Array.from(new Set(packs)).sort();
}

function callValidateModel(args) {
  const entry = requireLoadedModel(args.modelId);
  const packs = selectedShapePacks(args.shapePacks);
  const ModelFactory = importJavaClass("org.apache.jena.rdf.model.ModelFactory");
  const RDFDataMgr = importJavaClass("org.apache.jena.riot.RDFDataMgr");
  const Shapes = importJavaClass("org.apache.jena.shacl.Shapes");
  const ShaclValidator = importJavaClass("org.apache.jena.shacl.ShaclValidator");
  const shapesModel = ModelFactory.createDefaultModelSync();
  for (const pack of packs) {
    const shapePath = path.join(REPO_ROOT, "IFCtoLBD", "src", "main", "resources", "shacl", SHAPE_PACKS[pack]);
    shapesModel.addSync(RDFDataMgr.loadModelSync(shapePath));
  }
  const report = ShaclValidator.getSync().validateSync(Shapes.parseSync(shapesModel.getGraphSync()), entry.model.getGraphSync());
  const reportModel = report.getModelSync();
  const reportKey = crypto.createHash("sha256").update(`${entry.checksum}|${packs.join("|")}`).digest("hex");
  const reportId = `shacl-${reportKey.slice(0, 16)}`;
  const summary = {
    modelId: entry.modelId,
    reportId,
    conforms: Boolean(report.conformsSync()),
    shapePacks: packs.map((id) => ({ id, version: "1.0.0" })),
    reportResource: `ifctolbd://models/${entry.modelId}/validation/${reportId}`,
    dataModified: false,
  };
  entry.validationReports.set(reportId, { model: reportModel, summary });
  closeJavaObject(shapesModel);
  return structuredContent(summary);
}

function callExplainValidation(args) {
  const entry = requireLoadedModel(args.modelId);
  const retained = entry.validationReports.get(String(args.reportId || ""));
  if (!retained) throw new Error(`Unknown validation report: ${args.reportId}`);
  const limit = asInteger(args.limit, 100, 1, 1000);
  const query = `
PREFIX sh: <http://www.w3.org/ns/shacl#>
SELECT ?severity ?focusNode ?path ?value ?sourceShape ?message WHERE {
  ?report sh:result ?result .
  ?result sh:resultSeverity ?severity ; sh:focusNode ?focusNode ; sh:sourceShape ?sourceShape .
  OPTIONAL { ?result sh:resultPath ?path }
  OPTIONAL { ?result sh:value ?value }
  OPTIONAL { ?result sh:resultMessage ?message }
} ORDER BY ?severity ?focusNode ?path`;
  const details = executeSelect(retained.model, query, limit);
  return structuredContent({ ...retained.summary, violations: details.rows, truncated: details.truncated });
}

function callReadModelResource(args) {
  const uri = new URL(String(args.uri || ""));
  if (uri.protocol !== "ifctolbd:" || uri.hostname !== "models") {
    throw new Error(`Unsupported resource URI: ${args.uri}`);
  }
  const match = uri.pathname.match(/^\/([^/]+)\/(rdf|manifest|validation|validation\/([^/]+)|comparisons\/([^/]+)|geometry\/([a-f0-9]{64}\.[A-Za-z0-9]+))$/);
  if (!match) throw new Error(`Unsupported resource URI: ${args.uri}`);
  const entry = requireLoadedModel(decodeURIComponent(match[1]));
  const format = normalizeFormat(uri.searchParams.get("format") || DEFAULT_FORMAT);
  let resourceModel = entry.model;
  if (match[2] === "manifest") resourceModel = entry.manifestModel;
  if (match[2] === "validation") resourceModel = entry.validationModel;
  if (match[2].startsWith("validation/")) {
    const retained = entry.validationReports.get(decodeURIComponent(match[3]));
    if (!retained) throw new Error(`Unknown validation report: ${match[3]}`);
    resourceModel = retained.model;
  }
  if (match[2].startsWith("comparisons/")) {
    const retained = entry.comparisons.get(decodeURIComponent(match[4]));
    if (!retained) throw new Error(`Unknown revision comparison: ${match[4]}`);
    resourceModel = retained.model;
  }
  if (match[2].startsWith("geometry/")) {
    const fileName = match[5];
    const artifactPath = path.join(entry.artifactDirectory, fileName);
    if (!fs.existsSync(artifactPath)) throw new Error(`Unknown geometry artifact: ${fileName}`);
    const data = fs.readFileSync(artifactPath);
    if (data.length > MAX_RESOURCE_CHARS) throw new Error(`Geometry resource exceeds the ${MAX_RESOURCE_CHARS} byte output quota`);
    const extension = path.extname(fileName).slice(1).toLowerCase();
    const mimeType = extension === "obj" ? "model/obj" : "application/octet-stream";
    return { uri: args.uri, mimeType, blob: data.toString("base64") };
  }
  const serialized = entry.converter.serializeModel(resourceModel, format);
  if (serialized.length > MAX_RESOURCE_CHARS) {
    throw new Error(`RDF resource exceeds the ${MAX_RESOURCE_CHARS} character output quota`);
  }
  return { uri: args.uri, mimeType: format === "TURTLE" ? "text/turtle" : "application/rdf+xml", text: serialized };
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

function executeSelect(model, sparql, limit) {
  if (sparql.length > 100000) {
    throw new Error("SPARQL query exceeds the 100000 character quota");
  }
  const QueryFactory = importJavaClass("org.apache.jena.query.QueryFactory");
  const query = QueryFactory.createSync(sparql);
  if (!query.isSelectTypeSync()) {
    throw new Error("Only SPARQL SELECT queries are permitted");
  }
  if (/\bSERVICE\b/i.test(query.toStringSync())) {
    throw new Error("SPARQL SERVICE is disabled");
  }
  const QueryExecution = importJavaClass("org.apache.jena.query.QueryExecution");
  const queryExecution = QueryExecution.modelSync(model)
    .querySync(query)
    .timeoutSync(10000)
    .buildSync();

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

    return {
      variables,
      rows,
      limit,
      truncated: resultSet.hasNextSync(),
    };
  } finally {
    if (queryExecution.closeSync) {
      queryExecution.closeSync();
    }
  }
}

function callQuery(args) {
  if (!args.sparql || typeof args.sparql !== "string") {
    throw new Error("sparql is required and must be a string");
  }
  const limit = asInteger(args.limit, 100, 1, 1000);
  const { ifcPath, model } = convertToModel(args);
  const queryResult = executeSelect(model, args.sparql, limit);

  return textContent(
    JSON.stringify(
      {
        ifcPath,
        ...queryResult,
      },
      null,
      2
    )
  );
}

const TYPE_PREFIXES = {
  bot: "https://w3id.org/bot#",
  beo: "https://pi.pauwel.be/voc/buildingelement#",
  mep: "https://pi.pauwel.be/voc/distributionelement#",
  props: "http://lbd.arch.rwth-aachen.de/props#",
  product: "https://w3id.org/product#",
};

function sparqlTypeTerm(elementType = "bot:Element") {
  const value = String(elementType || "bot:Element").trim();
  if (!value) {
    throw new Error("elementType must not be empty");
  }
  if (/^https?:\/\/[^\s<>"{}|^`\\]+$/.test(value)) {
    return `<${value}>`;
  }
  const qnameMatch = value.match(
    /^([A-Za-z][A-Za-z0-9_-]*):([A-Za-z_][A-Za-z0-9_-]*)$/
  );
  if (qnameMatch && TYPE_PREFIXES[qnameMatch[1]]) {
    return `${qnameMatch[1]}:${qnameMatch[2]}`;
  }
  throw new Error(
    "elementType must be a full http(s) IRI or a supported QName prefix: " +
      Object.keys(TYPE_PREFIXES).join(", ")
  );
}

function localName(uri) {
  const text = String(uri || "");
  const hash = text.lastIndexOf("#");
  const slash = text.lastIndexOf("/");
  const index = Math.max(hash, slash);
  return index >= 0 ? text.slice(index + 1) : text;
}

function literalText(nodeJson) {
  return nodeJson && Object.prototype.hasOwnProperty.call(nodeJson, "value")
    ? String(nodeJson.value)
    : undefined;
}

function stripSimpleSuffix(name) {
  return String(name || "").replace(/_(property|attribute)_simple$/, "");
}

function compactValue(nodeJson) {
  if (!nodeJson) {
    return undefined;
  }
  if (nodeJson.type === "literal") {
    return {
      value: nodeJson.value,
      datatype: nodeJson.datatype,
      language: nodeJson.language,
    };
  }
  return nodeJson;
}

function truncateText(text, maxChars) {
  if (text === undefined) {
    return undefined;
  }
  const value = String(text);
  return {
    value: value.length > maxChars ? value.slice(0, maxChars) : value,
    length: value.length,
    truncated: value.length > maxChars,
  };
}

function decodeBase64(text) {
  const normalized = String(text || "").trim();
  if (!normalized) {
    return "";
  }
  return Buffer.from(normalized, "base64").toString("utf8");
}

function callListElementsWithProperties(args) {
  const limit = asInteger(args.limit, 100, 1, 1000);
  const includeProperties = asBoolean(args.includeProperties, true);
  const includeAttributes = asBoolean(args.includeAttributes, true);
  if (!includeProperties && !includeAttributes) {
    throw new Error(
      "At least one of includeProperties or includeAttributes must be true"
    );
  }

  const typeTerm = sparqlTypeTerm(args.elementType);
  const filters = [];
  if (includeProperties) {
    filters.push('STRENDS(STR(?predicate), "_property_simple")');
  }
  if (includeAttributes) {
    filters.push('STRENDS(STR(?predicate), "_attribute_simple")');
  }

  const { ifcPath, model } = convertToModel(args);
  const sparql = `
PREFIX bot: <https://w3id.org/bot#>
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>
PREFIX mep: <https://pi.pauwel.be/voc/distributionelement#>
PREFIX product: <https://w3id.org/product#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX props: <http://lbd.arch.rwth-aachen.de/props#>

SELECT ?element ?guid ?predicate ?name ?kind ?value WHERE {
  ?element rdf:type ${typeTerm} .
  ?element ?predicate ?value .
  OPTIONAL { ?element props:globalIdIfcRoot_attribute_simple ?guid . }
  BIND(STRAFTER(STR(?predicate), "#") AS ?name)
  BIND(IF(STRENDS(STR(?predicate), "_property_simple"), "property", "attribute") AS ?kind)
  FILTER (${filters.join(" || ")})
}
ORDER BY ?element ?predicate
`;

  const queryResult = executeSelect(model, sparql, limit * 200);
  const elements = [];
  const byUri = new Map();

  for (const row of queryResult.rows) {
    const elementUri = literalText(row.element);
    if (!elementUri) {
      continue;
    }
    let element = byUri.get(elementUri);
    if (!element) {
      if (elements.length >= limit) {
        continue;
      }
      element = {
        uri: elementUri,
        localName: localName(elementUri),
        guid: literalText(row.guid),
        properties: [],
        attributes: [],
      };
      byUri.set(elementUri, element);
      elements.push(element);
    }

    const kind = literalText(row.kind) === "attribute" ? "attributes" : "properties";
    element[kind].push({
      name: stripSimpleSuffix(literalText(row.name)),
      predicate: literalText(row.predicate),
      value: compactValue(row.value),
    });
  }

  return textContent(
    JSON.stringify(
      {
        ifcPath,
        elementType: args.elementType || "bot:Element",
        count: elements.length,
        truncated: queryResult.truncated || byUri.size > elements.length,
        elements,
      },
      null,
      2
    )
  );
}

function callListElementsWithGeometry(args) {
  const limit = asInteger(args.limit, 100, 1, 1000);
  const includeWkt = asBoolean(args.includeWkt, true);
  const includeObj = asBoolean(args.includeObj, false);
  const decodeObj = asBoolean(args.decodeObj, true);
  const maxGeometryChars = asInteger(
    args.maxGeometryChars,
    50000,
    1000,
    5000000
  );
  if (!includeWkt && !includeObj) {
    throw new Error("At least one of includeWkt or includeObj must be true");
  }

  const typeTerm = sparqlTypeTerm(args.elementType);
  const { ifcPath, model } = convertToModel({
    ...args,
    profile: asBoolean(args.hasBoundingBoxWKT, false) ? "geometry-envelope" : "geometry-full",
  });
  const sparql = `
PREFIX bot: <https://w3id.org/bot#>
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>
PREFIX mep: <https://pi.pauwel.be/voc/distributionelement#>
PREFIX product: <https://w3id.org/product#>
PREFIX geo: <http://www.opengis.net/ont/geosparql#>
PREFIX fog: <https://w3id.org/fog#>
PREFIX omg: <https://w3id.org/omg#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX props: <http://lbd.arch.rwth-aachen.de/props#>

SELECT ?element ?guid ?geometry ?wkt ?obj WHERE {
  ?element rdf:type ${typeTerm} .
  ?element omg:hasGeometry ?geometry .
  OPTIONAL { ?element props:globalIdIfcRoot_attribute_simple ?guid . }
  OPTIONAL { ?geometry geo:asWKT ?wkt . }
  OPTIONAL { ?geometry fog:asObj_v3.0-obj ?obj . }
}
ORDER BY ?element ?geometry
`;

  const queryResult = executeSelect(model, sparql, limit * 20);
  const elements = [];
  const byUri = new Map();

  for (const row of queryResult.rows) {
    const elementUri = literalText(row.element);
    if (!elementUri) {
      continue;
    }
    let element = byUri.get(elementUri);
    if (!element) {
      if (elements.length >= limit) {
        continue;
      }
      element = {
        uri: elementUri,
        localName: localName(elementUri),
        guid: literalText(row.guid),
        geometries: [],
      };
      byUri.set(elementUri, element);
      elements.push(element);
    }

    const geometry = {
      uri: literalText(row.geometry),
    };
    if (includeWkt && row.wkt) {
      geometry.wkt = truncateText(literalText(row.wkt), maxGeometryChars);
    }
    if (includeObj && row.obj) {
      const encoded = literalText(row.obj);
      const obj = decodeObj ? decodeBase64(encoded) : encoded;
      geometry.obj = {
        encoding: decodeObj ? "utf8" : "base64",
        ...truncateText(obj, maxGeometryChars),
      };
    }
    element.geometries.push(geometry);
  }

  return textContent(
    JSON.stringify(
      {
        ifcPath,
        elementType: args.elementType || "bot:Element",
        count: elements.length,
        truncated: queryResult.truncated || byUri.size > elements.length,
        elements,
      },
      null,
      2
    )
  );
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
        converterJar: CONVERTER_JAR,
        converterVersion: getConverterVersion(),
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
    case "load_ifc":
      return callLoadIfc(input);
    case "describe_model":
      return callDescribeModel(input);
    case "get_entity":
      return callGetEntity(input);
    case "list_classes":
      return callListClasses(input);
    case "list_properties":
      return callListProperties(input);
    case "close_model":
      return callCloseModel(input);
    case "compare_revisions":
      return callCompareRevisions(input);
    case "query_model":
      return callQueryModel(input);
    case "validate_model":
      return callValidateModel(input);
    case "explain_validation":
      return callExplainValidation(input);
    case "__read_model_resource":
      return callReadModelResource(input);
    case "convert_ifc_to_lbd":
      return callConvert(input);
    case "summarize_ifc_lbd":
      return callSummary(input);
    case "query_ifc_lbd":
      return callQuery(input);
    case "list_ifc_elements_with_properties":
      return callListElementsWithProperties(input);
    case "list_ifc_elements_with_geometry":
      return callListElementsWithGeometry(input);
    default:
      throw new Error(`Unknown tool: ${name}`);
  }
}

let worker = null;
let nextWorkerRequestId = 1;
const workerRequests = new Map();
const SESSION_TOOLS = new Set([
  "load_ifc", "describe_model", "get_entity", "list_classes", "list_properties", "query_model", "close_model",
  "validate_model", "explain_validation", "compare_revisions", "__read_model_resource"
]);

function getWorker() {
  if (worker && worker.connected) {
    return worker;
  }
  worker = fork(__filename, ["--worker"], { cwd: process.cwd(), silent: true });
  worker.stdout.on("data", () => {});
  worker.stderr.on("data", (chunk) => {
    if (process.env.IFCTOLBD_MCP_DEBUG) process.stderr.write(chunk);
  });
  worker.on("message", (message) => {
    const pending = workerRequests.get(message.requestId);
    if (!pending) return;
    workerRequests.delete(message.requestId);
    if (message.ok) pending.resolve(message.result);
    else pending.reject(Object.assign(new Error(message.error || "Worker tool call failed"), { stack: message.stack }));
  });
  worker.on("exit", (code) => {
    const failure = new Error(`Persistent worker exited with code ${code}`);
    for (const pending of workerRequests.values()) pending.reject(failure);
    workerRequests.clear();
    worker = null;
  });
  return worker;
}

function handleToolCall(name, args) {
  if (!SESSION_TOOLS.has(name)) {
    return handleEphemeralToolCall(name, args);
  }
  return new Promise((resolve, reject) => {
    const requestId = nextWorkerRequestId++;
    workerRequests.set(requestId, { resolve, reject });
    getWorker().send({ requestId, name, args });
  });
}

function handleEphemeralToolCall(name, args) {
  return new Promise((resolve, reject) => {
    const child = fork(__filename, ["--worker"], { cwd: process.cwd(), silent: true });
    const requestId = 1;
    child.stdout.on("data", () => {});
    child.stderr.on("data", (chunk) => {
      if (process.env.IFCTOLBD_MCP_DEBUG) process.stderr.write(chunk);
    });
    child.on("message", (message) => {
      if (message.ok) resolve(message.result);
      else reject(Object.assign(new Error(message.error || "Worker tool call failed"), { stack: message.stack }));
      child.kill();
    });
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code !== 0 && code !== null && code !== 143) reject(new Error(`Worker exited with code ${code}`));
    });
    child.send({ requestId, name, args });
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
            resources: {},
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
      case "resources/templates/list":
        result(id, { resourceTemplates: [{
          uriTemplate: "ifctolbd://models/{modelId}/rdf{?format}",
          name: "Loaded model RDF",
          description: "Serialized RDF for a loaded IFC model (default format: TURTLE)",
          mimeType: "text/turtle",
        }, {
          uriTemplate: "ifctolbd://models/{modelId}/manifest{?format}",
          name: "Conversion manifest",
          description: "The immutable manifest graph returned by ConversionResult",
          mimeType: "text/turtle",
        }, {
          uriTemplate: "ifctolbd://models/{modelId}/validation{?format}",
          name: "Conversion validation graph",
          description: "The validation graph returned by ConversionResult",
          mimeType: "text/turtle",
        }, {
          uriTemplate: "ifctolbd://models/{modelId}/validation/{reportId}{?format}",
          name: "SHACL validation report",
          description: "A standard RDF SHACL validation report retained for a loaded model",
          mimeType: "text/turtle",
        }, {
          uriTemplate: "ifctolbd://models/{modelId}/comparisons/{comparisonId}{?format}",
          name: "Revision comparison",
          description: "RDF change graph produced by compare_revisions",
          mimeType: "text/turtle",
        }, {
          uriTemplate: "ifctolbd://models/{modelId}/geometry/{artifact}",
          name: "Geometry artifact",
          description: "Content-addressed external geometry generated by the geometry-external profile",
          mimeType: "application/octet-stream",
        }] });
        break;
      case "resources/read": {
        const resource = await handleToolCall("__read_model_resource", { uri: message.params && message.params.uri });
        result(id, { contents: [resource] });
        break;
      }
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
        requestId: message.requestId,
        ok: true,
        result: withJavaStdoutCaptured(() =>
          handleToolCallDirect(message.name, message.args)
        ),
      });
    } catch (err) {
      process.send({
        requestId: message.requestId,
        ok: false,
        error: err.message,
        stack: err.stack,
      });
    }
  });
  process.on("disconnect", () => {
    for (const entry of loadedModels.values()) {
      for (const report of entry.validationReports.values()) closeJavaObject(report.model);
      for (const comparison of entry.comparisons.values()) closeJavaObject(comparison.diff);
      closeJavaObject(entry.result);
      closeJavaObject(entry.converter.converter);
    }
    process.exit(0);
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
    if (worker && worker.connected) worker.disconnect();
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
