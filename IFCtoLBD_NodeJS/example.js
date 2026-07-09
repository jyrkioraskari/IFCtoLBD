const fs = require("fs");
const path = require("path");
const { appendClasspath, ensureJvm, importClass } = require("java-bridge");

const baseDir = path.join(__dirname, "java_libraries");
const defaultIfcFile = path.join(
  __dirname,
  "..",
  "IFCtoLBD",
  "src",
  "main",
  "resources",
  "Duplex_A.ifc"
);
const ifcFile = process.argv[2] ? path.resolve(process.argv[2]) : defaultIfcFile;
const javaHome = process.env.JAVA_HOME || path.join(__dirname, "..", ".tools", "jdk");
const tmpDir = process.env.TMPDIR || "/tmp/";
const libJvm = path.join(javaHome, "lib", "server", "libjvm.so");

const dependencies = fs
  .readdirSync(baseDir)
  .filter((dependency) => dependency.toLowerCase().endsWith(".jar"))
  .map((dependency) => path.join(baseDir, dependency));

ensureJvm({
  ...(fs.existsSync(libJvm) ? { libPath: libJvm } : {}),
  opts: [`-Djava.io.tmpdir=${tmpDir.endsWith(path.sep) ? tmpDir : tmpDir + path.sep}`],
});
appendClasspath(dependencies);

const IFCtoLBDConverter = importClass(
  "org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter"
);
const converter = new IFCtoLBDConverter("https://example.com/", false, [1]);
const model = converter.convertSync(ifcFile);
const subjects = model.listSubjectsSync().toListSync();

console.log("subjects: " + subjects);
