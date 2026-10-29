import { describe, expect, it } from "vitest";
import { distanceToBounds, PROXIMITY_DEFAULT_DISTANCE, PROXIMITY_FAR_VISIBILITY, proximityVisibility } from "./proximity";

describe("proximity visibility", () => {
  it("uses a fixed two-metre near radius", () => {
    expect(PROXIMITY_DEFAULT_DISTANCE).toBe(2);
  });

  it("measures the nearest point of an entire bounding box", () => {
    const min = { x: 1, y: 2, z: 3 }, max = { x: 4, y: 5, z: 6 };
    expect(distanceToBounds({ x: 2, y: 3, z: 4 }, min, max)).toBe(0);
    expect(distanceToBounds({ x: 0, y: 3, z: 4 }, min, max)).toBe(1);
    expect(distanceToBounds({ x: 0, y: 1, z: 3 }, min, max)).toBeCloseTo(Math.SQRT2);
  });

  it("keeps near objects whole and softly fades distant ones", () => {
    expect(proximityVisibility(8, 8)).toBe(1);
    expect(proximityVisibility(9, 8)).toBeCloseTo((1 + PROXIMITY_FAR_VISIBILITY) / 2);
    expect(proximityVisibility(10, 8)).toBe(PROXIMITY_FAR_VISIBILITY);
    expect(proximityVisibility(100, 8)).toBe(PROXIMITY_FAR_VISIBILITY);
  });

  it("clamps invalid opacity input", () => {
    expect(proximityVisibility(100, 0, 2)).toBe(1);
    expect(proximityVisibility(100, 0, -1)).toBe(0);
  });
});
