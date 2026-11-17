import { Mesh, VertexBuffer, VertexData, type Scene } from "@babylonjs/core";
import type { Element } from "./model";

type Point = [number, number, number];

function clip(polygon: Point[], axis: number, limit: number, keepBelow: boolean): Point[] {
  const inside = (point: Point) => keepBelow ? point[axis] <= limit + 1e-8 : point[axis] >= limit - 1e-8;
  const result: Point[] = [];
  for (let i = 0; i < polygon.length; i++) {
    const current = polygon[i], next = polygon[(i + 1) % polygon.length];
    const a = inside(current), b = inside(next);
    if (a) result.push(current);
    if (a !== b && Math.abs(next[axis] - current[axis]) > 1e-10) {
      const ratio = (limit - current[axis]) / (next[axis] - current[axis]);
      result.push(current.map((value, index) => value + ratio * (next[index] - value)) as Point);
    }
  }
  return result;
}

function triangles(polygon: Point[], positions: number[], indices: number[]): void {
  for (let i = 1; i + 1 < polygon.length; i++) {
    const a = polygon[0], b = polygon[i], c = polygon[i + 1];
    const area = Math.hypot(
      (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]),
      (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]),
      (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]));
    if (area < 1e-9) continue;
    const start = positions.length / 3;
    positions.push(...a, ...b, ...c);
    indices.push(start, start + 1, start + 2);
  }
}

/** Clip mesh triangles directly when a non-manifold IFC wall defeats CSG. */
export function cutSurfaceOpenings(base: Mesh, wall: Element, openings: readonly Element[], scene: Scene): Mesh {
  let positions = Array.from(base.getVerticesData(VertexBuffer.PositionKind) ?? []);
  let indices = Array.from(base.getIndices() ?? []);
  const wallAngle = wall.rotation * Math.PI / 180;
  for (const opening of openings) {
    const dx = opening.x - wall.x, dz = opening.z - wall.z;
    const centerX = dx * Math.cos(wallAngle) - dz * Math.sin(wallAngle);
    const centerZ = dx * Math.sin(wallAngle) + dz * Math.cos(wallAngle);
    const centerY = opening.y - wall.y;
    const angle = (opening.rotation - wall.rotation) * Math.PI / 180;
    const cosine = Math.cos(angle), sine = Math.sin(angle);
    const toCutter = ([x, y, z]: Point): Point => {
      const px = x - centerX, pz = z - centerZ;
      return [px * cosine - pz * sine, y - centerY, px * sine + pz * cosine];
    };
    const fromCutter = ([x, y, z]: Point): Point =>
      [centerX + x * cosine + z * sine, centerY + y, centerZ - x * sine + z * cosine];
    const nextPositions: number[] = [], nextIndices: number[] = [];
    const half = [opening.width / 2, opening.height / 2, opening.depth / 2];
    for (let i = 0; i < indices.length; i += 3) {
      let inside: Point[] = [0, 1, 2].map(offset => {
        const index = indices[i + offset] * 3;
        return toCutter([positions[index], positions[index + 1], positions[index + 2]]);
      });
      for (let axis = 0; axis < 3 && inside.length >= 3; axis++) {
        for (const side of [-1, 1]) {
          const limit = side * half[axis];
          const outside = clip(inside, axis, limit, side === -1);
          triangles(outside.map(fromCutter), nextPositions, nextIndices);
          inside = clip(inside, axis, limit, side === 1);
          if (inside.length < 3) break;
        }
      }
    }
    // Four reveal faces connect the front and back wall surfaces around the void.
    const alongZ = Math.abs(sine) > Math.abs(cosine);
    const thickness = alongZ ? wall.width : wall.depth;
    const t = thickness / 2;
    const reveal = (a: Point, b: Point, c: Point, d: Point) => {
      triangles([a, b, c, d].map(fromCutter), nextPositions, nextIndices);
    };
    for (const x of [-half[0], half[0]])
      reveal([x, -half[1], -t], [x, half[1], -t], [x, half[1], t], [x, -half[1], t]);
    for (const y of [-half[1], half[1]])
      reveal([-half[0], y, -t], [half[0], y, -t], [half[0], y, t], [-half[0], y, t]);
    positions = nextPositions; indices = nextIndices;
  }
  const mesh = new Mesh(wall.id, scene);
  const data = new VertexData(); data.positions = positions; data.indices = indices;
  const normals: number[] = []; VertexData.ComputeNormals(positions, indices, normals);
  data.normals = normals; data.applyToMesh(mesh);
  return mesh;
}
