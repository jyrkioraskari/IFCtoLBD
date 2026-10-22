import { describe, expect, it } from "vitest";
import { chooseModelOrigin, ifcToBabylonPosition, parseObj } from "./geometry";

describe("shared rendering origin", () => {
  it("preserves centimetre detail and separation at million-unit coordinates", () => {
    const first = parseObj("v 5000000 6000000 100\nv 5000000.01 6000000 100\nv 5000000 6000000.01 100\nf 1 2 3");
    const second = parseObj("v 5000010 6000020 100\nv 5000010.01 6000020 100\nv 5000010 6000020.01 100\nf 1 2 3");
    const origin = chooseModelOrigin([first, second]);
    const relative = first.vertices.map(v => ifcToBabylonPosition(v.map((n, i) => n - origin[i]) as [number, number, number]));
    expect(relative[1][0] - relative[0][0]).toBeCloseTo(0.01, 9);
    const gpuPositions = Float32Array.from(relative.flat());
    expect(gpuPositions[3] - gpuPositions[0]).toBeCloseTo(0.01, 5);
    const firstX = first.vertices[0][0] - origin[0], secondX = second.vertices[0][0] - origin[0];
    expect(secondX - firstX).toBeCloseTo(10, 12);
  });

  it("uses one model origin rather than one origin per mesh", () => {
    const a = parseObj("v 1000000 0 0\nv 1000001 0 0\nv 1000000 1 0\nf 1 2 3");
    const b = parseObj("v 1000100 0 0\nv 1000101 0 0\nv 1000100 1 0\nf 1 2 3");
    expect(chooseModelOrigin([a, b])).toEqual([1000050.5, 0.5, 0]);
  });
});
