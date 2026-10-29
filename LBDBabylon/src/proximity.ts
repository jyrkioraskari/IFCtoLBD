export const PROXIMITY_DEFAULT_DISTANCE = 2;
export const PROXIMITY_FAR_VISIBILITY = .14;

export interface Point3 { x: number; y: number; z: number }

/** Shortest distance from a point to an axis-aligned world-space bounding box. */
export function distanceToBounds(point: Point3, minimum: Point3, maximum: Point3) {
  const dx = Math.max(minimum.x - point.x, 0, point.x - maximum.x);
  const dy = Math.max(minimum.y - point.y, 0, point.y - maximum.y);
  const dz = Math.max(minimum.z - point.z, 0, point.z - maximum.z);
  return Math.hypot(dx, dy, dz);
}

/** Full opacity through the selected distance, then a short smooth fade. */
export function proximityVisibility(distance: number, fullDistance: number, farVisibility = PROXIMITY_FAR_VISIBILITY) {
  const radius = Math.max(0, Number.isFinite(fullDistance) ? fullDistance : PROXIMITY_DEFAULT_DISTANCE);
  const far = Math.min(1, Math.max(0, Number.isFinite(farVisibility) ? farVisibility : PROXIMITY_FAR_VISIBILITY));
  if (distance <= radius) return 1;
  const fadeWidth = Math.max(1, radius * .25);
  const t = Math.min(1, Math.max(0, (distance - radius) / fadeWidth));
  const smooth = t * t * (3 - 2 * t);
  return 1 + (far - 1) * smooth;
}
