import { Mesh, VertexData } from "@babylonjs/core";

/** IFCtoLBD OBJ is authored in IFC's X/Y horizontal, Z-up coordinate system. */
export function objToMesh(name: string, obj: string, scene: import("@babylonjs/core").Scene): Mesh {
  const positions: number[] = [], indices: number[] = [];
  const vertices: number[][] = [];
  for (const line of obj.split(/\r?\n/)) {
    const p = line.trim().split(/\s+/);
    if (p[0] === "v" && p.length >= 4) vertices.push([Number(p[1]), Number(p[2]), Number(p[3])]);
    if (p[0] === "f" && p.length >= 4) {
      const face = p.slice(1).map(v => Number(v.split("/")[0]) - 1);
      for (let i = 1; i < face.length - 1; i++) indices.push(face[0], face[i], face[i + 1]);
    }
  }
  if (!vertices.length || !indices.length || vertices.some(v => v.some(n => !Number.isFinite(n)))) throw new Error("OBJ has no usable vertices and faces");
  // Preserve handedness: (IFC x, y, z) -> Babylon (x, z, -y), exactly once.
  vertices.forEach(v => positions.push(...ifcToBabylonPosition(v)));
  const mesh = new Mesh(name, scene); const data = new VertexData(); data.positions = positions; data.indices = indices; data.applyToMesh(mesh); mesh.refreshBoundingInfo();
  return mesh;
}
export function ifcToBabylonPosition([x, y, z]: number[]): [number, number, number] { return [x, z, -y]; }
export function decodeBase64(value: string): string {
  const binary = atob(value.replace(/\s/g, "")); const bytes = Uint8Array.from(binary, c => c.charCodeAt(0)); return new TextDecoder().decode(bytes);
}
