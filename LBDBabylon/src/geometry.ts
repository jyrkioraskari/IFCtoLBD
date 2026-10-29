import { Mesh, VertexData } from "@babylonjs/core";

export type IfcPoint = readonly [number, number, number];
export interface ParsedObj { vertices: IfcPoint[]; indices: number[] }

/** Parses OBJ into double-precision JS values but does not create GPU buffers. */
export function parseObj(obj: string): ParsedObj {
  const indices: number[] = [];
  const vertices: IfcPoint[] = [];
  for (const line of obj.split(/\r?\n/)) {
    const p = line.trim().split(/\s+/);
    if (p[0] === "v" && p.length >= 4) vertices.push([Number(p[1]), Number(p[2]), Number(p[3])]);
    if (p[0] === "f" && p.length >= 4) {
      const face = p.slice(1).map(v => Number(v.split("/")[0]) - 1);
      for (let i = 1; i < face.length - 1; i++) indices.push(face[0], face[i], face[i + 1]);
    }
  }
  if (!vertices.length || !indices.length || vertices.some(v => v.some(n => !Number.isFinite(n)))
      || indices.some(i => !Number.isInteger(i) || i < 0 || i >= vertices.length))
    throw new Error("OBJ has no usable vertices and faces");
  return { vertices, indices };
}

/** One origin for the complete loaded model; never center individual meshes. */
export function chooseModelOrigin(objects: readonly ParsedObj[]): IfcPoint {
  let minX = Infinity, minY = Infinity, minZ = Infinity, maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;
  for (const object of objects) for (const [x, y, z] of object.vertices) {
    minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
  }
  if (!Number.isFinite(minX)) return [0, 0, 0];
  return [minX + (maxX - minX) / 2, minY + (maxY - minY) / 2, minZ + (maxZ - minZ) / 2];
}

/** Normalizes source coordinates before selecting the shared floating origin. */
export function scaleObj(object: ParsedObj, factor: number): ParsedObj {
  if (factor === 1) return object;
  return { vertices: object.vertices.map(([x, y, z]) => [x * factor, y * factor, z * factor]), indices: object.indices };
}

/** Builds float GPU positions only after subtracting the shared double-precision IFC origin. */
export function objToMesh(name: string, obj: string | ParsedObj, scene: import("@babylonjs/core").Scene,
    origin: IfcPoint = [0, 0, 0], sourceMetadata?: object, unitToMetres = 1): Mesh {
  const parsed = typeof obj === "string" ? parseObj(obj) : obj;
  const positions: number[] = [];
  parsed.vertices.forEach(v => positions.push(...ifcToBabylonPosition([
    (v[0] - origin[0]) * unitToMetres, (v[1] - origin[1]) * unitToMetres, (v[2] - origin[2]) * unitToMetres
  ])));
  const mesh = new Mesh(name, scene);
  const data = new VertexData(); data.positions = positions; data.indices = parsed.indices; data.applyToMesh(mesh);
  mesh.metadata = { ifcToLbd: { coordinateSpace: "render-rebased", renderOriginIfc: [...origin],
    sourceCoordinateSpace: "local-engineering", ...sourceMetadata } };
  mesh.refreshBoundingInfo();
  return mesh;
}

/** Preserve handedness: (IFC x, y, z) -> Babylon (x, z, -y), exactly once. */
export function ifcToBabylonPosition([x, y, z]: readonly number[]): [number, number, number] { return [x, z, -y]; }

export function decodeBase64(value: string): string {
  const binary = atob(value.replace(/\s/g, ""));
  const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
