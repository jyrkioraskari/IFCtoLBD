import { describe, expect, it } from "vitest";
import { CadDocument, NS, starterDocument } from "./model";
import { decodeObj, encodeObj } from "./ifc-geometry";

describe("RDF document", () => {
  it("round trips editable geometry and preserves unrelated triples", async () => {
    const input = `@prefix cad: <${NS.cad}> .\n@prefix ex: <https://example.org/> .\nex:wall a cad:Wall; cad:width 4.5; cad:height 3; cad:depth 0.2; ex:asset ex:asset-1 .`;
    const doc = CadDocument.parse(input);
    expect(doc.elements[0].width).toBe(4.5);
    doc.setNumber("https://example.org/wall", "width", 5);
    const reopened = CadDocument.parse(await doc.serialize());
    expect(reopened.elements[0].width).toBe(5);
    expect(reopened.value("https://example.org/wall", "https://example.org/asset")).toBe("https://example.org/asset-1");
    expect(reopened.triples("https://example.org/wall").filter(q => q.predicate.value === NS.cad + "width")).toHaveLength(1);
  });
  it("creates BOT elements and rejects invalid dimensions", () => {
    const doc = new CadDocument(); const wall = doc.add("Wall");
    expect(doc.value(wall.id, NS.rdf + "type")).toBe(NS.cad + "Wall");
    expect(doc.triples(wall.id).some(q => q.object.value === NS.bot + "Element")).toBe(true);
    expect(() => doc.setNumber(wall.id, "width", 0)).toThrow();
    doc.remove(wall.id); expect(doc.elements).toHaveLength(0);
  });
  it("persists a drawn face and converts it to a BOT solid when extruded", async () => {
    const doc = new CadDocument();
    const face = doc.add("Face", { x: 2, y: 0, z: 3 });
    doc.setNumber(face.id, "width", 4);
    doc.setNumber(face.id, "depth", 3);
    expect(doc.triples(face.id).some(q => q.object.value === NS.bot + "Element")).toBe(false);
    const reopened = CadDocument.parse(await doc.serialize());
    expect(reopened.elements[0]).toMatchObject({ kind: "Face", x: 2, z: 3, width: 4, depth: 3 });
    reopened.setGeometry(face.id, { height: 2.5, y: 1.2495 });
    reopened.setKind(face.id, "Box");
    const solid = CadDocument.parse(await reopened.serialize());
    expect(solid.elements[0]).toMatchObject({ kind: "Box", height: 2.5, width: 4, depth: 3 });
    expect(solid.triples(face.id).some(q => q.object.value === NS.bot + "Element")).toBe(true);
    expect(solid.triples(face.id).some(q => q.object.value === NS.cad + "Face")).toBe(false);
  });
  it("stores an editable material color as RDF", async () => {
    const doc = new CadDocument();
    const wall = doc.add("Wall");
    doc.setColor(wall.id, "#AABBCC");
    expect(CadDocument.parse(await doc.serialize()).elements[0].color).toBe("#aabbcc");
    expect(() => doc.setColor(wall.id, "red")).toThrow();
    doc.setFinish(wall.id, "concrete");
    const finished = CadDocument.parse(await doc.serialize()).elements[0];
    expect(finished.finish).toBe("concrete");
    expect(finished.color).toBeUndefined();
    doc.setFinish(wall.id);
    expect(CadDocument.parse(await doc.serialize()).elements[0].finish).toBeUndefined();
  });
  it("shares named materials and keeps face IDs through geometry edits and Turtle reopen", async () => {
    const doc = new CadDocument();
    const first = doc.add("Box"), second = doc.add("Box");
    const timber = doc.addMaterial("Oak boards", "timber", "#b98855");
    doc.updateMaterial(timber.id, { tileWidth: .18, tileHeight: 1.2, grainDegrees: 90, opacity: .85, texture: "timber" });
    doc.assignMaterial(first.id, timber.id);
    doc.assignMaterial(second.id, timber.id);
    doc.setFaceMaterial(first.id, "pz", timber.id);
    const faceId = doc.faceId(first.id, "pz");
    doc.setGeometry(first.id, { x: 3, width: 2, rotation: 45 });
    const saved = await doc.serialize();
    const reopened = CadDocument.parse(saved);
    expect(reopened.material(timber.id)).toMatchObject({ label: "Oak boards", color: "#b98855",
      tileWidth: .18, tileHeight: 1.2, grainDegrees: 90, opacity: .85, texture: "timber" });
    expect(reopened.elements.filter(e => e.materialId === timber.id)).toHaveLength(2);
    expect(reopened.faceMaterial(first.id, "pz")?.id).toBe(timber.id);
    expect(reopened.value(faceId, NS.cad + "faceKey")).toBe("pz");
    reopened.updateMaterial(timber.id, { color: "#d0a878" });
    expect(reopened.material(reopened.elements[1].materialId!)?.color).toBe("#d0a878");
    reopened.remove(first.id);
    expect(reopened.triples(faceId)).toHaveLength(0);
    expect(reopened.material(timber.id)).toBeDefined();
  });
  it("validates material dimensions and accepts legacy finish literals", () => {
    const doc = CadDocument.parse(`@prefix cad: <${NS.cad}> . <urn:old> a cad:Box; cad:finish "timber" .`);
    expect(doc.elements[0].finish).toBe("timber");
    const material = doc.addMaterial("Pine", "timber");
    expect(() => doc.updateMaterial(material.id, { tileWidth: 0 })).toThrow();
    expect(() => doc.updateMaterial(material.id, { opacity: 1.2 })).toThrow();
    expect(() => doc.updateMaterial(material.id, { texture: "stone" as never })).toThrow();
    doc.updateMaterial(material.id, { finish: "glass" });
    expect(doc.material(material.id)?.opacity).toBe(.57);
    expect(doc.material(material.id)?.texture).toBe("none");
    expect(() => doc.setFaceMaterial("urn:old", "px", "urn:unknown")).toThrow();
    doc.assignMaterial("urn:old", material.id);
    expect(doc.value("urn:old", NS.cad + "finish")).toBeUndefined();
  });
  it("persists layers, groups, and visibility", async () => {
    const doc = new CadDocument();
    const wall = doc.add("Wall");
    doc.setLayer(wall.id, "Level 1");
    doc.setGroup(wall.id, "Envelope");
    doc.setHidden(wall.id, true);
    expect(CadDocument.parse(await doc.serialize()).elements[0]).toMatchObject({
      layer: "Level 1", group: "Envelope", hidden: true
    });
    doc.setGroup(wall.id);
    expect(doc.elements[0].group).toBeUndefined();
  });
  it("moves a named group as one edit", () => {
    const doc = new CadDocument();
    const first = doc.add("Wall", { x: 0, y: 1.5, z: 0 });
    const second = doc.add("Slab", { x: 3, y: 0, z: 0 });
    doc.setGroup(first.id, "Assembly"); doc.setGroup(second.id, "Assembly");
    doc.moveGroup(first.id, { x: 2, y: 1.5, z: 1 });
    expect(doc.elements.find(element => element.id === first.id)).toMatchObject({ x: 2, z: 1 });
    expect(doc.elements.find(element => element.id === second.id)).toMatchObject({ x: 5, z: 1 });
  });
  it("saves and restores a camera scene in RDF", async () => {
    const doc = new CadDocument();
    const scene = doc.addScene("North elevation", { alpha: 1, beta: 2, radius: 15,
      targetX: 3, targetY: 4, targetZ: 5, orthographic: true, sectionEnabled: true, sectionHeight: 2.5 });
    expect(CadDocument.parse(await doc.serialize()).scenes[0]).toEqual(scene);
    doc.removeScene(scene.id);
    expect(doc.scenes).toHaveLength(0);
  });
  it("round trips a mouse-edited position and Y rotation", async () => {
    const doc = new CadDocument(); const wall = doc.add("Wall");
    doc.setNumber(wall.id, "x", 2.25);
    doc.setNumber(wall.id, "z", -1.5);
    doc.setNumber(wall.id, "rotation", 90);
    const reopened = CadDocument.parse(await doc.serialize());
    expect(reopened.elements[0]).toMatchObject({ x: 2.25, z: -1.5, rotation: 90 });
    const legacy = CadDocument.parse(`@prefix cad: <${NS.cad}> . <urn:old> a cad:Wall .`);
    expect(legacy.elements[0].rotation).toBe(0);
  });
  it("recognizes IFCtoLBD walls, slabs, and spaces from linked bounding boxes", async () => {
    const encoded = encodeObj("v 1 -6 2\nv 5 -6 2\nv 1 -4 5\nf 1 2 3\n");
    const input = `@prefix bot: <https://w3id.org/bot#> .
@prefix omg: <https://w3id.org/omg#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix lbd: <https://linkedbuildingdata.org/LBD#> .
@prefix fog: <https://w3id.org/fog#> .
<urn:wall> a bot:Element; owl:sameAs <https://example.org/IfcWallStandardCase_42>; omg:hasGeometry <urn:wall-geometry> .
<urn:slab> a bot:Element; owl:sameAs <https://example.org/IfcSlab_43>; omg:hasGeometry <urn:slab-geometry> .
<urn:space> a bot:Space; owl:sameAs <https://example.org/IfcSpace_44>; omg:hasGeometry <urn:space-geometry> .
<urn:wall-geometry> lbd:hasBoundingBox <urn:bounds>; fog:asObj_v3.0-obj "${encoded}";
  lbd:hasWireframe "MULTILINESTRING Z((1 -6 2, 5 -6 2))" .
<urn:slab-geometry> lbd:hasBoundingBox <urn:bounds>; fog:asObj_v3.0-obj "${encoded}" .
<urn:space-geometry> lbd:hasBoundingBox <urn:bounds>; fog:asObj_v3.0-obj "${encoded}" .
<urn:bounds> lbd:x-min 1; lbd:x-max 5; lbd:y-min -6; lbd:y-max -4; lbd:z-min 2; lbd:z-max 5 .`;
    const doc = CadDocument.parse(input);
    expect(doc.elements.map(e => e.kind)).toEqual(["Wall", "Slab", "Space"]);
    expect(doc.elements[0]).toMatchObject({ imported: true, x: 3, y: 3.5, z: 5, width: 4, height: 3, depth: 2 });
    expect(CadDocument.parse(await doc.serialize()).elements).toEqual(doc.elements);
    doc.setGeometry("urn:wall", { x: 4, y: 4.5, z: 6 });
    expect(doc.elements.find(e => e.id === "urn:wall")).toMatchObject({ x: 4, y: 4.5, z: 6 });
    const moved = doc.store.getQuads(null, null, null, null).find(q => q.subject.value === "urn:wall-geometry" && q.predicate.value.endsWith("asObj_v3.0-obj"))!;
    expect(decodeObj(moved.object.value)).toContain("v 2 -7 3");
    expect(doc.store.getQuads(null, null, null, null).find(q => q.subject.value === "urn:wall-geometry" && q.predicate.value.endsWith("hasWireframe"))?.object.value).toContain("2 -7 3");
    doc.setGeometry("urn:wall", { width: 8 });
    expect(doc.elements.find(e => e.id === "urn:wall")?.width).toBeCloseTo(8);
    doc.setGeometry("urn:wall", { rotation: 90 });
    const rotated = doc.elements.find(e => e.id === "urn:wall")!;
    expect(rotated.rotation).toBe(90);
    expect(rotated.width).toBeCloseTo(2);
    expect(rotated.depth).toBeCloseTo(8);
    expect(CadDocument.parse(await doc.serialize()).elements.find(e => e.id === "urn:wall")).toEqual(rotated);
  });
  it("keeps a door and its opening aligned while retaining the wall link", () => {
    const encoded = encodeObj("v 0 0 0\nv 1 0 0\nv 0 0 2\nf 1 2 3\n");
    const input = `@prefix bot: <https://w3id.org/bot#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix omg: <https://w3id.org/omg#> .
@prefix fog: <https://w3id.org/fog#> .
@prefix lbd: <https://linkedbuildingdata.org/LBD#> .
<urn:wall> bot:hasSubElement <https://example.org/door>, <https://example.org/window> .
<https://example.org/door> owl:sameAs <https://example.org/IfcDoor_1>; omg:hasGeometry <urn:door-geometry> .
<https://example.org/door%3A1> owl:sameAs <https://example.org/IfcOpeningElement_2>; omg:hasGeometry <urn:opening-geometry> .
<https://example.org/window> owl:sameAs <https://example.org/IfcWindow_3>; omg:hasGeometry <urn:window-geometry> .
<urn:door-geometry> lbd:hasBoundingBox <urn:door-bounds>; fog:asObj_v3.0-obj "${encoded}" .
<urn:opening-geometry> lbd:hasBoundingBox <urn:opening-bounds>; fog:asObj_v3.0-obj "${encoded}" .
<urn:window-geometry> lbd:hasBoundingBox <urn:window-bounds>; fog:asObj_v3.0-obj "${encoded}" .
<urn:door-bounds> lbd:x-min 0; lbd:x-max 1; lbd:y-min 0; lbd:y-max 0.2; lbd:z-min 0; lbd:z-max 2 .
<urn:opening-bounds> lbd:x-min 0; lbd:x-max 1; lbd:y-min 0; lbd:y-max 0.2; lbd:z-min 0; lbd:z-max 2 .
<urn:window-bounds> lbd:x-min 0; lbd:x-max 1; lbd:y-min 0; lbd:y-max 0.2; lbd:z-min 0; lbd:z-max 2 .`;
    const doc = CadDocument.parse(input);
    expect(doc.elements.map(e => e.kind)).toEqual(["Door", "Opening", "Window"]);
    expect(doc.hostWall("https://example.org/door")).toBe("urn:wall");
    expect(doc.relatedOpening("https://example.org/door")).toBe("https://example.org/door%3A1");
    doc.setGeometry("https://example.org/door", { x: 2.5 });
    expect(doc.elements.find(e => e.kind === "Door")?.x).toBeCloseTo(2.5);
    expect(doc.elements.find(e => e.kind === "Opening")?.x).toBeCloseTo(2.5);
    expect(doc.hostWall("https://example.org/door")).toBe("urn:wall");
  });
  it("creates hosted doors and windows with linked openings that follow edits", async () => {
    const doc = starterDocument();
    const wall = doc.elements.find(e => e.kind === "Wall")!;
    const door = doc.addHosted("Door", wall.id);
    const window = doc.addHosted("Window", wall.id, { x: wall.x + 1, y: wall.y + .3, z: wall.z });
    expect(doc.hostWall(door.id)).toBe(wall.id);
    expect(window.x).toBeCloseTo(wall.x + 1);
    expect(window.y).toBeCloseTo(wall.y + .3);
    expect(doc.hostedOpenings(wall.id)).toHaveLength(2);
    const openingId = doc.relatedOpening(door.id)!;
    const before = doc.elements.find(e => e.id === openingId)!;
    doc.setGeometry(door.id, { x: door.x + 1, width: door.width * 1.2 });
    const after = doc.elements.find(e => e.id === openingId)!;
    expect(after.x).toBeCloseTo(before.x + 1);
    expect(after.width).toBeCloseTo(before.width * 1.2);
    doc.setGeometry(wall.id, { z: wall.z + 2 });
    expect(doc.elements.find(e => e.id === window.id)?.z).toBeCloseTo(window.z + 2);
    expect(CadDocument.parse(await doc.serialize()).hostedOpenings(wall.id)).toHaveLength(2);
    doc.remove(door.id);
    expect(doc.elements.some(e => e.id === openingId)).toBe(false);
    expect(doc.hostedOpenings(wall.id)).toHaveLength(1);
  });
});
