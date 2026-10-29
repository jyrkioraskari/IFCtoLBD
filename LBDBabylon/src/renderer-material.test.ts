import { Material, Mesh, MeshBuilder, MultiMaterial, NullEngine, Scene, StandardMaterial, Vector3, VertexData } from "@babylonjs/core";
import { describe, expect, it } from "vitest";
import { Viewer, configureMaterialOpacity, configureTwoSidedMaterial, entryGroundLevel, exteriorDoorThreshold, exteriorGroundGeometry, isRenderableEntity, isRenderableGeometry, isWallType, virtualGroundLevel } from "./renderer";
import { WALKER } from "./navigation-model";

describe("element materials", () => {
  it("makes standard and nested imported materials two-sided", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const child = new StandardMaterial("child", scene);
    const multi = new MultiMaterial("multi", scene);
    multi.subMaterials.push(child);

    configureTwoSidedMaterial(multi);

    expect(multi.backFaceCulling).toBe(false);
    expect(child.backFaceCulling).toBe(false);
    expect(child.twoSidedLighting).toBe(true);

    scene.dispose();
    engine.dispose();
  });

  it("makes wall materials 10% transparent, including nested imported materials", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const opaque = new StandardMaterial("opaque", scene);
    const alreadyTransparent = new StandardMaterial("already-transparent", scene);
    alreadyTransparent.alpha = .65;
    const multi = new MultiMaterial("wall", scene);
    multi.subMaterials.push(opaque, alreadyTransparent);

    configureMaterialOpacity(multi, .9);

    expect(multi.alpha).toBe(.9);
    expect(opaque.alpha).toBe(.9);
    expect(alreadyTransparent.alpha).toBe(.65);
    expect(opaque.transparencyMode).toBe(Material.MATERIAL_ALPHABLEND);
    expect(opaque.needDepthPrePass).toBe(true);
    expect(opaque.forceDepthWrite).toBe(true);
    expect(alreadyTransparent.transparencyMode).toBe(Material.MATERIAL_ALPHABLEND);
    expect(isWallType(["https://example.test/IfcWallStandardCase"])).toBe(true);
    expect(isWallType(["https://pi.pauwel.be/voc/buildingelement#Wall"])).toBe(true);
    expect(isWallType(["https://example.test/IfcDoor"])).toBe(false);

    scene.dispose();
    engine.dispose();
  });

  it("does not render IFC opening or space volume geometry", () => {
    const entity = (type: string) => ({ id: "element", kind: "element" as const, label: "Element", types: [type] });

    expect(isRenderableEntity(entity("https://example.test/IfcOpeningElement"))).toBe(false);
    expect(isRenderableEntity(entity("https://example.test/IfcSpace"))).toBe(false);
    expect(isRenderableEntity(entity("https://w3id.org/bot#Space"))).toBe(false);
    expect(isRenderableEntity(entity("https://example.test/IfcWall"))).toBe(true);
    expect(isRenderableEntity(undefined)).toBe(true);
    expect(isRenderableGeometry({ ifcMaterialClass: "IfcOpeningElement" }, undefined)).toBe(false);
    expect(isRenderableGeometry({ ifcMaterialClass: "IfcSpace" }, undefined)).toBe(false);
    expect(isRenderableGeometry({ ifcMaterialClass: "IfcWall" }, undefined)).toBe(true);
  });

  it("keeps a green ground even when model types do not identify a walkable surface", () => {
    expect(virtualGroundLevel([.15, 3.2], [-.4, 0])).toBeCloseTo(.145);
    expect(virtualGroundLevel([], [-1.4, -.8, 0, .01, .02, .03, 3.1, 3.1])).toBeCloseTo(.01);
    expect(entryGroundLevel([-.5, 0, 3.1], [-1.4, 0], [0, 0, 0, 0, 3.1])).toEqual({
      level: 0, source: "exterior-door-bottom", nearestFloorTop: 0,
      outlierDoorBottoms: [], aligned: true
    });
    expect(entryGroundLevel([-.5, 0, 3.1], [-1.4, 0], [0, 0, .5, 3.1])).toMatchObject({
      level: 0, outlierDoorBottoms: [.5], aligned: false
    });
    expect(entryGroundLevel([-.5, 0, 3.1], [-1.4, 0], [0, 0, 0, 0])).toMatchObject({
      level: 0, nearestFloorTop: 0, aligned: true
    });
    expect(entryGroundLevel([0], [-.2], [0, .02])).toMatchObject({
      level: 0, nearestFloorTop: 0, aligned: true
    });
    expect(entryGroundLevel([.5], [-.2], [0, 0])).toMatchObject({
      level: 0, nearestFloorTop: .5, aligned: false
    });
  });

  it("makes exactly one continuous ground plane", () => {
    const { positions, indices, colors } = exteriorGroundGeometry(-1, 1, -1, 1, 0);
    expect(positions).toEqual([-1, 0, -1, 1, 0, -1, -1, 0, 1, 1, 0, 1]);
    expect(indices).toEqual([0, 3, 1, 0, 2, 3]);
    expect(colors).toHaveLength(16);
    expect(new Set(colors.filter((_value, index) => index % 4 === 0)).size).toBeGreaterThan(1);
    const larger = exteriorGroundGeometry(-10, 11, -12, 13, 0);
    expect(larger.positions.every((value, index) => index % 3 !== 1 || value === 0)).toBe(true);
    expect(larger.indices.length).toBeGreaterThan(6);
  });

  it("bridges only ground-level exterior doorways in the single ground mesh", () => {
    const footprint = { minX: -4.4, maxX: 4.4, minZ: -8.9, maxZ: 8.9 };
    // This Duplex door and the entry slab both start at IFC elevation zero.
    const doorway = { minX: -4.425, maxX: -3.958, minZ: -7.9, maxZ: -6.5, minY: -2.5424 };
    const thresholds = exteriorDoorThreshold(doorway, footprint, -2.5424);
    expect(thresholds).toEqual([{
      minX: footprint.minX,
      maxX: doorway.maxX + WALKER.radius,
      minZ: doorway.minZ - WALKER.radius,
      maxZ: doorway.maxZ + WALKER.radius
    }]);
    expect(exteriorDoorThreshold({ ...doorway, minY: .2 }, footprint, -2.5424)).toEqual([]);
    expect(thresholds[0].maxX).toBeGreaterThan(-3.983);
  });

  it("picks the single plane under a walker crossing a Duplex doorway", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const ground = new Mesh("__virtual_ground", scene);
    const geometry = exteriorGroundGeometry(-4.4, 4.4, -8.9, 8.9, 8);
    const data = new VertexData();
    data.positions = geometry.positions;
    data.indices = geometry.indices;
    data.applyToMesh(ground);
    ground.position.y = -2.5424;
    ground.computeWorldMatrix(true);
    ground.isPickable = true;
    ground.metadata = { ifcToLbd: {
      virtualGround: true,
      footprint: { minX: -4.4, maxX: 4.4, minZ: -8.9, maxZ: 8.9 },
      thresholds: exteriorDoorThreshold(
        { minX: -4.425, maxX: -3.958, minZ: -7.9, maxZ: -6.5, minY: -2.5424 },
        { minX: -4.4, maxX: 4.4, minZ: -8.9, maxZ: 8.9 }, -2.5424)
    } };
    const viewer = Object.create(Viewer.prototype) as Viewer;
    Object.assign(viewer, { scene, roles: new Map([[ground, { walkableSurface: true }]]) });

    expect(viewer.groundBelow(new Vector3(-4.1, -2.5224, -7.0), .34)?.y).toBeCloseTo(-2.5424);
    scene.dispose();
    engine.dispose();
  });

  it("creates an opaque patterned ground without a canvas texture", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const wall = MeshBuilder.CreateBox("wall", { width: 8, height: 3, depth: .3 }, scene);
    const floor = MeshBuilder.CreateBox("floor", { width: 8, height: .2, depth: 8 }, scene);
    floor.position.y = -.1;
    wall.metadata = { ifcToLbd: { types: ["IfcWall"] } };
    const viewer = Object.create(Viewer.prototype) as Viewer;
    Object.assign(viewer, {
      scene,
      roles: new Map([[wall, { walkableSurface: false }], [floor, { walkableSurface: true }]]),
      byMesh: new Map([[wall, "wall"], [floor, "floor"]])
    });

    (viewer as unknown as { createVirtualGround(): void }).createVirtualGround();
    const ground = scene.getMeshByName("__virtual_ground") as Mesh;
    expect(ground).toBeDefined();
    expect((ground.material as StandardMaterial).alpha).toBe(1);
    expect((ground.material as StandardMaterial).diffuseTexture).toBeNull();
    expect((ground.material as StandardMaterial).backFaceCulling).toBe(false);
    expect(ground.getVerticesData("color")?.length).toBeGreaterThan(16);
    scene.dispose();
    engine.dispose();
  });
});
