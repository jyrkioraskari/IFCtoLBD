# Learn IFCtoLBD step by step

IFCtoLBD converts IFC models into RDF triples using Linked Building Data
vocabularies. The beginner workflow is tested against the published
[2.49.0 release](https://github.com/jyrkioraskari/IFCtoLBD/releases/tag/2.49.0).
Developer and advanced pages describe the 2.54.1 source tree and label features
that are not in that release.

## What can you do?

- **Use the desktop app** to convert IFC to Turtle or JSON-LD, select export
  content, query and validate the output, and preview exported geometry.
- **Use the triples** to explore building information. In your own applications,
  you can use LBD identifiers to link it to assets, products, sensors, or other
  datasets; creating those external links is a separate step from conversion.
- **Use Python, Java, or the command line** to automate conversion and build
  applications around the output.
- **Extend the source code** with new mappings, vocabularies, validation rules,
  geometry backends, and domain-specific enrichment.

## Choose your starting point

| Step | What you will learn | Guide |
| --- | --- | --- |
| 1 | See how IFC becomes BOT and PROPS data | [Conceptual overview](overview.md) |
| 2 | Launch the desktop app, then **Read IFC** and **Run** | [Your first conversion](quick-start.md) |
| 3 | Read Turtle and ask questions with SPARQL | [Using the triples](using-triples.md) |
| 4 | Work with RDFLib and call the converter from Python | [Python](python_examples.md) |
| 5 | Use the Java library and Apache Jena | [Java](java_examples.md) |
| 6 | Connect an AI assistant through MCP | [MCP tutorial](mcp.md) |
| 7 | Build the source, find the right module, and extend it | [Development](development.md) |

Windows users can alternatively use the [`IFCtoLBDConverter_CLI.exe`](quick-start.md#windows-alternative-ifctolbdconverter_cliexe).

You can stop after any step and use what you have learned. Python and Java are
alternative programming routes; neither requires learning the other first.

## Advanced topics

- [Property output levels 1–3](property-levels.md)
- [Export formats, split files, and ICDD](export-formats.md)
- [Supply-chain and sustainability output](supply-chain-and-sustainability.md)
- [Compatible projects](compatible-projects.md)
- [Glossary](glossary.md)

Additional resources: [project history](history.md) and the [repository](https://github.com/jyrkioraskari/IFCtoLBD).
