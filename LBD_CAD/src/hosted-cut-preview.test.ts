import { readFileSync } from "node:fs";
import { Mesh, MeshBuilder, NullEngine, Ray, Scene, Vector3, VertexData } from "@babylonjs/core";
import { describe, expect, it } from "vitest";
import { CadDocument } from "./model";
import { HostedCutPreview } from "./hosted-cut-preview";

describe("hosted cut preview", () => {
  it("moves the opening with the door before the document is changed", () => {
    const doc = new CadDocument();
    const wall = doc.add("Wall");
    const door = doc.addHosted("Door", wall.id);
    const engine = new NullEngine(), scene = new Scene(engine);
    try {
      const wallMesh = MeshBuilder.CreateBox(wall.id, { width: wall.width, height: wall.height, depth: wall.depth }, scene);
      const preview = new HostedCutPreview(doc, wall, wallMesh, door.id, scene);
      preview.update(door, { x: 1, y: door.y, z: door.z }, true);
      const cut = scene.meshes.find(mesh => mesh.name === wall.id && mesh !== wallMesh)!;
      const ray = (x: number) => new Ray(new Vector3(x, door.y, -2), new Vector3(0, 0, 1), 4);
      expect(cut.intersects(ray(0)).hit).toBe(true);
      expect(cut.intersects(ray(1)).hit).toBe(false);
      expect(doc.elements.find(e => e.id === door.id)?.x).toBe(0);
      expect(wallMesh.isVisible).toBe(false);
      preview.dispose();
      expect(scene.meshes.includes(cut)).toBe(false);
    } finally { scene.dispose(); engine.dispose(); }
  });
  it("previews a moved IFC window's old and new voids", () => {
    const doc = CadDocument.parse(readFileSync(new URL("../../Duplex_LBD.ttl", import.meta.url), "utf8"));
    const window = doc.elements.find(e => e.kind === "Window" && e.imported && doc.hostWall(e.id) &&
      doc.relatedOpening(e.id) && doc.elements.some(item => item.id === doc.relatedOpening(e.id)))!;
    const wall = doc.elements.find(e => e.id === doc.hostWall(window.id))!;
    expect(doc.hostedOpenings(wall.id).map(item => item.id)).toContain(doc.relatedOpening(window.id));
    const engine = new NullEngine(), scene = new Scene(engine);
    try {
      const imported = doc.importedMesh(wall.id)!;
      const wallMesh = new Mesh(wall.id, scene);
      const data = new VertexData(); data.positions = imported.positions; data.indices = imported.indices;
      const normals: number[] = []; VertexData.ComputeNormals(imported.positions, imported.indices, normals);
      data.normals = normals; data.applyToMesh(wallMesh);
      wallMesh.position.set(wall.x, wall.y, wall.z);
      const preview = new HostedCutPreview(doc, wall, wallMesh, window.id, scene);
      preview.update(window, { x: window.x, y: window.y, z: window.z + 1.2 }, true);
      const cut = scene.meshes.find(mesh => mesh.name === wall.id && mesh !== wallMesh)!;
      const oldRay = new Ray(new Vector3(-10, window.y - wall.y, window.z - wall.z), new Vector3(1, 0, 0), 20);
      const newRay = new Ray(new Vector3(-10, window.y - wall.y, window.z + 1.2 - wall.z), new Vector3(1, 0, 0), 20);
      expect(cut.intersects(oldRay).hit).toBe(true);
      expect(cut.intersects(newRay).hit).toBe(false);
      expect(doc.originalOpening(doc.relatedOpening(window.id)!)).toBeUndefined();
      preview.dispose();
    } finally { scene.dispose(); engine.dispose(); }
  });
});
