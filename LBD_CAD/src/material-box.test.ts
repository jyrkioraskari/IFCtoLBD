import { describe, expect, it } from "vitest";
import { NullEngine, Scene, VertexBuffer } from "@babylonjs/core";
import { CadDocument } from "./model";
import { materialBox } from "./material-box";

describe("material box", () => {
  it("maps texture repeats from metres and rotates the grain in local face coordinates", () => {
    const doc = new CadDocument();
    const box = doc.add("Box");
    doc.setGeometry(box.id, { width: 2, height: 3, depth: 1 });
    const timber = doc.addMaterial("Boards", "timber");
    doc.updateMaterial(timber.id, { tileWidth: .5, tileHeight: 1, grainDegrees: 0 });
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const mesh = materialBox(doc.elements[0], Array(6).fill(doc.material(timber.id)), scene);
    const uvs = mesh.getVerticesData(VertexBuffer.UVKind)!;
    expect(mesh.subMeshes).toHaveLength(6);
    expect(uvs.slice(0, 8)).toEqual([0, 0, 6, 0, 6, 1, 0, 1]);
    doc.updateMaterial(timber.id, { grainDegrees: 90 });
    const rotated = materialBox(doc.elements[0], Array(6).fill(doc.material(timber.id)), scene);
    const rotatedUvs = rotated.getVerticesData(VertexBuffer.UVKind)!;
    expect(rotatedUvs[2]).toBeCloseTo(0);
    expect(rotatedUvs[3]).toBeCloseTo(3);
    expect(rotatedUvs[4]).toBeCloseTo(-2);
    scene.dispose(); engine.dispose();
  });
});
