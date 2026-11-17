import { SubMesh, VertexBuffer, VertexData, type Mesh } from "@babylonjs/core";
import type { MaterialDefinition } from "./model";

export function projectTriangleUvs(points: number[][], material: MaterialDefinition): number[] {
  const [a, b, c] = points;
  const ab = b.map((value, axis) => value - a[axis]);
  const ac = c.map((value, axis) => value - a[axis]);
  const normal = [ab[1] * ac[2] - ab[2] * ac[1], ab[2] * ac[0] - ab[0] * ac[2],
    ab[0] * ac[1] - ab[1] * ac[0]];
  const dominant = normal.reduce((best, value, axis) => Math.abs(value) > Math.abs(normal[best]) ? axis : best, 0);
  const axes = dominant === 0 ? [2, 1] : dominant === 1 ? [0, 2] : [0, 1];
  const angle = material.grainDegrees * Math.PI / 180, cosine = Math.cos(angle), sine = Math.sin(angle);
  return points.flatMap(vertex => {
    const u = vertex[axes[0]], v = vertex[axes[1]];
    return [(u * cosine - v * sine) / material.tileWidth, (u * sine + v * cosine) / material.tileHeight];
  });
}

/** Planar metre-based UVs for imported OBJ triangles and edited wall cuts. */
export function applyProjectedUvs(mesh: Mesh, material: MaterialDefinition): void {
  const source = mesh.getVerticesData(VertexBuffer.PositionKind);
  const triangles = mesh.getIndices();
  if (!source?.length || !triangles?.length) return;
  const positions: number[] = [], indices: number[] = [], uvs: number[] = [];
  const point = (index: number) => [source[index * 3], source[index * 3 + 1], source[index * 3 + 2]];
  for (let i = 0; i + 2 < triangles.length; i += 3) {
    const a = point(triangles[i]), b = point(triangles[i + 1]), c = point(triangles[i + 2]);
    const mapped = projectTriangleUvs([a, b, c], material);
    for (const [corner, vertex] of [a, b, c].entries()) {
      positions.push(...vertex);
      uvs.push(mapped[corner * 2], mapped[corner * 2 + 1]);
      indices.push(indices.length);
    }
  }
  const normals: number[] = [];
  VertexData.ComputeNormals(positions, indices, normals);
  const data = new VertexData();
  data.positions = positions; data.indices = indices; data.uvs = uvs; data.normals = normals;
  data.applyToMesh(mesh);
  mesh.subMeshes = [];
  new SubMesh(0, 0, positions.length / 3, 0, indices.length, mesh);
}
