import { describe, expect, it } from "vitest";
import { MeshBuilder, NullEngine, Scene, VertexBuffer } from "@babylonjs/core";
import { CadDocument } from "./model";
import { applyProjectedUvs } from "./projected-uvs";

describe("projected texture coordinates", () => {
  it("gives each triangle metre-based UVs on a mesh without source UVs", () => {
    const engine = new NullEngine(), scene = new Scene(engine);
    const box = MeshBuilder.CreateBox("wall", { width: 4, height: 3, depth: .2 }, scene);
    box.removeVerticesData(VertexBuffer.UVKind);
    const doc = new CadDocument();
    const concrete = doc.addMaterial("Concrete", "concrete");
    doc.updateMaterial(concrete.id, { tileWidth: 1, tileHeight: 1 });
    applyProjectedUvs(box, doc.material(concrete.id)!);
    expect(box.getVerticesData(VertexBuffer.UVKind)?.length).toBe(box.getTotalVertices() * 2);
    const uvs = box.getVerticesData(VertexBuffer.UVKind)!;
    expect(Math.max(...uvs) - Math.min(...uvs)).toBeGreaterThanOrEqual(4);
    expect(box.getIndices()?.length).toBe(36);
    scene.dispose(); engine.dispose();
  });
});
