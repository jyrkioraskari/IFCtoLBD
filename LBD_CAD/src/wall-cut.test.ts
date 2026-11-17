import { describe, expect, it } from "vitest";
import { Mesh, MeshBuilder, NullEngine, Ray, Scene, Vector3, VertexData } from "@babylonjs/core";
import { CadDocument, NS } from "./model";
import { decodeObj, encodeObj, parseObj } from "./ifc-geometry";
import { meshToIfcObj } from "./geometry-export";
import { cutWallOpenings } from "./wall-cut";

describe("imported wall openings", () => {
  it("cuts through the full thickness when a moved IFC window drifts sideways", () => {
    const engine = new NullEngine(), scene = new Scene(engine);
    try {
      const base = MeshBuilder.CreateBox("wall", { width: .417, height: 3, depth: 4 }, scene);
      const wall = { id: "wall", kind: "Wall", label: "Wall", x: 0, y: 0, z: 0,
        width: .417, height: 3, depth: 4, rotation: 0, imported: true } as const;
      const opening = { id: "window-opening", kind: "Opening", label: "Opening", x: -.09, y: 0, z: 1,
        width: .417, height: 1, depth: .75, rotation: 0, imported: true } as const;
      const cut = cutWallOpenings(base, wall, [opening], scene,
        [{ x: 0, y: 0, z: 0, width: .417, height: 1, depth: .75 }]);
      const throughWindow = new Ray(new Vector3(-2, 0, 1), new Vector3(1, 0, 0), 4);
      const besideWindow = new Ray(new Vector3(-2, 0, -1), new Vector3(1, 0, 0), 4);
      expect(cut.intersects(throughWindow).hit).toBe(false);
      expect(cut.intersects(besideWindow).hit).toBe(true);
    } finally { scene.dispose(); engine.dispose(); }
  });
  it("saves a cut OBJ while retaining the editable wall base", async () => {
    const engine = new NullEngine();
    try {
      const scene = new Scene(engine);
      const original = MeshBuilder.CreateBox("source", { width: 4, height: 3, depth: .2 }, scene);
      original.position.y = 1.5;
      const originalObj = meshToIfcObj(original);
      const turtle = `@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix omg: <https://w3id.org/omg#> .
@prefix fog: <https://w3id.org/fog#> .
@prefix lbd: <https://linkedbuildingdata.org/LBD#> .
<urn:wall> owl:sameAs <https://example.org/IfcWallStandardCase_1>; omg:hasGeometry <urn:geometry> .
<urn:geometry> fog:asObj_v3.0-obj "${encodeObj(originalObj)}"; lbd:hasBoundingBox <urn:bounds> .
<urn:bounds> lbd:x-min -2; lbd:x-max 2; lbd:y-min -0.1; lbd:y-max 0.1; lbd:z-min 0; lbd:z-max 3 .`;
      const doc = CadDocument.parse(turtle);
      const wall = doc.elements[0];
      doc.addHosted("Door", wall.id);
      expect(doc.hasDerivedWall(wall.id)).toBe(true);
      const source = doc.importedMesh(wall.id, wall)!;
      const mesh = new Mesh("wall", scene);
      const data = new VertexData();
      data.positions = source.positions; data.indices = source.indices;
      const normals: number[] = [];
      VertexData.ComputeNormals(data.positions, data.indices, normals);
      data.normals = normals; data.applyToMesh(mesh);
      const cut = cutWallOpenings(mesh, wall, doc.hostedOpenings(wall.id), scene);
      const opening = doc.hostedOpenings(wall.id)[0];
      const throughDoor = new Ray(new Vector3(opening.x - wall.x, opening.y - wall.y, -2), new Vector3(0, 0, 1), 4);
      expect(mesh.intersects(throughDoor).hit).toBe(true);
      expect(cut.intersects(throughDoor).hit).toBe(false);
      cut.position.set(wall.x, wall.y, wall.z);
      const cutObj = meshToIfcObj(cut);
      expect(parseObj(cutObj).vertices.length).toBeGreaterThan(parseObj(originalObj).vertices.length);
      doc.setDerivedWallObj(wall.id, cutObj);
      const reopened = CadDocument.parse(await doc.serialize());
      const fog = "https://w3id.org/fog#asObj_v3.0-obj";
      expect(decodeObj(reopened.value("urn:geometry", fog)!)).toBe(cutObj);
      expect(decodeObj(reopened.value("urn:geometry", NS.cad + "baseObj")!)).toBe(originalObj);
      expect(reopened.hostedOpenings(wall.id)).toHaveLength(1);
      expect(reopened.value("urn:geometry", "https://linkedbuildingdata.org/LBD#hasWireframe")).toContain("MULTILINESTRING Z");
    } finally { engine.dispose(); }
  });
});
