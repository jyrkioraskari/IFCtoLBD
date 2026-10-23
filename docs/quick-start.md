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

If it reports Java 21 or newer, verify that the vendor identifies the installed
build as fixed for
[CVE-2026-91403](https://www.oracle.com/security-alerts/alert-cve-2026-91403.html)
before launching the JAR. Java 21.0.13 and earlier builds are not safe unless
the vendor has backported the fix. If Java is missing, too old, or unpatched:

1. Install a Java 21 build whose vendor explicitly lists the CVE fix. Known
   fixed builds include Azul Zulu 21.54.107 (OpenJDK 21.0.13+9) and Oracle JDK
   21.0.13.0.2. Do not install an unpatched base 21.0.13 build. Choose the
   **JDK** package; it can both run the application and support the developer
   guides later.
2. Install the package. On Windows x64, download and run the `.msi` installer;
   keep **Add to PATH** and the `.jar` association enabled. Select **Set JAVA_HOME**
   if you also plan to compile the converter. See the
   installer documentation supplied by your selected JDK vendor. On macOS, use
   the vendor's installer when available. On Linux, use the vendor package or
   distribution package that explicitly contains the CVE fix.
3. Open a **new terminal**, run `java -version` again, and confirm the selected
   Java version is 21 or newer and is a fixed vendor build. Then run the desktop
   JAR using the command above.

If the terminal still reports an older Java, check that its PATH selects the
new installation. Java installation is needed for the JAR route and the Java
programming integration; it is not needed just to read exported Turtle in Python.

## Read IFC

Click **Read IFC** and select your IFC model. Reading prepares the model and
collects its element types and property sets. Wait for the conversion log to
finish reading and for **Run** to become available. In development version
2.55.0, the **Model Summary** then shows the detected IFC schema, file size,
element and property-set totals, storeys, spaces, geometry availability, major
element types, and any model warnings. This step does not yet produce the final
LBD export.

The application proposes a filename such as `model_LBD.ttl`. Check the displayed
path: a previously used output directory may be remembered. Choose **Output**
if you want to save somewhere else. Use a fresh filename to retain earlier exports.
The **Proposed output** preview shows full paths. When settings produce multiple
files, it lists every path and the number of output files.

## Run

For the first conversion, leave settings and filters as they are. Click **Run**
and wait for completion in the conversion log. Locate the output at the displayed
path. You now have RDF, but you can get a useful answer without reading Turtle.
The following checked query step targets development version 2.55.0. Open
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

The converter uses fast in-memory staging by default. For an IFC model that is
too large for the configured JVM heap, add `--staging=TDB2` to use a temporary
disk-backed graph instead.

Replace `model.ifc` with your input path. The command reads and converts the
model; there are no **Read IFC** or **Run** buttons. Continue with
[using the generated triples](using-triples.md) once conversion finishes.

## Explore after the first success

To browse the generated Turtle as an interactive building graph, open it in
[LBD Browser](https://github.com/jyrkioraskari/LBD-browser). The browser provides
focused views of spatial topology, geometry, and properties and processes the
selected file locally in your web browser.

To turn the same output into application-friendly JSON, use
[ProductExtractor](../ProductExtractor/README.md). It creates one summary per
building element with IFC attributes and property-set values. It can also show
materials and ordered layers when you convert with the optional
[`materials-bmo` profile](materials-bmo.md), and includes a local web interface
for filtering and inspecting the summaries without writing code.

The desktop offers a basic workflow and an advanced workflow. Numbered
buttons describe the full workflow; the essential first actions are **Read IFC**
and **Run**. In the advanced workflow, **Settings** controls export options,
**Filters** selects element types and property sets, and **Output** chooses the
file location. Saved preferences can affect later conversions.

In **Settings**, choose a conversion preset to fill the advanced options. The
built-in presets are **Lightweight topology**, **Asset and properties**,
**Geometry preview**, **Full preservation**, and **ICDD package**. You can edit
the options after applying one. **Save** stores the current options under a
name (or updates the selected saved preset); **Duplicate** copies the selected
preset. **Import** and **Export** exchange `.properties` preset files. **Delete**
removes a saved preset; built-in presets stay available. Presets include the
output format and, for saved presets, the Project URI. They do not include
the IFC input path, output path, or model-specific element and property-set filters.

### Project URI and identity

The source-development desktop has one **Project URI** field in
**Settings**. Enter a persistent absolute HTTPS IRI that you control, for
example `https://example.org/projects/campus-a/`. The desktop uses this one
URI as the namespace for generated project-resource identifiers; there is no separate
dataset Base URI field.

Choose **Identity policy** under **Product / BOT**:

- **Standard behaviour** retains the historical identifier layout within the
  project namespace.
- **Stable project identity** identifies mapped IFC objects from the project
  namespace and their IFC GlobalId, for example
  `https://example.org/projects/campus-a/ifc-globalid/3x...`. Keep the namespace
  unchanged between revisions of the same project. Use different namespaces
  for unrelated projects, even if their IFC files happen to contain equal
  GlobalIds.

Changing the Project URI after **Read IFC** marks the loaded model for a
safe reread before conversion. New saved presets store `projectNamespace`.
Older presets with a separate `baseUri` remain loadable and migrate that value
into the single Project URI field.

For an optional external-link mapping, open **Filters** and use **Browse** in
the **External-link mapping** section at the bottom to select a `.json` or
`.csv` file. The selected path is included in saved presets and in the
command-line preview.

Where available, **Query** opens SPARQL queries, **Validate** checks RDF against
SHACL shapes, and **Geometry** previews exported geometry. Geometry must be
enabled for a geometry preview. Validation reports on the selected RDF rules;
it does not certify the original IFC model. SHACL shape files added with
**Load SHACL** reopen when you restart the app. Removing an added shape or choosing
**Restore defaults** updates that saved list. If a file has moved, load it again
from its new location. **Copy command line** helps you
repeat the desktop settings in automated conversions.

### Development-only external geometry options

The source-development desktop for 2.55.0 adds three optional controls at the
bottom of **Settings → Geometry**: **Geometry output**, **Geometry directory**,
and **Geometry base IRI**. These controls are not in the release-pinned 2.49.0
packages described at the top of this page.

- **Embedded OBJ (existing)** is the backward-compatible default and leaves the directory fields disabled.
- **External OBJ**, **External GLB**, and **External glTF** write content-addressed mesh files separately
  from RDF. Selecting one also enables geometry conversion.
- **Geometry directory** is the local directory where artifacts are written.
- **Geometry base IRI** is the public or package-relative IRI used in RDF; it is not inferred from the
  local directory.

Both external fields are required for an external format. These options are included in saved/imported
desktop presets, while older presets automatically retain embedded OBJ. GLTF output is self-contained;
the desktop does not create sibling `.bin` or texture files.

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
