import { describe, expect, it } from "vitest";
import { initialElementTexture } from "./texture-presets";

describe("texture defaults", () => {
  it("keeps imported IFC appearance unchanged until a texture is assigned", () => {
    expect(initialElementTexture("plaster", true)).toBe("none");
    expect(initialElementTexture("concrete", true)).toBe("none");
    expect(initialElementTexture("plaster", false)).toBe("plaster");
  });
});
