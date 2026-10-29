import { describe, expect, it } from "vitest";
import { icon } from "./icons";

describe("toolbar icons", () => {
  it("emits a valid compact negative decimal in the hide path", () => {
    const svg = icon("hide");
    expect(svg).toContain("9.5 7-.4 1.1");
    expect(svg).not.toMatch(/-\s+\./);
  });
});
