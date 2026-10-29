# Learn IFCtoLBD step by step

IFCtoLBD converts IFC models into RDF triples using Linked Building Data
vocabularies. The beginner workflow is tested against the published
[2.49.0 release](https://github.com/jyrkioraskari/IFCtoLBD/releases/tag/2.49.0).
Developer and advanced pages describe the 2.56.0 source tree and label features
that are not in that release.

## What can you do?

- **Use the desktop app** to convert IFC to Turtle or JSON-LD, select export
  content, query and validate the output, and preview exported geometry.
- **Use the triples** to explore building information. In your own applications,
  you can use LBD identifiers to link it to assets, products, sensors, or other
  datasets; creating those external links is a separate step from conversion.
- **Summarize products as JSON** with ProductExtractor, using its command-line
  tool or browser-local interface to select element types and inspect results.
- **Use Python, Java, the command line, or the HTTP API** to automate
  conversion and build applications around the output.
- **Extend the source code** with new mappings, vocabularies, validation rules,
  geometry backends, and domain-specific enrichment.

## Choose your starting point

| Step | What you will learn | Guide |
| --- | --- | --- |
| 1 | See how IFC becomes BOT and PROPS data | [Conceptual overview](overview.md) |
| 2 | Launch the desktop app, then **Read IFC** and **Run** | [Your first conversion](quick-start.md) |
| 3 | Browse Turtle visually and ask questions with SPARQL | [Using the triples](using-triples.md) |
| 4 | Walk through exported building geometry in a browser | [LBDBabylon walkthrough](lbd-babylon.md) |
| 5 | Turn building elements, properties, and materials into JSON | [ProductExtractor](../ProductExtractor/README.md) |
| 6 | Work with RDFLib and call the converter from Python | [Python](python_examples.md) |
| 7 | Use the Java library and Apache Jena | [Java](java_examples.md) |
| 8 | Upload IFC and receive RDF through OpenAPI | [`IFCtoLBD_OpenAPI_Node_js`](../IFCtoLBD_OpenAPI_Node_js/README.md) |
| 9 | Connect an AI assistant through MCP | [MCP tutorial](mcp.md) |
| 10 | Build the source, find the right module, and extend it | [Development](development.md) |

Windows users can alternatively use the [`IFCtoLBDConverter_CLI.exe`](quick-start.md#windows-alternative-ifctolbdconverter_cliexe).

You can stop after any step and use what you have learned. Python and Java are
alternative programming routes; neither requires learning the other first.

## Advanced topics

- [Property output levels 1–3](property-levels.md)
- [Export formats, split files, and ICDD](export-formats.md)
- [Supply-chain and sustainability output](supply-chain-and-sustainability.md)
- [Optional identity, external links, provenance, and sensor integration](optional-integration.md) (development source)
- [Traceable graph retrieval, evidence, and validation](traceable-retrieval.md) (development source)
- [IFC materials with BMO](materials-bmo.md)
- [ProductExtractor JSON summaries](../ProductExtractor/README.md)
- [LBDBabylon geometry walkthrough](lbd-babylon.md)
- [Node.js HTTP/OpenAPI service](../IFCtoLBD_OpenAPI_Node_js/README.md)
- [Compatible projects](compatible-projects.md)
- [Glossary](glossary.md)

Additional resources: [project history](history.md) and the [repository](https://github.com/jyrkioraskari/IFCtoLBD).
