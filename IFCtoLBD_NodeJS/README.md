# IFCtoLBD Node.js Example

This package shows how to call the IFCtoLBD Java converter from Node.js. The `IFCtoLBD.js` wrapper loads the IFCtoLBD and dependency JAR files from `java_libraries/`, starts a JVM through `java-bridge`, converts IFC files, and exposes small JavaScript helper methods.

## Requirements

- Node.js 18 or newer
- npm
- Java runtime or JDK
- IFCtoLBD JAR files in `java_libraries/`

The script first uses `JAVA_HOME` when it is set. If `JAVA_HOME` is not set, it falls back to the repository-local JDK at `../.tools/jdk`.

## Install

```sh
npm install
```

This installs `java-bridge`, which provides prebuilt native binaries for supported platforms. The older `java` package is not used because it does not compile on current Node.js releases.

### Windows 11 cleanup after the old install

If `npm install` fails in `node_modules\java` with `node-gyp rebuild`, the directory still has the old dependency tree or the old dependency in `package.json`. The current package must depend on `java-bridge`, not `java`.

First update `package.json`:

```bat
cd C:\jo\IFCtoLBD_NodeJS
npm pkg delete dependencies.java
npm pkg set dependencies.java-bridge="^2.8.1"
```

Then clean and reinstall. In `cmd.exe`:

```bat
cd C:\jo\IFCtoLBD_NodeJS
rmdir /s /q node_modules
del package-lock.json
npm install
```

In PowerShell:

```powershell
cd C:\jo\IFCtoLBD_NodeJS
Remove-Item -Recurse -Force node_modules
Remove-Item -Force package-lock.json
npm install
```

If Windows reports `EPERM: operation not permitted`, close editors, terminals, file explorers, Java processes, and antivirus scans that may be holding files under `node_modules`. Then run the delete command again from a new terminal. If it still fails, open the terminal as Administrator and run:

```bat
cd C:\jo\IFCtoLBD_NodeJS
rd /s /q node_modules
del package-lock.json
npm cache verify
npm install
```

Only delete `package-lock.json` when it still contains references to the old `java` package. A clean lock file for this project contains `java-bridge`, not `node_modules/java`.

After running the `npm pkg` commands, check that `package.json` contains:

```json
"dependencies": {
  "java-bridge": "^2.8.1"
}
```

It must not contain:

```json
"java": "^0.14.0"
```

With `java-bridge`, Visual Studio Build Tools are not required for the normal Windows x64 install because the package provides prebuilt binaries.

## Run

Run the basic example with the default sample IFC file:

```sh
npm start
```

The default input is:

```text
../IFCtoLBD/src/main/resources/Duplex_A.ifc
```

Run with a specific IFC file:

```sh
node example.js /path/to/model.ifc
```

The output is a list of RDF subjects from the converted model.

Run the additional examples:

```sh
npm run example1
npm run example2
npm run example3
```

`example1.js` sets a custom base URI and prints a subject count. `example2.js` shows direct access to the converted Jena model and prints the first subjects. `example3.js` serializes the converted Duplex model to Turtle, loads it into an in-memory RDF store, and queries it with Comunica.

## Wrapper API

Import the wrapper from `IFCtoLBD.js`:

```js
const { IFCtoLBD } = require("./IFCtoLBD");

const converter = new IFCtoLBD({
  baseUri: "https://example.com/building/",
  hasGeometry: false,
  levels: [1],
});

const subjects = converter.convertToSubjects("/path/to/model.ifc");
console.log(subjects);
```

The constructor accepts:

- `baseUri`: base URI used by IFCtoLBD for generated resources.
- `hasGeometry`: passed to the IFCtoLBD Java converter constructor.
- `levels`: conversion levels passed to the Java converter.
- `javaHome`: optional JDK/JRE path. Defaults to `JAVA_HOME`, then `../.tools/jdk`.
- `jarDir`: optional directory containing dependency JAR files. Defaults to `java_libraries/`.
- `tmpDir`: optional Java temp directory. Defaults to `TMPDIR`, then the operating system temp directory.
- `jvmOptions`: extra JVM options, for example `["-Xmx4G"]`.

Useful methods:

- `convert(ifcFile)`: converts an IFC file and returns the Java RDF model object.
- `serializeModel(model, format)`: serializes a Java RDF model. The default format used by the examples is `TURTLE`.
- `convertToTurtle(ifcFile)`: converts an IFC file and returns Turtle text.
- `listSubjects(model)`: returns the subjects from a converted model.
- `convertToSubjects(ifcFile)`: converts an IFC file and returns subjects in one call.

## Comunica In-Memory Example

`example3.js` demonstrates querying converted IFC data without writing RDF output files:

```sh
npm run example3
```

The example:

- Converts the default Duplex IFC file with `IFCtoLBD`.
- Serializes the Java RDF model to Turtle.
- Parses Turtle with `n3`.
- Stores the quads in an in-memory `N3.Store`.
- Runs a SPARQL query with `@comunica/query-sparql`.

## Test

```sh
npm test
```

The test currently checks that the wrapper and example files are valid JavaScript syntax.

## Java And Classpath

All `.jar` files in `java_libraries/` are added to the JVM classpath. Files that are not JARs, such as Windows `:Zone.Identifier` metadata files, are ignored.

If Java is installed outside the repository-local JDK, set `JAVA_HOME` before running:

```sh
export JAVA_HOME=/path/to/jdk
npm start
```

The wrapper also sets `java.io.tmpdir` with a trailing path separator. By default it creates a per-process temp directory under the operating system temp directory. This avoids temporary-file path issues in the IFC conversion step and prevents separate example runs from writing to the same converter temp file.

## Project Files

- `IFCtoLBD.js` wraps JVM startup, classpath loading, converter creation, and common helper methods.
- `example.js` is the shortest conversion example.
- `example1.js` shows custom converter options and a subject count.
- `example2.js` shows direct access to the converted Java model.
- `example3.js` shows Comunica querying over an in-memory RDF store.
- `java_libraries/` contains IFCtoLBD and Java dependency JAR files.
- `package.json` defines install, run, and syntax-check commands.
- `package-lock.json` locks the Node dependency versions.

## Troubleshooting

If installation fails with native compiler or V8/NAN errors, make sure the package uses `java-bridge`, not the obsolete `java` dependency.

If installation fails with `node_modules\java`, `node-gyp rebuild`, or `Could not find any Visual Studio installation to use`, run `npm pkg delete dependencies.java` and `npm pkg set dependencies.java-bridge="^2.8.1"`, then remove `node_modules` and reinstall.

If npm still tries to build `node_modules\java` after that, search both `package.json` and `package-lock.json` for `"java"` and replace the old dependency with `java-bridge`. The current install should create `node_modules\java-bridge`, not `node_modules\java`.

If `npm ls java` reports `invalid: "^0.14.0" from the root project`, the root `package.json` still contains the old dependency. Update `package.json` first, then delete `node_modules` and reinstall.

If runtime fails with `Java is not installed or not in the system PATH`, set `JAVA_HOME` to a JDK or JRE directory, or make sure `../.tools/jdk` exists.

If runtime fails with missing Java classes, check that `java_libraries/` contains the IFCtoLBD converter JAR and all required dependency JARs.
