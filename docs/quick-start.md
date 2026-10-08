# Your first conversion: Read IFC, then Run

[Documentation home](README.md) · Next: [use the triples](using-triples.md)

> **Tested release:** This walkthrough is pinned to
> [IFCtoLBD 2.49.0](https://github.com/jyrkioraskari/IFCtoLBD/releases/tag/2.49.0).
> Its Java distribution was checksum-verified and used to convert the bundled
> two-wall model; the resulting graph contains the expected storey. Commands
> and filenames below are for that release.

## Need to know what IFC is?

**Industry Foundation Classes (IFC)** is buildingSMART’s open standard for
sharing building and infrastructure models between applications. It describes
objects such as walls and spaces, their properties, geometry, and relationships.
Start with [buildingSMART’s IFC introduction](https://www.buildingsmart.org/standards/bsi-standards/industry-foundation-classes/).
For detailed entity definitions and examples, use the
[IFC specification documentation](https://ifc43-docs.standards.buildingsmart.org/).
If you already work with IFC, continue below.

You need an IFC model and the desktop application. For a small first model, use
the bundled [two-wall model](../examples/getting-started/building-inventory/model.ifc),
or export an IFC model from your BIM application.

## Download and choose your launch route

Open the [2.49.0 release](https://github.com/jyrkioraskari/IFCtoLBD/releases/tag/2.49.0)
and expand **Assets**. Use `IFCtoLBD-Desktop_for_Windows.zip` for the bundled
Windows application, or `IFCtoLBD.zip` for the Java 21 distribution. GitHub’s
automatically generated “Source code” archives are not application packages.

For the bundled Windows application, extract the ZIP and launch `IFCtoLBD.exe`.
It carries its own Java runtime. Keep the whole application folder together.

### Windows, macOS, or Linux: use the Java .jar

Extract `IFCtoLBD.zip` completely. This route needs Java installed. Check or
install Java 21 as described below, change into the extracted `IFCtoLBD`
directory, then run:

```sh
java -jar IFCtoLBD-Desktop_Java_21.jar
```

Keep the accompanying `IFCtoLBD-Desktop_Java_21_lib` folder next to the JAR.

## Install Java 21 for the JAR route

First open a terminal (PowerShell or Command Prompt on Windows) and check:

```sh
java -version
```

If it reports Java 21 or newer, continue
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
path. You now have RDF, but you can get a useful answer without reading Turtle.
The following checked query step targets development version 2.54.1. Open
**Query**, paste the
[building inventory query](../examples/getting-started/building-inventory/query.rq),
and select **Run query**. For the bundled model the result groups two walls under
`Level 04 - T.O. Fnd. Wall`; compare the complete table with
[expected.txt](../examples/getting-started/building-inventory/expected.txt).

Turtle (`.ttl`) is a convenient first format because it is readable. JSON-LD
(`.jsonld`) is another RDF representation available in the desktop.
It is different from IFC/JSON, which is an input format.

For named graphs, split Turtle output, and ISO 21597 ICDD containers, continue
with the advanced [export formats and ICDD guide](export-formats.md).

## Windows alternative: IFCtoLBDConverter_CLI.exe

`IFCtoLBDConverter_CLI.exe` is a **command-line converter**, not the desktop
interface. For the **Read IFC → Run** interface, use one of the desktop packages
described above.

If you prefer terminal commands, download `IFCtoLBDConverter_CLI.exe` from the
[2.49.0 release](https://github.com/jyrkioraskari/IFCtoLBD/releases/tag/2.49.0),
open PowerShell in its directory, and run its help command:

```powershell
.\IFCtoLBDConverter_CLI.exe --help
```

The native executable needs no separate Java installation. A conversion is:

```powershell
.\IFCtoLBDConverter_CLI.exe --url https://example.com/building/ --target_file output.ttl model.ifc
```

Replace `model.ifc` with your input path. The command reads and converts the
model; there are no **Read IFC** or **Run** buttons. Continue with
[using the generated triples](using-triples.md) once conversion finishes.

## Explore after the first success

The desktop offers a basic workflow and an advanced workflow. Numbered
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
For two more complete workflows, run the
[data-completeness and revision-comparison examples](../examples/getting-started/README.md).
