# Learn IFCtoLBD step by step

IFCtoLBD converts IFC models into RDF triples using Linked Building Data
vocabularies. These guides describe the current 2.52.0 source; downloaded
releases may have different requirements or features.

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
| 1 | Learn about IFC if needed, launch the desktop JAR with Java, then **Read IFC** and **Run** | [Your first conversion](quick-start.md) |
| 2 | Read Turtle and ask questions with SPARQL | [Using the triples](using-triples.md) |
| 3 | Work with RDFLib and call the converter from Python | [Python](python_examples.md) |
| 4 | Use the Java library and Apache Jena | [Java](java_examples.md) |
| 5 | Build the source, find the right module, and extend it | [Development](development.md) |

Windows users can alternatively use the [command-line `.exe`](quick-start.md#windows-alternative-use-the-command-line-exe).

You can stop after any step and use what you have learned. Python and Java are
alternative programming routes; neither requires learning the other first.

Additional resources: [supply-chain and sustainability output](supply-chain-and-sustainability.md),
[project history](history.md), and the
[repository](https://github.com/jyrkioraskari/IFCtoLBD).
