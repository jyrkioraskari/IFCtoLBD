import type { Element } from "./model";
import { featureEdges, type Segment3 } from "./feature-edges";

/** Hide CSG face seams that continue vertically beyond a hosted opening. */
export function wallFeatureEdges(positions: ArrayLike<number>, indices: ArrayLike<number>, wall: Element,
  openings: readonly Element[]): Segment3[] {
  const borders = featureEdges(positions, indices);
  const wallAngle = wall.rotation * Math.PI / 180;
  return borders.flatMap(([start, end]) => {
    if (Math.abs(start[0] - end[0]) > 1e-4 || Math.abs(start[2] - end[2]) > 1e-4) return [[start, end] as Segment3];
    for (const opening of openings) {
      const dx = opening.x - wall.x, dz = opening.z - wall.z;
      const centerX = dx * Math.cos(wallAngle) - dz * Math.sin(wallAngle);
      const centerZ = dx * Math.sin(wallAngle) + dz * Math.cos(wallAngle);
      const angle = (opening.rotation - wall.rotation) * Math.PI / 180;
      const along = (start[0] - centerX) * Math.cos(angle) - (start[2] - centerZ) * Math.sin(angle);
      if (Math.abs(Math.abs(along) - opening.width / 2) > 1e-4) continue;
      const lower = opening.y - wall.y - opening.height / 2;
      const upper = opening.y - wall.y + opening.height / 2;
      const min = Math.max(Math.min(start[1], end[1]), lower);
      const max = Math.min(Math.max(start[1], end[1]), upper);
      if (max - min < 1e-4) return [];
      const first: typeof start = [start[0], start[1] <= end[1] ? min : max, start[2]];
      const second: typeof end = [end[0], start[1] <= end[1] ? max : min, end[2]];
      return [[first, second] as Segment3];
    }
    return [[start, end] as Segment3];
  });
}
