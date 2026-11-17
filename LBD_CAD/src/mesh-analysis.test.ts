import { describe, expect, it } from "vitest";
import { MeshBuilder, NullEngine, Scene, VertexBuffer } from "@babylonjs/core";
import { analyzeMesh } from "./mesh-analysis";

describe("mesh quantities", () => {
  it("computes exact area and volume for a closed box", () => {
    const engine = new NullEngine();
    try {
      const box = MeshBuilder.CreateBox("box", { width: 2, height: 3, depth: 4 }, new Scene(engine));
      const result = analyzeMesh(box.getVerticesData(VertexBuffer.PositionKind)!, box.getIndices()!);
      expect(result).toMatchObject({ boundaryEdges: 0, nonManifoldEdges: 0 });
      expect(result.surfaceArea).toBeCloseTo(52);
      expect(result.volume).toBeCloseTo(24);
    } finally { engine.dispose(); }
  });
  it("rejects volume for an open triangle", () => {
    const result = analyzeMesh([0, 0, 0, 1, 0, 0, 0, 1, 0], [0, 1, 2]);
    expect(result.surfaceArea).toBeCloseTo(.5);
    expect(result.volume).toBeUndefined();
    expect(result.boundaryEdges).toBe(3);
  });
});
