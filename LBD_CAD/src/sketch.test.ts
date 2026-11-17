import { describe, expect, it } from "vitest";
import { parseCoordinates, parseDegrees, parseLength, parseRectangleDimensions, rectangleFromCorners } from "./sketch";

describe("measured sketch input", () => {
  it("creates a rectangle from reversed corners and parses metric dimensions", () => {
    expect(rectangleFromCorners({ x: 5, z: 3 }, { x: 1, z: -1 }))
      .toEqual({ x: 3, z: 1, width: 4, depth: 4 });
    expect(parseRectangleDimensions("4.5, 3 m")).toEqual([4.5, 3]);
    expect(parseRectangleDimensions("4 x 2.25")).toEqual([4, 2.25]);
    expect(parseRectangleDimensions("4;0")).toBeUndefined();
    expect(parseLength("2.8 m")).toBe(2.8);
    expect(parseCoordinates("-2, 3.25 m")).toEqual([-2, 3.25]);
    expect(parseCoordinates("2, bad")).toBeUndefined();
    expect(parseDegrees("-45°")).toBe(-45);
  });
});
