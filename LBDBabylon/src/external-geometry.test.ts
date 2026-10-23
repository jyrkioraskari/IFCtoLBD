import { describe, expect, it } from "vitest";
import { normalizeAssetReference } from "./renderer";

describe("external geometry package paths", () => {
  it("normalizes relative and artifact URI paths", () => {
    expect(normalizeAssetReference("geometry/wall.obj")).toBe("geometry/wall.obj");
    expect(normalizeAssetReference("ifctolbd://models/a/geometry/wall.obj")).toBe("a/geometry/wall.obj");
  });
  it("rejects traversal and Windows separators", () => {
    expect(() => normalizeAssetReference("../secret.obj")).toThrow(/Unsafe/);
    expect(() => normalizeAssetReference("geometry/%2e%2e/secret.obj")).toThrow(/Unsafe/);
    expect(() => normalizeAssetReference("geometry\\wall.obj")).toThrow(/Unsafe/);
  });
});
