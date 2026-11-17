import { describe, expect, it } from "vitest";
import { NullEngine, Scene } from "@babylonjs/core";
import { CadDocument } from "./model";
import { hostedInsert } from "./hosted-insert";

describe("hosted window and door inserts", () => {
  it("builds a pane or leaf with separate visible frame members", () => {
    const doc = new CadDocument();
    const engine = new NullEngine(), scene = new Scene(engine);
    for (const kind of ["Window", "Door"] as const) {
      const element = doc.add(kind);
      const material = doc.material(element.materialId!)!;
      const mesh = hostedInsert(element, material, material, scene);
      expect(mesh.getIndices()!.length).toBeGreaterThan(36);
      expect(mesh.subMeshes.some(part => part.materialIndex === 1)).toBe(true);
      mesh.dispose();
    }
    scene.dispose(); engine.dispose();
  });
});
