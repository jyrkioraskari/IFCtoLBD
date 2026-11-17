import type { Element } from "./model";

/** Keep a dragged insert on its host wall and within the usable wall face. */
export function clampHostedPosition(wall: Element, insert: Element,
  target: { x: number; y: number; z: number }, anchor?: { x: number; z: number }): { x: number; y: number; z: number } {
  const angle = wall.rotation * Math.PI / 180;
  const cosine = Math.cos(angle), sine = Math.sin(angle);
  const dx = target.x - wall.x, dz = target.z - wall.z;
  const alongZ = wall.imported && wall.depth > wall.width;
  const run = alongZ ? wall.depth : wall.width;
  const local = alongZ ? dx * sine + dz * cosine : dx * cosine - dz * sine;
  const runSize = alongZ && insert.imported ? insert.depth : insert.width;
  const halfTravel = Math.max(0, (run - runSize) / 2);
  const position = Math.max(-halfTravel, Math.min(halfTravel, local));
  const anchorX = (anchor?.x ?? wall.x) - wall.x, anchorZ = (anchor?.z ?? wall.z) - wall.z;
  const thickness = alongZ ? anchorX * cosine - anchorZ * sine : anchorX * sine + anchorZ * cosine;
  const halfHeightTravel = Math.max(0, (wall.height - insert.height) / 2);
  return { x: wall.x + position * (alongZ ? sine : cosine) + thickness * (alongZ ? cosine : sine),
    y: wall.y + Math.max(-halfHeightTravel, Math.min(halfHeightTravel, target.y - wall.y)),
    z: wall.z + position * (alongZ ? cosine : -sine) + thickness * (alongZ ? -sine : cosine) };
}
