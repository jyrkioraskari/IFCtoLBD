import type { GeometrySource, NavigationRole } from "./types";

export const WALKER = Object.freeze({
  // A 0.48 m collision diameter approximates shoulder width and leaves useful
  // clearance in common 0.80-0.90 m door openings.
  radius: .24,
  height: 1.75,
  eyeHeight: 1.65,
  followDistance: 2.3,
  followBeta: Math.PI / 2,
  followTargetHeight: 1.65,
  followLookAhead: .65,
  minimumFollowDistance: .55,
  walkSpeed: 1.82,
  fastSpeed: 2.99,
  backwardSpeed: 1.3,
  turnStep: Math.PI / 18,
  turnSmoothingSeconds: .12,
  collisionBounce: .05,
  avoidanceDistance: 1.4,
  avoidanceMaxSpeed: .45,
  avoidanceSmoothingSeconds: .25,
  acceleration: 7,
  arrowAcceleration: 1.5,
  gravity: 9.81,
  maxFallSpeed: 30,
  maxStep: .2,
  // Descending needs a little more reach than climbing because exported stair
  // flights can contain a landing transition slightly deeper than one riser.
  maxStepDown: .3,
  groundSnap: .14,
  maxDeltaSeconds: 1 / 20,
  cameraSmoothingSeconds: .1
});

const nonPhysical = [
  "space", "openingelement", "opening", "void", "zone", "storey", "site",
  "building", "spatialzone", "annotation", "grid", "virtualelement", "featureelement"
];
const walkable = ["slab", "floor", "ramp", "stair", "landing", "covering"];
const blocking = [
  "wall", "slab", "column", "beam", "roof", "door", "window", "stair", "ramp",
  "railing", "curtainwall", "member", "plate", "pile", "footing", "furnishingelement",
  "furniture", "transportelement"
];

/** Collision is deliberately allowlisted: semantic and unknown meshes never become barriers. */
export function navigationRole(types: readonly string[]): NavigationRole {
  const names = types.map(typeName).join(" ");
  if (nonPhysical.some(token => names.includes(token)))
    return { blocksMovement: false, walkableSurface: false, openable: false, reason: "semantic-or-void" };
  const isWalkable = walkable.some(token => names.includes(token));
  const isBlocking = isWalkable || blocking.some(token => names.includes(token));
  const isDoor = types.map(typeName).some(name => name === "door");
  return { blocksMovement: isBlocking, walkableSurface: isWalkable, openable: isDoor, reason: isWalkable ? "walkable-product" : isBlocking ? "physical-product" : "unknown-nonblocking" };
}

function typeName(value: string) {
  const tail = value.split(/[\/#]/).pop() ?? value;
  return tail.toLowerCase().replace(/^ifc/, "");
}

/** OBJ is emitted in the unit declared by its geometry metadata; Babylon navigation uses metres. */
export function objUnitToMetres(source: Pick<GeometrySource, "kind" | "unitToMetres">): number {
  if (!source.kind.endsWith("obj")) return 1; // glTF's defined linear unit is metres.
  const scale = source.unitToMetres;
  return typeof scale === "number" && Number.isFinite(scale) && scale > 0 ? scale : 1;
}

export function moveToward(current: number, target: number, maxDelta: number) {
  if (Math.abs(target - current) <= maxDelta) return target;
  return current + Math.sign(target - current) * maxDelta;
}

export function nextPlanarVelocity(currentX: number, currentZ: number,
    targetX: number, targetZ: number, acceleration: number, dt: number) {
  if (targetX === 0 && targetZ === 0) return { x: 0, z: 0 };
  return {
    x: moveToward(currentX, targetX, acceleration * dt),
    z: moveToward(currentZ, targetZ, acceleration * dt)
  };
}

/** A stationary grounded walker needs no collision move; moving walkers keep a small floor bias. */
export function nextVerticalVelocity(current: number, grounded: boolean, planarMoving: boolean, dt: number) {
  if (grounded) return planarMoving ? -.5 : 0;
  return Math.max(current - WALKER.gravity * dt, -WALKER.maxFallSpeed);
}

/** Eases a queued view turn toward its target so arrow-key rotation is smooth, never overshoots, and lands exactly. */
export function nextTurnProgress(current: number, target: number, dt: number) {
  const remaining = target - current;
  if (Math.abs(remaining) < .001) return target;
  return current + remaining * (1 - Math.exp(-dt / WALKER.turnSmoothingSeconds));
}

/**
 * While walking inside a building, an element whose center is above the walker's
 * head is hidden while the walker stands under it, so ceilings, roofs and upper
 * floors do not block the view. Walkable surfaces are never hidden.
 */
export function isAboveHeadCut(centerY: number, headY: number, walker: HorizontalPoint,
    bounds: HorizontalBounds, walkable: boolean) {
  if (walkable || centerY <= headY) return false;
  return walker.x >= bounds.min.x && walker.x <= bounds.max.x
    && walker.z >= bounds.min.z && walker.z <= bounds.max.z;
}

type HorizontalPoint = Readonly<{ x: number; z: number }>;
type HorizontalBounds = Readonly<{ min: HorizontalPoint; max: HorizontalPoint }>;

/**
 * Soft steering bias that curves a moving walker gently away from nearby
 * elements. Purely advisory: the bias only steers the movement outwards and
 * never cancels it, so collisions still stop the walker where the bias alone
 * is not enough.
 */
export function steerAwayFromElements(movement: HorizontalPoint, walker: HorizontalPoint,
    obstacles: readonly HorizontalBounds[], awarenessDistance: number, maxSteer: number): HorizontalPoint {
  const speed = Math.hypot(movement.x, movement.z);
  if (speed < .0001 || awarenessDistance <= 0 || maxSteer <= 0) return { x: 0, z: 0 };
  const headingX = movement.x / speed, headingZ = movement.z / speed;
  let pushX = 0, pushZ = 0;
  for (const bounds of obstacles) {
    // Closest point of the element footprint to the walker; inside the
    // footprint the element center is used so the push still points outwards.
    const closestX = Math.min(Math.max(walker.x, bounds.min.x), bounds.max.x);
    const closestZ = Math.min(Math.max(walker.z, bounds.min.z), bounds.max.z);
    let deltaX = walker.x - closestX, deltaZ = walker.z - closestZ;
    if (deltaX * deltaX + deltaZ * deltaZ < .000001) {
      deltaX = walker.x - (bounds.min.x + bounds.max.x) / 2;
      deltaZ = walker.z - (bounds.min.z + bounds.max.z) / 2;
    }
    const distance = Math.hypot(deltaX, deltaZ);
    if (distance < .0001 || distance >= awarenessDistance) continue;
    const strength = 1 - distance / awarenessDistance;
    pushX += deltaX / distance * strength;
    pushZ += deltaZ / distance * strength;
  }
  if (pushX === 0 && pushZ === 0) return { x: 0, z: 0 };
  // Steer only: keep the component perpendicular to the movement, so the bias
  // curves the path outwards without ever changing the walking speed.
  const along = pushX * headingX + pushZ * headingZ;
  let steerX = pushX - along * headingX, steerZ = pushZ - along * headingZ;
  const steerLimit = maxSteer * Math.min(1, speed / WALKER.walkSpeed);
  const steerLength = Math.hypot(steerX, steerZ);
  if (steerLength > steerLimit) {
    steerX = steerX / steerLength * steerLimit;
    steerZ = steerZ / steerLength * steerLimit;
  }
  return { x: steerX, z: steerZ };
}

/** Keeps the initial walker on the virtual ground while preferring visible space outside the model footprint. */
export function exteriorGroundPoint(preferred: HorizontalPoint, ground: HorizontalBounds,
    occupied: HorizontalBounds, clearance = WALKER.radius + .18): HorizontalPoint {
  const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));
  const point = {
    x: clamp(preferred.x, ground.min.x + clearance, ground.max.x - clearance),
    z: clamp(preferred.z, ground.min.z + clearance, ground.max.z - clearance)
  };
  const expanded = {
    minX: occupied.min.x - clearance,
    maxX: occupied.max.x + clearance,
    minZ: occupied.min.z - clearance,
    maxZ: occupied.max.z + clearance
  };
  if (point.x < expanded.minX || point.x > expanded.maxX || point.z < expanded.minZ || point.z > expanded.maxZ)
    return point;

  const candidates = [
    { x: expanded.minX, z: clamp(point.z, ground.min.z + clearance, ground.max.z - clearance) },
    { x: expanded.maxX, z: clamp(point.z, ground.min.z + clearance, ground.max.z - clearance) },
    { x: clamp(point.x, ground.min.x + clearance, ground.max.x - clearance), z: expanded.minZ },
    { x: clamp(point.x, ground.min.x + clearance, ground.max.x - clearance), z: expanded.maxZ }
  ].filter(candidate => candidate.x >= ground.min.x + clearance && candidate.x <= ground.max.x - clearance &&
      candidate.z >= ground.min.z + clearance && candidate.z <= ground.max.z - clearance);
  return candidates.sort((a, b) => squaredDistance(a, preferred) - squaredDistance(b, preferred))[0] ?? point;
}

function squaredDistance(a: HorizontalPoint, b: HorizontalPoint) {
  return (a.x - b.x) ** 2 + (a.z - b.z) ** 2;
}


