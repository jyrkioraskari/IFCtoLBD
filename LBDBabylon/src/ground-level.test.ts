import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { decodeBase64, parseObj } from "./geometry";
import { entryGroundLevel } from "./renderer";
import { parseTurtle } from "./rdf";

describe("Duplex entry ground", () => {
  it("aligns the single exterior plane with all four door bottoms and both entry slabs", async () => {
    const model = await parseTurtle(readFileSync(new URL("../../Duplex_LBD.ttl", import.meta.url), "utf8"));
    const verticesFor = (id: string) => {
      const source = model.geometries.find(geometry =>
        geometry.kind === "embedded-obj" && decodeURIComponent(geometry.element).endsWith(`:${id}`));
      expect(source?.payload, `OBJ geometry for ${id}`).toBeDefined();
      return parseObj(decodeBase64(source!.payload!)).vertices;
    };
    const doorBottoms = ["146678", "146596", "171853", "171975"]
      .map(id => Math.min(...verticesFor(id).map(vertex => vertex[2])));
    const entrySlabTops = ["141232", "143106"]
      .map(id => Math.max(...verticesFor(id).map(vertex => vertex[2])));

    expect(doorBottoms).toEqual([0, 0, 0, 0]);
    expect(entrySlabTops).toEqual([0, 0]);
    expect(entryGroundLevel(entrySlabTops, [], doorBottoms)).toMatchObject({
      level: 0, nearestFloorTop: 0, outlierDoorBottoms: [], aligned: true
    });
  });
});
