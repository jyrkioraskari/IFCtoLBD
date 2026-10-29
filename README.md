# IFCtoLBD

Development version 2.56.0 · Stable release used by the beginner guide: [2.49.0](https://github.com/jyrkioraskari/IFCtoLBD/releases/tag/2.49.0) · Free for all of us, forever.

IFCtoLBD turns an IFC building model into **Linked Building Data (LBD)**: small
subject–relationship–value facts, called RDF triples, that describe buildings,
spaces, elements, and their properties. You can query these facts and use LBD in
your own applications to link building
information to other data. Development version 2.56.0 supports IFC STEP,
IFC/XML, and IFC/JSON.

**Audience and prerequisites.** The desktop quick start is for BIM practitioners,
data engineers, and researchers and requires no programming. Bring an IFC file;
the Java distribution also requires Java 21. **New to IFC?** Read the short
introduction below. **New to RDF or SPARQL?** Start with the
[conceptual overview](docs/overview.md), then follow
[using the triples](docs/using-triples.md). Developers need Java 21 and Maven.

> **Try IFCtoLBD in ten minutes.** The
> [checked example bundle](examples/getting-started/README.md) includes small IFC
> models, saved configurations, SPARQL questions, and expected answers for a
> building inventory, a completeness check, and a revision comparison. The
> bundle targets development version 2.56.0.

## What can you do with IFCtoLBD?

- **Convert an IFC model into linked data** with the desktop app and save it as
  Turtle, JSON-LD, or an ICDD package containing the original IFC and separate
  Linked Building Data submodels.
- **Choose what to export:** select element types, property sets, and options
  for properties, units, and geometry.
- **Explore your building:** query the data with SPARQL, validate it with SHACL,
  and preview exported geometry in the desktop app.
- **Summarize building products as JSON:** use ProductExtractor to collect each
  element's IFC attributes, property sets, and available material layers from
  an exported Turtle or TriG file.
- **Prepare building data for linking:** the generated LBD identifiers let you
  create links to asset records, products, sensors, and other datasets in your
  own applications. Development source also has an [optional, offline external-link workflow](docs/optional-integration.md);
  released/default conversion does not add those links.
- **Automate your work** with the command-line converter, Python examples, the
  Java library, or the modern Node.js HTTP API.
- **Build your own tools and extensions** from the source: add mappings,
  vocabularies, validation rules, geometry backends, or domain-specific enrichment.

The guides below show you where to start. The beginner workflow is pinned to
release 2.49.0; source-only 2.56.0 features are identified separately.

## The pieces of IFCtoLBD

| Piece | Use it when | Artifact or entry point |
| --- | --- | --- |
| Desktop app | You want the **Read IFC → Run** interface | `IFCtoLBD.zip` or `IFCtoLBD-Desktop_for_Windows.zip` in release 2.49.0 |
| CLI | You want scripted conversion on Windows | `IFCtoLBDConverter_CLI.exe` in release 2.49.0 |
| Java library | You are embedding conversion in a JVM application | `org.linkedbuildingdata:ifc-to-lbd:<version>` / `ifc-to-lbd-<version>.jar` |
| HTTP/OpenAPI service | You want to upload IFC and receive RDF over HTTP | `IFCtoLBD_OpenAPI_Node_js`; Node.js 20+ and Java 21, built from source |
| MCP server | You want an AI tool to inspect or convert IFC | `IFCtoLBD_MCP`; build from source because release 2.49.0 has no prebuilt MCP server |
| OneClick | You want one selected Turtle export from the web app, a separate Node CLI, or an MCP tool | `OneClick/cli.mjs` and MCP `export_oneclick_lbd`; source checkout with Python, IfcOpenShell, and Shapely |
| ProductExtractor | You want element-oriented JSON or a local summary interface for LBD output | `ProductExtractor`; a standalone Python 3.10+ tool installed from source |
| LBD Babylon viewer | You want to explore IFCtoLBD Turtle geometry in a web browser | `LBDBabylon`; a local Vite/Babylon.js application built from source |

The desktop and CLI names above are the exact names from release 2.49.0;
source-built entries describe the development tree. New releases follow the
[artifact naming policy](docs/development.md#artifact-names).

The [OneClick command line](OneClick/README.md#command-line-export) and
[MCP tool](IFCtoLBD_MCP/README.md#oneclick-export) are separate opt-in paths.
They use OneClick's Turtle format; the Java CLI and the existing MCP
`convert_ifc_to_lbd` tool keep their conversion behavior.

## New to IFC? Start with the standard

**Industry Foundation Classes (IFC)** is an open, vendor-neutral standard from
buildingSMART for exchanging building and infrastructure information. An IFC
model can describe geometry, elements, spaces, properties, and relationships.
Read [buildingSMART’s introduction to IFC](https://www.buildingsmart.org/standards/bsi-standards/industry-foundation-classes/)
if you need the background. For technical definitions, follow its links to the
IFC specifications. You can skip this introduction if you already use IFC.

## Start here: convert your IFC without programming

You need an IFC model and the desktop application.

1. Open the [2.49.0 release](https://github.com/jyrkioraskari/IFCtoLBD/releases/tag/2.49.0), expand
   **Assets**, and choose `IFCtoLBD.zip` for the Java 21 distribution or
   `IFCtoLBD-Desktop_for_Windows.zip` for the bundled Windows application. The
   bundled Windows application includes Java. The Java distribution needs
   [Java 21](docs/quick-start.md#install-java-21-for-the-jar-route).

   <img src="Screen.png" alt="Annotated IFCtoLBD desktop application highlighting Read IFC and Run" width="800">

2. Start the desktop application. For a result you can check, use the small
   [two-wall example model](examples/getting-started/building-inventory/model.ifc).
   Click **Read IFC**, choose the model, and
   wait for it to finish reading. Development version 2.56.0 also shows a model
   summary with the detected schema, model contents, geometry availability, and
   warnings. The application proposes an output path.
3. Check that output path, then click **Run**. For your first conversion, keep
   the existing settings and filters. Wait for the conversion log to report completion.
4. In development version 2.56.0, open **Query**, paste the saved
   [building inventory query](examples/getting-started/building-inventory/query.rq),
   and click **Run query**. The useful result is immediate: the storey contains
   two walls. Compare it with the
   [expected result](examples/getting-started/building-inventory/expected.txt).


[Follow the release-pinned desktop quick start](docs/quick-start.md) for launch
commands, output choices, and help if the application does not start.

**Windows command-line alternative:** `IFCtoLBDConverter_CLI.exe` is the Windows
command-line converter. Use it from PowerShell or Command Prompt;
[see the Windows CLI instructions](docs/quick-start.md#windows-alternative-ifctolbdconverter_cliexe).
The same functionality is available as a Java app for Linux and macOS users.

## View LBD geometry in a browser

`LBDBabylon` is a browser-local viewer for Turtle created by IFCtoLBD. It can
open an **LBD Turtle file**, render embedded OBJ geometry or explicitly linked
OBJ/GLB/self-contained glTF artifacts while preserving `omg:hasGeometry`
element associations, and provides orbit, grounded avatar walk,
fly, and direct floor-assisted navigation. Walk uses metre-aware body dimensions, smooth
acceleration and braking, gravity, ground snapping, safe-position recovery, and
semantic collision roles. A grass-green ground plane around the model prevents
the walker from falling while exploring the exterior. Spaces, openings, voids, zones, and unknown helper
geometry remain visible and selectable but do not block movement. If a Turtle
file has no renderable geometry or recognized walkable floor, the viewer reports
that result rather than inventing a model.

The source-development desktop exposes **Embedded OBJ (existing)**, **External OBJ**, **External GLB**,
and **External glTF** under **Settings → Geometry**. Released 2.49.0 desktop packages retain their documented
OBJ workflow; see the [optional integration guide](docs/optional-integration.md#optional-geometry-artifact-formats)
for development CLI, Java, MCP, and desktop usage.

<img src="B1.png" alt="LBD Babylon viewer showing an IFCtoLBD building model in orbit mode" width="800">

<img src="B2.png" alt="LBD Babylon viewer showing the interior of a building in walk mode" width="800">

Run it from the repository root:

```sh
npm --prefix LBDBabylon ci
npm --prefix LBDBabylon run dev
```

Open the Vite URL shown in the terminal, then choose **Upload LBD Turtle file**
or drop a `.ttl` file into the viewport. Use an IFCtoLBD conversion with
geometry enabled (`--hasGeometry`) to produce embedded renderable OBJ geometry,
or opt into external OBJ, GLB, or self-contained glTF artifacts. External files
are loaded only after an explicit local-file/package selection; automatic remote
fetching remains disabled. See the
[LBDBabylon walkthrough](docs/lbd-babylon.md) for controls, semantic collision
rules, walking behaviour, troubleshooting, and limitations. The
[component README](LBDBabylon/README.md) records the exact RDF predicates and
implementation contract.

## Summarize building products as JSON

`ProductExtractor` turns IFCtoLBD Turtle or TriG output into a JSON array with
one summary per `bot:Element`. Each summary includes element identity and type,
exported IFC attributes, and property sets. When the input was converted with
the optional `materials-bmo` profile, it also includes direct materials and
ordered material layers with their thicknesses.

Install and run it from the repository root with Python 3.10 or newer:

```sh
python -m pip install -e ProductExtractor
product-extractor --input model.ttl --output elements.json
```

Filter the result with `--types Wall,Slab`, inspect the available types with
`--list-types`, or start the browser-local interface:

```sh
product-extractor --web --input model.ttl
```

Then open <http://127.0.0.1:8000/>. See the
[ProductExtractor guide](ProductExtractor/README.md) for Windows setup, input
formats, the JSON schema, library use, and the local HTTP API.


## What would you like to do next?

| Your goal | Guide |
| --- | --- |
| Understand LBD and use the generated triples | [Conceptual overview](docs/overview.md), then [your first queries](docs/using-triples.md) |
| Browse the generated LBD as an interactive graph | [LBD Browser](https://github.com/jyrkioraskari/LBD-browser) |
| Turn elements, properties, and materials into JSON | [ProductExtractor guide](ProductExtractor/README.md) |
| Read the output or automate conversion in Python | [Python guide](docs/python_examples.md) |
| Embed the converter and query its output in Java | [Java guide](docs/java_examples.md) |
| Convert uploaded IFC models through an HTTP/OpenAPI service | [`IFCtoLBD_OpenAPI_Node_js` guide](IFCtoLBD_OpenAPI_Node_js/README.md) |
| Use AI to inspect, query, validate, and convert IFC models | [MCP tutorial](docs/mcp.md) — build from source; release 2.49.0 has no prebuilt MCP package |
| Compile the converter, understand the subprojects, or extend it | [Build and development guide](docs/development.md) |
| Explore classification, product identity, GS1, or EPD output | [Supply-chain and sustainability](docs/supply-chain-and-sustainability.md) |
| Preserve project identity and supply reviewed external links | [Optional integration workflows](docs/optional-integration.md) (development source) |
| Retrieve elements with supporting triples and optional evidence | [Traceable graph retrieval](docs/traceable-retrieval.md) (development source) |

Start with the output you have just created; programming and compiling can come
later. LBD uses shared vocabularies such as the
[Building Topology Ontology (BOT)](https://w3c-lbd-cg.github.io/bot/).
The [W3C Linked Building Data Community Group](https://www.w3.org/community/lbd/)
provides further background and community resources.

Browse the [documentation website](https://jyrkioraskari.github.io/IFCtoLBD/#/)
or the [documentation index](docs/README.md). Earlier announcements are in [project history](docs/history.md).

## Contributors and citation authors

The citation authors, in the same order as `CITATION.cff`, are Jyrki Oraskari,
Mathias Bonduel, Kris McGlinn, Pieter Pauwels, Freddy Priyatna, Anna Wagner,
Ville Kukkonen, Simon Steyskaland, Joel Lehtonen, and Maxime Lefrançois.
Additional contributions were made by Lewis John McGibbney. Thanks also to
Vladimir Alexiev, Kathrin Dentler, and Lukas Kirner for their valuable insights.



## License

This project is released under the open source [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## How to cite

```
@misc{jyrki\_oraskari\_2026\_07,
 author       = {Jyrki Oraskari and
                  Mathias Bonduel and
                  Kris McGlinn and
                  Pieter Pauwels and
                  Freddy Priyatna and
                  Anna Wagner and
                  Ville Kukkonen and
                  Simon Steyskaland and
                  Joel Lehtonen and
                  Maxime Lefrançois},
  title        = {{IFCtoLBD v 2.56.0}},
  month        = 07,
  year         = 2026,
  publisher    = {GitHub},
  version      = {2.56.0},
  url          = {https://github.com/jyrkioraskari/IFCtoLBD}
}

```



## Acknowledgements

The research was partly funded by the EU through the H2020 project
[BIM4REN](https://dc.rwth-aachen.de/de/forschung/bim4ren).
