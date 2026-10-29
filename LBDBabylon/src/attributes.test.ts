import { describe, expect, it } from "vitest";
import { lbdAttributes } from "./attributes";
import { parseTurtle } from "./rdf";

describe("LBD attribute presentation", () => {
  it("groups values and resolves compact predicates and linked labels", async () => {
    const data = await parseTurtle(`
      @prefix bot: <https://w3id.org/bot#> .
      @prefix ex: <https://example.test/> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      ex:wall a bot:Element ; rdfs:label "Wall 12" ; ex:tag "external", "load-bearing" ; bot:hasSpace ex:room .
      ex:room a bot:Space ; rdfs:label "Meeting room" .
    `);
    const rows = lbdAttributes(data, "https://example.test/wall");
    expect(rows[0]).toMatchObject({ name: "label", values: [{ text: "Wall 12" }] });
    expect(rows.find(row => row.name === "ex:tag")?.values.map(value => value.text)).toEqual(["external", "load-bearing"]);
    expect(rows.find(row => row.name === "bot:hasSpace")?.values[0]).toEqual({ text: "Meeting room (ex:room)", iri: "https://example.test/room" });
  });

  it("keeps language and datatype information", async () => {
    const data = await parseTurtle(`@prefix ex: <https://example.test/> . ex:a ex:name "Tür"@de ; ex:height 2.1 .`);
    const rows = lbdAttributes(data, "https://example.test/a");
    expect(rows.find(row => row.name === "ex:name")?.values[0].text).toBe("Tür @de");
    expect(rows.find(row => row.name === "ex:height")?.values[0].text).toContain("decimal");
  });
});
