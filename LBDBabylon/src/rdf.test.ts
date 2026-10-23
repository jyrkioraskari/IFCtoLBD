import { describe, expect, it } from "vitest";
import { readFile } from "node:fs/promises";
import { decodeBase64, ifcToBabylonPosition } from "./geometry";
import { parseTurtle } from "./rdf";
const fixture = (name: string) => readFile(new URL(`../fixtures/${name}`, import.meta.url), "utf8");
describe("IFCtoLBD Turtle discovery", () => {
  it("finds embedded FOG OBJ, material, coordinate metadata, and preserves its element URI", async () => { const model = await parseTurtle(await fixture("supported.ttl")); expect(model.geometries).toHaveLength(1); expect(model.geometries[0].element).toBe("https://example.test/wall"); expect(model.geometries[0]).toMatchObject({ diffuseColor: "#cc6633", alpha: .7, coordinateSpace: "local-engineering", coordinateReferenceSystem: "https://example.test/ifc-local", lengthUnit: "metre", unitToMetres: 1 }); expect(decodeBase64(model.geometries[0].payload!)).toContain("v 4 0 3"); });
  it("discovers GLB and self-contained glTF artifacts", async () => { const model = await parseTurtle(`
    @prefix omg: <https://w3id.org/omg#> . @prefix geo: <https://w3id.org/ifctolbd/geometry#> .
    <https://example.test/a> omg:hasGeometry <https://example.test/ga> .
    <https://example.test/ga> geo:artifact <geometry/a.glb> . <geometry/a.glb> geo:mediaType "model/gltf-binary" .
    <https://example.test/b> omg:hasGeometry <https://example.test/gb> .
    <https://example.test/gb> geo:artifact <geometry/b.gltf> . <geometry/b.gltf> geo:mediaType "model/gltf+json" .`);
    expect(model.geometries.map(g => g.kind)).toEqual(["artifact-glb", "artifact-gltf"]);
  });
  it("keeps a semantic-only model useful", async () => { const model = await parseTurtle(await fixture("semantics-only.ttl")); expect(model.entities[0].kind).toBe("building"); expect(model.geometries).toHaveLength(0); });
  it("retains multiple geometries for one RDF element", async () => { const model = await parseTurtle(await fixture("multiple-meshes.ttl")); expect(model.geometries.map(g => g.element)).toEqual(["https://example.test/wall", "https://example.test/wall"]); });
  it("reports a geometry node without a supported rendering payload", async () => { const model = await parseTurtle(await fixture("unsupported.ttl")); expect(model.geometries).toHaveLength(0); expect(model.warnings).toHaveLength(1); });
  it("maps IFC Z-up coordinates to Babylon Y-up once", () => expect(ifcToBabylonPosition([2, 3, 4])).toEqual([2, 4, -3]));
  it("reports malformed Turtle", async () => expect(parseTurtle(await fixture("malformed.ttl"))).rejects.toThrow());
});
