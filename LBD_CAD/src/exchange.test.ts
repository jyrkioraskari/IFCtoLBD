import { describe, expect, it } from "vitest";
import { exportDxfPlan, exportObj, exportQuantitiesCsv, exportStl, type ExportPart } from "./exchange";

const triangle: ExportPart = { name: "Wall 1", layer: "Level 0", positions: [0, 0, 0, 2, 0, 0, 0, 2, 0], indices: [0, 1, 2] };

describe("model exchange", () => {
  it("exports geometry with metric coordinates", () => {
    expect(exportObj([triangle])).toContain("f 1 2 3");
    expect(exportStl([triangle])).toContain("facet normal 0 0 1");
    expect(exportDxfPlan([triangle])).toContain("$INSUNITS\n70\n6");
  });
  it("quotes quantity labels in CSV", () => {
    expect(exportQuantitiesCsv([{ name: 'Wall "A"', kind: "Wall", layer: "Level 0", area: 4, closed: false }]))
      .toContain('"Wall ""A"""');
  });
});
