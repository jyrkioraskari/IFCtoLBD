import type { Element, Field } from "./model";

export type FaceAxis = "x" | "y" | "z";
export interface BoxFace { axis: FaceAxis; sign: 1 | -1 }

export function boxFaceFromNormal(normal: { x: number; y: number; z: number }, rotation: number): BoxFace {
  const angle = rotation * Math.PI / 180;
  const local = [normal.x * Math.cos(angle) - normal.z * Math.sin(angle), normal.y,
    normal.x * Math.sin(angle) + normal.z * Math.cos(angle)];
  const index = local.reduce((best, value, next) => Math.abs(value) > Math.abs(local[best]) ? next : best, 0);
  return { axis: (["x", "y", "z"] as const)[index], sign: local[index] >= 0 ? 1 : -1 };
}

/** Extends one face of a rotated box and keeps its opposite face in place. */
export function pullBoxFace(element: Element, face: BoxFace, distance: number): Partial<Pick<Element, Field>> {
  if (!Number.isFinite(distance)) throw new Error("Push/Pull distance must be finite");
  const field = face.axis === "x" ? "width" : face.axis === "y" ? "height" : "depth";
  const size = element[field] + distance;
  if (size < .001) throw new Error("Push/Pull would collapse the solid");
  const shift = face.sign * distance / 2;
  const angle = element.rotation * Math.PI / 180;
  if (face.axis === "x") return { width: size, x: element.x + shift * Math.cos(angle),
    z: element.z - shift * Math.sin(angle) };
  if (face.axis === "z") return { depth: size, x: element.x + shift * Math.sin(angle),
    z: element.z + shift * Math.cos(angle) };
  return { height: size, y: element.y + shift };
}
