# Python: use the output, then automate conversion

[Documentation home](README.md) · Background: [using triples](using-triples.md)

## First, query an exported file with RDFLib

If you have already converted an IFC in the desktop, you can work entirely in
Python with the resulting Turtle file. This step needs no JVM or converter wrapper.

```sh
python -m pip install rdflib
```

Save the following as `query_lbd.py` beside `model_LBD.ttl`, adapting the filename:

```python
from rdflib import Graph

graph = Graph().parse("model_LBD.ttl", format="turtle")
print(f"Loaded {len(graph)} triples")

query = """
PREFIX bot: <https://w3id.org/bot#>
SELECT ?building ?storey WHERE {
    ?building a bot:Building ; bot:hasStorey ?storey .
}
"""
for building, storey in graph.query(query):
    print(building, storey)
```

Run `python query_lbd.py`. Each row gives a building/storey relationship.
Use the [discovery queries](using-triples.md) if your model has no matching rows.
You can also iterate `for subject, predicate, obj in graph` to inspect triples,
and use `graph.serialize(destination="copy.ttl", format="turtle")` to save a graph.

## Then, call the Java converter from Python

The Python integration uses **JPype** to call the Java converter in the same
process. It is a wrapper around the Java implementation. You need a compatible
Java installation ([install Java 21 if needed](quick-start.md#install-java-21-for-the-jar-route)), JPype, the converter
JAR and its dependencies, and the wrapper from this checkout.

```sh
python -m pip install JPype1 rdflib
```

The wrapper lives in
[`IFCtoLBD_Python/IFCtoLBD_wrapper`](https://github.com/jyrkioraskari/IFCtoLBD/tree/master/IFCtoLBD_Python/IFCtoLBD_wrapper).
Run your script from `IFCtoLBD_Python` so Python can import it, or make that folder
available on your Python import path. The wrapper is not installed by the
`pip` command above.

Choose **one** classpath setup:

- With a released library distribution, keep the converter and all matching
  dependency JARs together and pass their absolute directory wildcard to
  `start_jvm`, for example `classpath=["/absolute/path/to/jars/*"]`.
- With this source checkout, follow the [build guide](development.md) and run
  `mvn -pl IFCtoLBD -am package -DskipTests` from the repository root. This creates
  the bundled `IFCtoLBD_MCP/lib/ifctolbd-converter.jar` used below.

Save this script in `IFCtoLBD_Python` and run it from that directory:

```python
from pathlib import Path
import jpype
from rdflib import Graph
from IFCtoLBD_wrapper import (
    ConversionProperties, IFCtoLBDConverter, start_jvm, shutdown_jvm,
)

repo = Path.cwd().parent
jar = repo / "IFCtoLBD_MCP/lib/ifctolbd-converter.jar"
ifc_file = repo / "IFCtoLBD/src/main/resources/Duplex_A.ifc"
start_jvm(classpath=[str(jar.resolve())])  # Set the classpath before creating wrappers.
converter = None
try:
    properties = ConversionProperties()
    properties.setHasGeometry(False)
    converter = IFCtoLBDConverter("https://example.com/building/", 1)
    model = converter.convert(str(ifc_file.resolve()), properties)
    if model is None:
        raise RuntimeError("Conversion failed; inspect the converter messages")
    writer = jpype.JClass("java.io.StringWriter")()
    model.write(writer, "TTL")
    graph = Graph().parse(data=str(writer.toString()), format="turtle")
    print(f"Converted {len(graph)} triples")
    graph.serialize(destination="output.ttl", format="turtle")
finally:
    if converter is not None:
        converter.close()
    shutdown_jvm()
```

The Java model belongs to the converter: read or serialize it before closing
the converter. The RDFLib graph above is an independent Python copy. Start the
JVM once per process and close all converters before shutting it down; JPype
does not support restarting a JVM after shutdown in that process.

The wrapper’s default classpath is `IFCtoLBD_wrapper/jars/*`. Passing an explicit
absolute path avoids dependence on that default and helps when troubleshooting
classpath problems. A “class not found” error usually means the converter JAR
or a dependency is missing. Do not mix libraries from different releases.

## Explore the examples

The runnable examples are in
[`IFCtoLBD_Python`](https://github.com/jyrkioraskari/IFCtoLBD/tree/master/IFCtoLBD_Python).
Check their input filenames and classpath setup before running them.

| Example | What to explore |
| --- | --- |
| `IFCtoLBD_list_subjects_of_triples.py` | Iterate Java statements |
| `IFCtoLBD_RDFLibTurtle.py` | Transfer Turtle into RDFLib |
| `IFCtoLBD_SPARQL.py` | Query the Java model with SPARQL |
| `IFCtoLBD_RDFLib_Replace1.py`, `IFCtoLBD_RDFLib_Replace2.py` | Replace property URIs |
| `IFCtoLBD_Plot.py` | Plot graph relationships |
| `IFCtoLBD_SPARQL_Open3D.py` | Query and visualize geometry |
| `IFCtoLBD_SPARQL_Open3D_Interface.py` | Explore spatial interfaces |

Visualization examples need additional Python packages and geometry export.
For converter options, consult the [Java guide](java_examples.md); for geometry
engine setup and extension points, use the [development guide](development.md).
