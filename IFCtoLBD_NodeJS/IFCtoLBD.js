const fs = require("fs");
const os = require("os");
const path = require("path");
const { appendClasspath, ensureJvm, importClass } = require("java-bridge");

const DEFAULT_IFC_FILE = path.join(
  __dirname,
  "..",
  "IFCtoLBD",
  "src",
  "main",
  "resources",
  "Duplex_A.ifc"
);
const DEFAULT_JAVA_HOME = path.join(__dirname, "..", ".tools", "jdk");
const DEFAULT_JAR_DIR = path.join(__dirname, "java_libraries");

let jvmReady = false;
let classpathReady = false;
let ConverterClass = null;
let ByteArrayOutputStreamClass = null;

function withTrailingSeparator(directory) {
  return directory.endsWith(path.sep) ? directory : directory + path.sep;
}

function defaultTmpDir() {
  const directory = path.join(os.tmpdir(), `ifctolbd-node-${process.pid}`);
  fs.mkdirSync(directory, { recursive: true });
  return directory;
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

function initializeJvm(options = {}) {
  if (jvmReady) {
    return;
  }

  const javaHome = options.javaHome || process.env.JAVA_HOME || DEFAULT_JAVA_HOME;
  const tmpDir = options.tmpDir || process.env.TMPDIR || defaultTmpDir();
  const libPath = options.libPath || findJvmLibrary(javaHome);
  const opts = [
    `-Djava.io.tmpdir=${withTrailingSeparator(tmpDir)}`,
    ...(options.jvmOptions || []),
  ];

  ensureJvm({
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
  appendClasspath(findJars(options.jarDir));
  classpathReady = true;
}

function getConverterClass(options = {}) {
  initializeClasspath(options);

  if (!ConverterClass) {
    ConverterClass = importClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter");
  }

  return ConverterClass;
}

function getByteArrayOutputStreamClass(options = {}) {
  initializeClasspath(options);

  if (!ByteArrayOutputStreamClass) {
    ByteArrayOutputStreamClass = importClass("java.io.ByteArrayOutputStream");
  }

  return ByteArrayOutputStreamClass;
}

class IFCtoLBD {
  constructor(options = {}) {
    this.options = {
      baseUri: "https://example.com/",
      hasGeometry: false,
      levels: [1],
      ...options,
    };

    const Converter = getConverterClass(this.options);
    this.converter = new Converter(
      this.options.baseUri,
      this.options.hasGeometry,
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

  serializeModel(model, format = "TURTLE") {
    const ByteArrayOutputStream = getByteArrayOutputStreamClass(this.options);
    const outputStream = new ByteArrayOutputStream();

    model.writeSync(outputStream, format);

    return outputStream.toStringSync("UTF-8");
  }

  convertToTurtle(ifcFile = DEFAULT_IFC_FILE) {
    return this.serializeModel(this.convert(ifcFile), "TURTLE");
  }

  convertToSubjects(ifcFile = DEFAULT_IFC_FILE) {
    return this.listSubjects(this.convert(ifcFile));
  }
}

module.exports = {
  DEFAULT_IFC_FILE,
  DEFAULT_JAR_DIR,
  DEFAULT_JAVA_HOME,
  IFCtoLBD,
  findJars,
  initializeClasspath,
  initializeJvm,
};
