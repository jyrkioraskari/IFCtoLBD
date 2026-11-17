import type { Element } from "./model";

/** Keep a dragged insert on its host wall and within the usable wall face. */
export function clampHostedPosition(wall: Element, insert: Element,
  target: { x: number; y: number; z: number }): { x: number; y: number; z: number } {
  const angle = wall.rotation * Math.PI / 180;
  const cosine = Math.cos(angle), sine = Math.sin(angle);
  const dx = target.x - wall.x, dz = target.z - wall.z;
  const alongZ = wall.imported && wall.depth > wall.width;
  const run = alongZ ? wall.depth : wall.width;
  const local = alongZ ? dx * sine + dz * cosine : dx * cosine - dz * sine;
  const halfTravel = Math.max(0, (run - insert.width) / 2);
  const position = Math.max(-halfTravel, Math.min(halfTravel, local));
  const halfHeightTravel = Math.max(0, (wall.height - insert.height) / 2);
  return { x: wall.x + position * (alongZ ? sine : cosine),
    y: wall.y + Math.max(-halfHeightTravel, Math.min(halfHeightTravel, target.y - wall.y)),
    z: wall.z + position * (alongZ ? cosine : -sine) };
}
