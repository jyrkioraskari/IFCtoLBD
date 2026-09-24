# Your first conversion: Read IFC, then Run

[Documentation home](README.md) · Next: [use the triples](using-triples.md)

## Need to know what IFC is?

**Industry Foundation Classes (IFC)** is buildingSMART’s open standard for
sharing building and infrastructure models between applications. It describes
objects such as walls and spaces, their properties, geometry, and relationships.
Start with [buildingSMART’s IFC introduction](https://www.buildingsmart.org/standards/bsi-standards/industry-foundation-classes/).
For detailed entity definitions and examples, use the
[IFC specification documentation](https://ifc43-docs.standards.buildingsmart.org/).
The specification version you read and the schemas supported by your converter
release may differ; check the release notes for supported input versions.
If you already work with IFC, continue below.

You need an IFC model and the desktop application. For a small first model,
download [Duplex_A.ifc](https://github.com/jyrkioraskari/IFCtoLBD/blob/master/IFCtoLBD/src/main/resources/Duplex_A.ifc)
using GitHub’s download button, or export an IFC model from your BIM application.

## Download and choose your launch route

Go to [Releases](https://github.com/jyrkioraskari/IFCtoLBD/releases) and expand
**Assets** for the release you choose. Select the packaged **desktop application**
for your operating system. GitHub’s “Source code” archives are for building the
project. Read the release notes and any instructions included with the package.

### Windows, macOS, or Linux: use the Java .jar

Choose the desktop JAR distribution and extract it completely. This route needs
Java installed on your computer. Check or install Java 21 as described below,
then run the desktop JAR from the extracted application directory:

```sh
java -jar IFCtoLBD-Desktop_Java_21.jar
```

Adapt the filename to the JAR you downloaded. Keep the accompanying library
folder next to it; for the current source build this is
`IFCtoLBD-Desktop_Java_21_lib`. Follow the chosen release’s requirements if its
filenames or required Java version differ.

## Install Java 21 for the JAR route

First open a terminal (PowerShell or Command Prompt on Windows) and check:

```sh
java -version
```

If it reports Java 21 or a newer version compatible with your release, continue
to launching the JAR. If Java is missing or too old:

1. Open [Eclipse Temurin downloads for Java 21](https://adoptium.net/temurin/releases/?version=21).
   Select **version 21**, your operating system, and your processor architecture.
   Choose the **JDK** package; it can both run the application and support the
   developer guides later.
2. Install the package. On Windows x64, download and run the `.msi` installer;
   keep **Add to PATH** and the `.jar` association enabled. Select **Set JAVA_HOME**
   if you also plan to compile the converter. See the
   [official Windows installer guide](https://adoptium.net/installation/windows/).
   On macOS, use the `.pkg` installer when available. For Linux, follow the
   [official installation instructions](https://adoptium.net/installation/) for
   your distribution.
3. Open a **new terminal**, run `java -version` again, and confirm the selected
   Java version is 21 or newer. Then run the desktop JAR using the command above.

If the terminal still reports an older Java, check that its PATH selects the
new installation. Java installation is needed for the JAR route and the Java
programming integration; it is not needed just to read exported Turtle in Python.

## Read IFC

Click **Read IFC** and select your IFC model. Reading prepares the model and
collects its element types and property sets. Wait for the conversion log to
finish reading and for **Run** to become available. This step does not yet
produce the final LBD export.

The application proposes a filename such as `model_LBD.ttl`. Check the displayed
path: a previously used output directory may be remembered. Choose **Output**
if you want to save somewhere else. Use a fresh filename to retain earlier exports.

## Run

For the first conversion, leave settings and filters as they are. Click **Run**
and wait for completion in the conversion log. Locate the output at the displayed
path. Open the `.ttl` file in a text editor: prefixes and statements describe
your building as RDF triples.

Turtle (`.ttl`) is a convenient first format because it is readable. JSON-LD
(`.jsonld`) is another RDF representation available in the current desktop.
It is different from IFC/JSON, which is an input format.

ICDD (`.icdd`) creates an ISO 21597-1 ZIP container. It includes the original
IFC file and separate Turtle documents for BOT topology (including the IFC
attributes of spatial resources), Product/BEO building elements (including their
IFC attributes), geometry, and PROPS/OPM property and quantity sets. Geometry
representations and the links from elements to those representations are assigned
to `lbd/geometry.ttl`; the properties document contains only property-set and
quantity-set content. The four RDF payload documents are physically disjoint: a
triple is written to only one of them. `Index.rdf`
describes each document. `Ontology resources/bot.ttl` is a bundled offline copy
of BOT 0.3.2, while `Ontology resources/props.ttl` contains the generated
ontology declarations for the property predicates actually used in the payload;
the standard `Ontology resources`, `Payload documents`, and `Payload triples`
folders are present in the package.

Selecting separate building-element and property files for an ordinary Turtle
export uses the topology, product, and property partitions: the main file contains topology,
`_building_elements.ttl` contains product data, and `_element_properties.ttl`
contains property and quantity-set data, without triples repeated across files.
The dedicated `geometry.ttl` document is specific to ICDD packaging.

## Windows alternative: IFCtoLBDConverter_CLI.exe

The Windows `.exe` offered in releases is a **command-line converter**. There
is no Windows desktop `.exe`. For the **Read IFC → Run** interface, use the
Java desktop JAR described above.

If you prefer terminal commands, download the Windows command-line `IFCtoLBDConverter_CLI.exe`
from [Releases](https://github.com/jyrkioraskari/IFCtoLBD/releases). Extract the
complete package if zipped, open PowerShell in its directory, and run its help
command:

```powershell
.\IFCtoLBDConverter_CLI.exe --help
```

For a native executable, no separate Java installation is needed. Follow the
release’s instructions and use its help output to check supported options.
For releases supporting the current CLI options, a conversion looks like this:

```powershell
.\IFCtoLBDConverter_CLI.exe --url https://example.com/building/ --target_file output.ttl model.ifc
```

Replace `model.ifc` with your input path. The command reads and converts the
model; there are no **Read IFC** or **Run** buttons. Continue with
[using the generated triples](using-triples.md) once conversion finishes.

Explicit `IfcRelSpaceBoundary` relationships are exported as BOT interfaces by
default and do not require geometry. Use `--ifc-space-boundaries=false` to
disable them. Bounding-box interface inference is a separate, opt-in feature:

```powershell
.\IFCtoLBDConverter_CLI.exe --infer-geometry-interfaces model.ifc
```

Enabling inference also enables its geometry prerequisite. Those results are
marked as candidate interfaces. Use `--ifc-zones` to export
`IfcZone` resources and their `IfcRelAssignsToGroup` memberships. The legacy
`--hasInterfaces` option remains an alias for bounding-box inference.

## Explore after the first success

The current desktop offers a basic workflow and an advanced workflow. Numbered
buttons describe the full workflow; the essential first actions are **Read IFC**
and **Run**. In the advanced workflow, **Settings** controls export options,
**Filters** selects element types and property sets, and **Output** chooses the
file location. Saved preferences can affect later conversions.

Where available, **Query** opens SPARQL queries, **Validate** checks RDF against
SHACL shapes, and **Geometry** previews exported geometry. Geometry must be
enabled for a geometry preview. Validation reports on the selected RDF rules;
it does not certify the original IFC model. **Copy command line** helps you
repeat the desktop settings in automated conversions.

## If something stops you

| Symptom | What to try |
| --- | --- |
| Double-clicking the JAR does nothing | Run `java -jar` in a terminal to see the error. Check `java -version` against the release requirements. |
| JavaFX or another library is missing | Extract the whole distribution and keep the library folder next to the JAR. |
| Run stays disabled | Wait for reading to finish; inspect the conversion log for an input or schema error. |
| You cannot find the output | Check the displayed output path and the final log message. |
| “Java heap space” | Close the app and restart with a larger heap, for example `java -Xmx4g -jar IFCtoLBD-Desktop_Java_21.jar`, if your computer has enough available memory. |
| A large model fails during conversion | Check free disk space as well as memory; the converter uses temporary disk-backed storage. |

If the error persists, report the release, operating system, Java version, and
conversion log in a [GitHub issue](https://github.com/jyrkioraskari/IFCtoLBD/issues).

Next, learn [what those triples mean and how to query them](using-triples.md).
