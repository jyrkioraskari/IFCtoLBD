import { describe, expect, it } from "vitest";
import { CadDocument } from "./model";
import { boxFaceFromNormal, pullBoxFace } from "./solid-edit";

describe("face Push/Pull", () => {
  it("keeps the opposite side fixed and persists the edit in Turtle", async () => {
    const doc = new CadDocument();
    const box = doc.add("Box");
    doc.setGeometry(box.id, pullBoxFace(box, { axis: "x", sign: 1 }, 2));
    const reopened = CadDocument.parse(await doc.serialize()).elements[0];
    expect(reopened.width).toBe(3);
    expect(reopened.x).toBe(1);
    expect(reopened.x - reopened.width / 2).toBe(-.5);
  });

  it("pulls a rotated side face along its local normal", () => {
    const doc = new CadDocument();
    const box = doc.add("Box");
    doc.setGeometry(box.id, { rotation: 90 });
    const change = pullBoxFace(doc.elements[0], { axis: "x", sign: 1 }, 2);
    expect(change.width).toBe(3);
    expect(change.x).toBeCloseTo(0);
    expect(change.z).toBeCloseTo(-1);
    expect(boxFaceFromNormal({ x: 0, y: 0, z: -1 }, 90)).toEqual({ axis: "x", sign: 1 });
  });

  it("moves the lower face downward and rejects a collapsed solid", () => {
    const doc = new CadDocument();
    const box = doc.add("Box");
    expect(pullBoxFace(box, { axis: "y", sign: -1 }, 1)).toEqual({ height: 2, y: -.5 });
    expect(pullBoxFace(box, { axis: "z", sign: -1 }, .5)).toEqual({ depth: 1.5, x: 0, z: -.25 });
    expect(() => pullBoxFace(box, { axis: "y", sign: 1 }, -1)).toThrow("collapse");
  });
});
