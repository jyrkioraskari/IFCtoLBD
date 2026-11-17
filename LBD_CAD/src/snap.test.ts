import { describe, expect, it } from "vitest";
import { moveXZ, snapXZ } from "./snap";
import type { Element } from "./model";

describe("drawing snaps", () => {
  it("moves continuously by default and snaps only when requested", () => {
    expect(moveXZ(1.13, -2.08, [], false)).toEqual({ x: 1.13, z: -2.08 });
    expect(moveXZ(1.13, -2.08, [], true)).toEqual({ x: 1.25, z: -2 });
  });
  it("prefers nearby rotated corners and uses the metric grid elsewhere", () => {
    const wall: Element = { id: "wall", kind: "Wall", label: "Wall", x: 2, y: 1.5, z: 3,
      width: 4, height: 3, depth: .2, rotation: 90 };
    const corner = snapXZ(2.12, 5.03, [wall], .25, .2);
    expect(corner.source).toBe("corner");
    expect(corner.x).toBeCloseTo(2.1);
    expect(corner.z).toBeCloseTo(5);
    expect(snapXZ(7.12, 8.13, [wall], .25, .2)).toEqual({ x: 7, z: 8.25, source: "grid" });
    expect(snapXZ(2.12, 5.03, [wall], .25, .2, "wall").source).toBe("grid");
  });
});
