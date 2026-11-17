import { describe, expect, it } from "vitest";
import { CadDocument, NS } from "./model";
import { prepareObj, readMtl, readObjSurface } from "./obj-import";

const png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=";
const obj = `mtllib panel.mtl
v 0 0 0
v 100 0 0
v 100 100 0
v 0 100 0
vt 0 0
vt 1 0
vt 1 1
vt 0 1
usemtl Oak
f -4/-4 -3/-3 -2/-2 -1/-1`;
const mtl = `newmtl Oak
Kd 0.5 0.25 0.0
d 0.8
map_Kd textures/oak.png`;

describe("OBJ and image import", () => {
  it("converts units and axis while retaining material runs and UVs", () => {
    const converted = prepareObj(obj, "cm", "y");
    expect(converted).toContain("v 1 0 1");
    const surface = readObjSurface(converted);
    expect(surface.indices).toHaveLength(6);
    expect(surface.runs).toEqual([{ name: "Oak", start: 0, count: 6 }]);
    expect(surface.uvs.slice(0, 6)).toEqual([0, 0, 1, 0, 1, 1]);
    expect(readMtl(mtl)).toEqual([{ name: "Oak", color: "#804000", opacity: .8, imageName: "textures/oak.png" }]);
    expect(readMtl("newmtl Oak\nmap_Kd -s 1 1 1 textures/oak.png")[0].imageName).toBe("textures/oak.png");
  });
  it("stores OBJ, MTL materials, and the image in Turtle and keeps them after edits", async () => {
    const doc = new CadDocument();
    const element = doc.importObj("Panel", obj, "cm", "y", mtl, { "oak.png": png });
    expect(element).toMatchObject({ kind: "Mesh", imported: true, width: 1, height: 1 });
    expect(doc.importedMesh(element.id)?.uvs).toHaveLength(12);
    const material = doc.objMaterials(element.id).get("Oak")!;
    expect(material).toMatchObject({ color: "#804000", opacity: .8, texture: "image", imageData: png,
      tileWidth: 1, tileHeight: 1 });
    expect(doc.value(element.id, NS.cad + "sourceUnit")).toBe("cm");
    const replacement = doc.addImageMaterial("New panel", png);
    doc.assignMaterial(element.id, replacement.id);
    expect(doc.objMaterials(element.id).get("Oak")?.id).toBe(replacement.id);
    doc.setGeometry(element.id, { x: 3 });
    const reopened = CadDocument.parse(await doc.serialize());
    expect(reopened.elements.find(item => item.id === element.id)?.x).toBeCloseTo(3);
    expect(reopened.objMaterials(element.id).get("Oak")?.id).toBe(replacement.id);
    expect(reopened.objMaterials(element.id).get("Oak")?.imageData).toBe(png);
    expect(reopened.importedMesh(element.id)?.runs[0].name).toBe("Oak");
    reopened.remove(element.id);
    expect(reopened.elements).toHaveLength(0);
  });
  it("imports a standalone PNG as a reusable material", async () => {
    const doc = new CadDocument();
    const material = doc.addImageMaterial("Brick", png);
    expect(CadDocument.parse(await doc.serialize()).material(material.id)?.texture).toBe("image");
    expect(() => doc.addImageMaterial("Bad", "data:text/html;base64,PHNjcmlwdD4=")).toThrow();
  });
  it("keeps separate OBJ material runs and accepts faces without UVs", () => {
    const source = `v 0 0 0\nv 1 0 0\nv 0 1 0\nv 0 0 1\nusemtl Red\nf 1 2 3\nusemtl Blue\nf 1 3 4`;
    const doc = new CadDocument();
    const element = doc.importObj("Two finishes", source, "m", "z",
      "newmtl Red\nKd 1 0 0\nnewmtl Blue\nKd 0 0 1");
    expect(doc.importedMesh(element.id)?.runs).toEqual([
      { name: "Red", start: 0, count: 3 }, { name: "Blue", start: 3, count: 3 }
    ]);
    expect(doc.importedMesh(element.id)?.uvs.every(value => Number.isNaN(value))).toBe(true);
    expect(doc.objMaterials(element.id).get("Red")?.color).toBe("#ff0000");
    expect(doc.objMaterials(element.id).get("Blue")?.color).toBe("#0000ff");
  });
});
