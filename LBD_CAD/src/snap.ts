import type { Element } from "./model";

export interface SnapPoint { x: number; z: number; source: "corner" | "midpoint" | "center" | "grid" }

/** Keep placement continuous unless the user has explicitly enabled snapping. */
export function moveXZ(x: number, z: number, elements: readonly Element[], snapping: boolean,
    excludeId?: string, tolerance = .12): { x: number; z: number } {
  if (!snapping) return { x, z };
  const point = snapXZ(x, z, elements, .25, tolerance, excludeId);
  return { x: point.x, z: point.z };
}

export function snapXZ(x: number, z: number, elements: readonly Element[], grid = .25,
    tolerance = .2, excludeId?: string): SnapPoint {
  let nearest: SnapPoint | undefined;
  let distance = tolerance;
  for (const element of elements) {
    if (element.id === excludeId || element.kind === "Opening" || element.kind === "Space") continue;
    const halfX = element.width / 2, halfZ = element.depth / 2;
    const angle = element.rotation * Math.PI / 180;
    const cosine = Math.cos(angle), sine = Math.sin(angle);
    const local: Array<[number, number, SnapPoint["source"]]> = [
      [0, 0, "center"], [-halfX, -halfZ, "corner"], [-halfX, halfZ, "corner"],
      [halfX, -halfZ, "corner"], [halfX, halfZ, "corner"],
      [-halfX, 0, "midpoint"], [halfX, 0, "midpoint"], [0, -halfZ, "midpoint"], [0, halfZ, "midpoint"]
    ];
    for (const [px, pz, source] of local) {
      const candidate = { x: element.x + px * cosine + pz * sine,
        z: element.z - px * sine + pz * cosine, source };
      const nextDistance = Math.hypot(candidate.x - x, candidate.z - z);
      if (nextDistance < distance) { nearest = candidate; distance = nextDistance; }
    }
  }
  if (nearest) return nearest;
  return { x: Math.round(x / grid) * grid, z: Math.round(z / grid) * grid, source: "grid" };
}
