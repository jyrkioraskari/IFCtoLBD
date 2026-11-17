import { describe, expect, it } from "vitest";
import { MeshBuilder, NullEngine, Scene, VertexBuffer } from "@babylonjs/core";
import { featureEdges } from "./feature-edges";

describe("architectural borders", () => {
  it("keeps the rectangle perimeter but removes its triangle diagonal", () => {
    const positions = [0, 0, 0, 2, 0, 0, 2, 0, 1, 0, 0, 1];
    expect(featureEdges(positions, [0, 1, 2, 0, 2, 3])).toHaveLength(4);
  });
  it("keeps a crease even when triangles duplicate their vertices", () => {
    const positions = [0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    expect(featureEdges(positions, [0, 1, 2, 3, 4, 5])).toHaveLength(5);
  });
  it("returns the twelve outside edges of a box", () => {
    const engine = new NullEngine();
    try {
      const box = MeshBuilder.CreateBox("box", { size: 2 }, new Scene(engine));
      expect(featureEdges(box.getVerticesData(VertexBuffer.PositionKind)!, box.getIndices()!)).toHaveLength(12);
    } finally { engine.dispose(); }
  });
});
