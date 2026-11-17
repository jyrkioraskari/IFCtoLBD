import { describe, expect, it } from "vitest";
import { contrastEdge, defaultFinish } from "./finishes";

describe("architectural finishes", () => {
  it("uses distinct finishes for walls, doors, and windows", () => {
    expect(defaultFinish("Wall")).toBe("plaster");
    expect(defaultFinish("Door")).toBe("timber");
    expect(defaultFinish("Window")).toBe("glass");
  });
  it("uses dark borders on light material and light borders on dark material", () => {
    expect(contrastEdge("#e8dfd1")).toBe("#1c3944");
    expect(contrastEdge("#20323a")).toBe("#e3f3f4");
  });
});
