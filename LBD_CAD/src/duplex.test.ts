import { readFileSync } from "node:fs";
import { Mesh, NullEngine, Ray, Scene, Vector3, VertexBuffer, VertexData } from "@babylonjs/core";
import { describe, expect, it } from "vitest";
import { CadDocument } from "./model";
import { cutWallOpenings } from "./wall-cut";
import { featureEdges } from "./feature-edges";
import { wallFeatureEdges } from "./wall-edges";
import { applyProjectedUvs } from "./projected-uvs";

describe("Duplex_LBD.ttl", () => {
  it("opens the repository's IFCtoLBD fixture as editable building elements", () => {
    const turtle = readFileSync(new URL("../../Duplex_LBD.ttl", import.meta.url), "utf8");
    const doc = CadDocument.parse(turtle);
    const kinds = doc.elements.reduce<Record<string, number>>((counts, element) => {
      counts[element.kind] = (counts[element.kind] ?? 0) + 1;
      return counts;
    }, {});
    expect(kinds).toMatchObject({ Wall: 57, Slab: 21, Space: 21, Door: 14, Window: 24, Opening: 50 });
    const wall = doc.elements.find(element => element.kind === "Wall")!;
    const wallGeometry = doc.importedMesh(wall.id)!;
    expect(wallGeometry.indices.length).toBeGreaterThan(0);
    expect(featureEdges(wallGeometry.positions, wallGeometry.indices).length).toBeGreaterThan(0);
    const engine = new NullEngine();
    try {
      const scene = new Scene(engine);
      const original = doc.importedMesh(wall.id)!;
      const mesh = new Mesh(wall.id, scene);
      const data = new VertexData();
      data.positions = original.positions;
      data.indices = original.indices;
      const normals: number[] = [];
      VertexData.ComputeNormals(data.positions, data.indices, normals);
      data.normals = normals;
      data.applyToMesh(mesh);
      doc.addHosted("Window", wall.id);
      const cut = cutWallOpenings(mesh, wall, doc.hostedOpenings(wall.id), scene);
      const opening = doc.hostedOpenings(wall.id)[0];
      const ray = new Ray(new Vector3(opening.x - wall.x - 2, opening.y - wall.y, opening.z - wall.z), new Vector3(1, 0, 0), 4);
      expect(mesh.intersects(ray).hit).toBe(true);
      expect(cut.intersects(ray).hit).toBe(false);
      doc.addHosted("Door", wall.id, { x: wall.x, y: wall.y, z: wall.z + wall.depth / 4 });
      const newDoor = doc.elements.find(e => e.kind === "Door" && !e.imported)!;
      expect(doc.material(newDoor.materialId!)?.color).toBe("#f1ece4");
      const doorOpening = doc.hostedOpenings(wall.id)[1];
      const doorRay = new Ray(new Vector3(doorOpening.x - wall.x - 2, doorOpening.y - wall.y, doorOpening.z - wall.z),
        new Vector3(1, 0, 0), 4);
      const withDoor = cutWallOpenings(mesh, wall, doc.hostedOpenings(wall.id), scene);
      const borders = wallFeatureEdges(withDoor.getVerticesData(VertexBuffer.PositionKind)!, withDoor.getIndices()!,
        wall, doc.hostedOpenings(wall.id));
      for (const opening of doc.hostedOpenings(wall.id)) {
        const localZ = opening.z - wall.z, lower = opening.y - wall.y - opening.height / 2;
        const upper = opening.y - wall.y + opening.height / 2;
        for (const [a, b] of borders.filter(([a, b]) => Math.abs(a[0] - b[0]) < 1e-4 &&
          Math.abs(a[2] - b[2]) < 1e-4 && Math.abs(Math.abs(a[2] - localZ) - opening.width / 2) < 1e-4)) {
          expect(Math.min(a[1], b[1])).toBeGreaterThanOrEqual(lower - 1e-4);
          expect(Math.max(a[1], b[1])).toBeLessThanOrEqual(upper + 1e-4);
        }
      }
      expect(mesh.intersects(doorRay).hit).toBe(true);
      expect(withDoor.intersects(doorRay).hit).toBe(false);
      expect(cut.getIndices()?.length).toBeGreaterThan(0);
      expect(cut.getIndices()?.length).not.toBe(mesh.getIndices()?.length);
      const plaster = doc.addMaterial("Plaster", "plaster");
      applyProjectedUvs(cut, plaster);
      expect(cut.getVerticesData(VertexBuffer.UVKind)?.length).toBe(cut.getTotalVertices() * 2);
    } finally { engine.dispose(); }
  });
  it("aligns new windows with the long axis and thickness of imported walls", () => {
    const turtle = readFileSync(new URL("../../Duplex_LBD.ttl", import.meta.url), "utf8");
    const doc = CadDocument.parse(turtle);
    const zWall = doc.elements.find(e => e.kind === "Wall" && e.depth > 3 && e.width < .2)!;
    const zWindow = doc.addHosted("Window", zWall.id, { x: zWall.x + 2, y: zWall.y, z: zWall.z + 1 });
    const zOpening = doc.elements.find(e => e.id === doc.relatedOpening(zWindow.id))!;
    expect(zWindow).toMatchObject({ x: zWall.x, z: zWall.z + 1, rotation: 90 });
    expect(zWindow.width).toBeGreaterThan(.5);
    expect(zWindow.depth).toBeLessThanOrEqual(zWall.width);
    expect(zOpening.depth).toBeCloseTo(zWall.width + .1);
    const engine = new NullEngine(), scene = new Scene(engine);
    const base = new Mesh("base", scene);
    const imported = doc.importedMesh(zWall.id)!;
    const data = new VertexData(); data.positions = imported.positions; data.indices = imported.indices;
    const normals: number[] = []; VertexData.ComputeNormals(data.positions, data.indices, normals);
    data.normals = normals; data.applyToMesh(base);
    const beforeCut = cutWallOpenings(base, zWall, [zOpening], scene);
    doc.moveGroup(zWindow.id, { x: zWindow.x, y: zWindow.y + .2, z: zWindow.z - .5 });
    const movedWindow = doc.elements.find(e => e.id === zWindow.id)!;
    const movedOpening = doc.elements.find(e => e.id === zOpening.id)!;
    expect(movedOpening.z - zOpening.z).toBeCloseTo(-.5);
    expect(movedOpening.y - zOpening.y).toBeCloseTo(.2);
    expect(movedWindow.z - zWindow.z).toBeCloseTo(-.5);
    const afterCut = cutWallOpenings(base, zWall, [movedOpening], scene);
    expect(afterCut.getVerticesData(VertexBuffer.PositionKind)).not.toEqual(beforeCut.getVerticesData(VertexBuffer.PositionKind));
    scene.dispose(); engine.dispose();
    const xWall = doc.elements.find(e => e.kind === "Wall" && e.width > 3 && e.depth < .2)!;
    const xWindow = doc.addHosted("Window", xWall.id, { x: xWall.x + 1, y: xWall.y, z: xWall.z + 2 });
    expect(xWindow).toMatchObject({ x: xWall.x + 1, z: xWall.z, rotation: 0 });
    expect(xWindow.depth).toBeLessThanOrEqual(xWall.depth);
  });
  it("cuts through imported Duplex walls where a new door is placed", () => {
    const turtle = readFileSync(new URL("../../Duplex_LBD.ttl", import.meta.url), "utf8");
    const doc = CadDocument.parse(turtle);
    const engine = new NullEngine(), scene = new Scene(engine);
    try {
      for (const wall of doc.elements.filter(e => e.kind === "Wall")) {
        const imported = doc.importedMesh(wall.id)!;
        const base = new Mesh("base", scene);
        const data = new VertexData(); data.positions = imported.positions; data.indices = imported.indices;
        const normals: number[] = []; VertexData.ComputeNormals(data.positions, data.indices, normals);
        data.normals = normals;
        data.applyToMesh(base);
        const door = doc.addHosted("Door", wall.id);
        const opening = doc.elements.find(e => e.id === doc.relatedOpening(door.id))!;
        const alongZ = wall.depth > wall.width;
        const origin = alongZ ? new Vector3(-10, opening.y - wall.y, opening.z - wall.z) :
          new Vector3(opening.x - wall.x, opening.y - wall.y, -10);
        const ray = new Ray(origin, alongZ ? new Vector3(1, 0, 0) : new Vector3(0, 0, 1), 20);
        if (base.intersects(ray).hit) {
          const cut = cutWallOpenings(base, wall, [opening], scene);
          expect(cut.intersects(ray).hit, wall.id).toBe(false);
          const borders = wallFeatureEdges(cut.getVerticesData(VertexBuffer.PositionKind)!, cut.getIndices()!, wall, [opening]);
          const boundary = alongZ ? opening.z - wall.z : opening.x - wall.x;
          const lower = opening.y - wall.y - opening.height / 2;
          const upper = opening.y - wall.y + opening.height / 2;
          for (const [a, b] of borders.filter(([a, b]) => {
            if (Math.abs(a[0] - b[0]) > 1e-4 || Math.abs(a[2] - b[2]) > 1e-4) return false;
            const position = alongZ ? a[2] : a[0];
            return Math.abs(Math.abs(position - boundary) - opening.width / 2) < 1e-4;
          })) {
            expect(Math.min(a[1], b[1]), wall.id).toBeGreaterThanOrEqual(lower - 1e-4);
            expect(Math.max(a[1], b[1]), wall.id).toBeLessThanOrEqual(upper + 1e-4);
          }
          cut.dispose();
        }
        base.dispose();
      }
    } finally { scene.dispose(); engine.dispose(); }
  }, 30000);
  it("moves a new door and cuts the host wall at its new location", () => {
    const doc = CadDocument.parse(readFileSync(new URL("../../Duplex_LBD.ttl", import.meta.url), "utf8"));
    const wall = doc.elements.find(e => e.kind === "Wall" && e.depth > 15 && e.width > .5)!;
    const door = doc.addHosted("Door", wall.id);
    const openingId = doc.relatedOpening(door.id)!;
    const target = { x: door.x, y: door.y, z: door.z + 2 };
    doc.moveGroup(door.id, target);
    const moved = doc.elements.find(e => e.id === door.id)!;
    const opening = doc.elements.find(e => e.id === openingId)!;
    expect(moved).toMatchObject(target);
    expect(opening).toMatchObject(target);
    const engine = new NullEngine(), scene = new Scene(engine);
    try {
      const imported = doc.importedMesh(wall.id)!;
      const base = new Mesh("base", scene);
      const data = new VertexData(); data.positions = imported.positions; data.indices = imported.indices;
      const normals: number[] = []; VertexData.ComputeNormals(data.positions, data.indices, normals);
      data.normals = normals; data.applyToMesh(base);
      const ray = new Ray(new Vector3(-10, opening.y - wall.y, opening.z - wall.z), new Vector3(1, 0, 0), 20);
      expect(base.intersects(ray).hit).toBe(true);
      const cut = cutWallOpenings(base, wall, doc.hostedOpenings(wall.id), scene);
      expect(cut.intersects(ray).hit).toBe(false);
    } finally { scene.dispose(); engine.dispose(); }
  }, 30000);
  it("retains an imported window's original void when its IFC mesh and opening move", async () => {
    const turtle = readFileSync(new URL("../../Duplex_LBD.ttl", import.meta.url), "utf8");
    const doc = CadDocument.parse(turtle);
    const window = doc.elements.find(e => e.kind === "Window" && e.imported &&
      doc.hostWall(e.id) && doc.relatedOpening(e.id) &&
      doc.elements.some(item => item.id === doc.relatedOpening(e.id)))!;
    const wallId = doc.hostWall(window.id)!;
    const openingId = doc.relatedOpening(window.id)!;
    const original = doc.elements.find(e => e.id === openingId)!;
    doc.moveGroup(window.id, { x: window.x + .2, y: window.y, z: window.z });
    expect(doc.elements.find(e => e.id === openingId)?.x).toBeCloseTo(original.x + .2);
    expect(doc.originalOpening(openingId)?.x).toBeCloseTo(original.x);
    expect(doc.hasDerivedWall(wallId)).toBe(true);
    const wall = doc.elements.find(e => e.id === wallId)!;
    const imported = doc.importedMesh(wallId)!;
    const engine = new NullEngine(), scene = new Scene(engine);
    const mesh = new Mesh(wallId, scene);
    const data = new VertexData(); data.positions = imported.positions; data.indices = imported.indices;
    const normals: number[] = []; VertexData.ComputeNormals(data.positions, data.indices, normals);
    data.normals = normals; data.applyToMesh(mesh);
    const moved = doc.elements.find(e => e.id === openingId)!;
    const cut = cutWallOpenings(mesh, wall, [moved], scene, [doc.originalOpening(openingId)!]);
    expect(cut.getIndices()?.length).toBeGreaterThan(0);
    scene.dispose(); engine.dispose();
    const reopened = CadDocument.parse(await doc.serialize());
    expect(reopened.originalOpening(openingId)).toEqual(doc.originalOpening(openingId));
  });
});
