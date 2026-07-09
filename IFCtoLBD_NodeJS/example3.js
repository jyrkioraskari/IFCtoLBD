const { QueryEngine } = require("@comunica/query-sparql");
const { Parser, Store } = require("n3");
const { DEFAULT_IFC_FILE, IFCtoLBD } = require("./IFCtoLBD");

async function main() {
  const ifcFile = process.argv[2] || DEFAULT_IFC_FILE;
  const converter = new IFCtoLBD({
    baseUri: "https://example.com/duplex/",
    levels: [1],
  });
  const turtle = converter.convertToTurtle(ifcFile);
  const store = new Store();
  const parser = new Parser({ format: "text/turtle" });

  store.addQuads(parser.parse(turtle));

  const engine = new QueryEngine();
  const bindings = await engine.queryBindings(
    `
      SELECT ?subject ?type WHERE {
        ?subject a ?type .
      }
      LIMIT 10
    `,
    { sources: [store] }
  );
  const rows = await bindings.toArray();

  console.log("converted IFC file: " + ifcFile);
  console.log("in-memory quad count: " + store.size);
  console.log("first rdf:type bindings:");
  rows.forEach((row) => {
    console.log("- " + row.get("subject").value + " -> " + row.get("type").value);
  });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
