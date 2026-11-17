import { describe, expect, it } from "vitest";
import { CadDocument } from "./model";
import { clampHostedPosition } from "./hosted-move";

describe("hosted insert movement", () => {
  it("keeps doors on their wall when dragged in a front view", () => {
    const doc = new CadDocument();
    const wall = doc.add("Wall", { x: 2, y: 1.5, z: 4 });
    const door = doc.addHosted("Door", wall.id);
    const target = clampHostedPosition(wall, door, { x: 100, y: 100, z: -100 });
    expect(target.z).toBe(wall.z);
    expect(target.x).toBeCloseTo(wall.x + (wall.width - door.width) / 2);
    expect(target.y).toBeCloseTo(wall.y + (wall.height - door.height) / 2);
    doc.moveGroup(door.id, target);
    expect(doc.elements.find(e => e.id === door.id)).toMatchObject(target);
    expect(doc.elements.find(e => e.id === doc.relatedOpening(door.id))).toMatchObject(target);
  });
  it("moves along the long axis of an imported wall", () => {
    const wall = { id: "wall", kind: "Wall", label: "Wall", x: 0, y: 1.5, z: 3,
      width: .2, height: 3, depth: 6, rotation: 0, imported: true } as const;
    const window = { id: "window", kind: "Window", label: "Window", x: 0, y: 1.5, z: 3,
      width: 1.2, height: 1.2, depth: .1, rotation: 90 } as const;
    expect(clampHostedPosition(wall, window, { x: 8, y: 1, z: 4 })).toEqual({ x: 0, y: 1, z: 4 });
  });
});
