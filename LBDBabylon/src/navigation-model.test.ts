import { describe, expect, it } from "vitest";
import { readFile } from "node:fs/promises";
import { parseTurtle } from "./rdf";
import { exteriorGroundPoint, isAboveHeadCut, moveToward, navigationRole, nextPlanarVelocity, nextTurnProgress, nextVerticalVelocity, objUnitToMetres, steerAwayFromElements, WALKER } from "./navigation-model";

const fixture = (name: string) => readFile(new URL(`../fixtures/${name}`, import.meta.url), "utf8");

describe("semantic navigation roles", () => {
  it("allows only physical products to block movement", () => {
    expect(navigationRole(["https://w3id.org/bot#Space"])).toMatchObject({ blocksMovement: false, walkableSurface: false });
    expect(navigationRole(["https://example.test/IfcOpeningElement"])).toMatchObject({ blocksMovement: false, walkableSurface: false });
    expect(navigationRole(["https://example.test/UnknownHelper"])).toMatchObject({ blocksMovement: false, walkableSurface: false });
    expect(navigationRole(["https://example.test/IfcWall"])).toMatchObject({ blocksMovement: true, walkableSurface: false });
    expect(navigationRole(["https://example.test/IfcDoor"])).toMatchObject({ blocksMovement: true, openable: true });
    expect(navigationRole(["https://example.test/IfcSlab"])).toMatchObject({ blocksMovement: true, walkableSurface: true });
    expect(navigationRole(["https://example.test/IfcStair"])).toMatchObject({ blocksMovement: true, walkableSurface: true });
  });

  it("classifies the floor, stair, wall, space and void fixture", async () => {
    const model = await parseTurtle(await fixture("navigation-semantics.ttl"));
    const roles = new Map(model.entities.map(entity => [entity.id, navigationRole(entity.types)]));
    expect(roles.get("https://example.test/navigation/floor")?.walkableSurface).toBe(true);
    expect(roles.get("https://example.test/navigation/stair")?.walkableSurface).toBe(true);
    expect(roles.get("https://example.test/navigation/wall")?.blocksMovement).toBe(true);
    expect(roles.get("https://example.test/navigation/room")?.blocksMovement).toBe(false);
    expect(roles.get("https://example.test/navigation/void")?.blocksMovement).toBe(false);
    expect(model.geometries[0].unitToMetres).toBe(.001);
  });
});

describe("metre-aware locomotion", () => {
  it("uses the 30 percent faster walking speeds", () => {
    expect(WALKER.walkSpeed).toBeCloseTo(1.4 * 1.3);
    expect(WALKER.fastSpeed).toBeCloseTo(2.3 * 1.3);
    expect(WALKER.backwardSpeed).toBeCloseTo(1 * 1.3);
  });

  it("starts behind the avatar with the camera at eye height, still framing the figure", () => {
    expect(WALKER.followDistance).toBe(2.3);
    expect(WALKER.followBeta).toBeCloseTo(Math.PI / 2);
    expect(WALKER.followTargetHeight).toBe(1.65);
    expect(WALKER.followLookAhead).toBe(.65);
    // The camera sits level with the avatar's eyes, about 30 cm above the old
    // low nose-up view, and still aims at the head with the look-ahead.
    const cameraHeight = WALKER.followTargetHeight + WALKER.followDistance * Math.cos(WALKER.followBeta);
    expect(cameraHeight).toBeCloseTo(WALKER.eyeHeight, 5);
    expect(cameraHeight - (1.65 + 2.3 * Math.cos(1.7))).toBeCloseTo(.3, 1);
    expect(WALKER.followDistance * Math.sin(WALKER.followBeta) - WALKER.followLookAhead).toBeGreaterThan(1.5);
    expect(WALKER.minimumFollowDistance).toBeLessThan(WALKER.followDistance);
  });

  it("ramps arrow-key movement gently", () => {
    expect(moveToward(0, WALKER.walkSpeed, WALKER.arrowAcceleration * .1)).toBeCloseTo(.15);
  });

  it("uses a small 10 degree turn for each arrow-key press", () => {
    expect(WALKER.turnStep).toBeCloseTo(Math.PI / 18);
  });

  it("uses a door-friendly collision width", () => {
    expect(WALKER.radius * 2).toBeCloseTo(.48);
  });

  it("allows a deeper downward landing transition than an upward step", () => {
    expect(WALKER.maxStep).toBe(.2);
    expect(WALKER.maxStepDown).toBe(.3);
    expect(WALKER.maxStepDown).toBeGreaterThan(.246);
  });

  it("eases a queued turn over several frames and lands exactly on the requested heading", () => {
    let progress = 0;
    let frames = 0;
    while (progress !== WALKER.turnStep && frames < 600) {
      progress = nextTurnProgress(progress, WALKER.turnStep, 1 / 60);
      frames++;
    }
    expect(frames).toBeGreaterThan(2);
    expect(progress).toBe(WALKER.turnStep);
  });

  it("eases toward a queued turn without overshoot", () => {
    const eased = nextTurnProgress(0, WALKER.turnStep, 1 / 60);
    expect(eased).toBeGreaterThan(0);
    expect(eased).toBeLessThan(WALKER.turnStep);
    expect(nextTurnProgress(-WALKER.turnStep, WALKER.turnStep, 1 / 60)).toBeLessThan(WALKER.turnStep);
    expect(nextTurnProgress(WALKER.turnStep - .0005, WALKER.turnStep, 1 / 60)).toBe(WALKER.turnStep);
  });

  describe("above-head visibility cut", () => {
    const bounds = { min: { x: -2, z: -2 }, max: { x: 2, z: 2 } };

    it("hides an element whose center is above the head while the walker stands under it", () => {
      expect(isAboveHeadCut(3, WALKER.eyeHeight, { x: 0, z: 0 }, bounds, false)).toBe(true);
      expect(isAboveHeadCut(3, WALKER.eyeHeight, { x: 2, z: -2 }, bounds, false)).toBe(true); // footprint edge is inclusive
    });

    it("never hides walkable surfaces such as floors and stairs", () => {
      expect(isAboveHeadCut(3, WALKER.eyeHeight, { x: 0, z: 0 }, bounds, true)).toBe(false);
    });

    it("keeps elements at or below head height visible", () => {
      expect(isAboveHeadCut(WALKER.eyeHeight, WALKER.eyeHeight, { x: 0, z: 0 }, bounds, false)).toBe(false);
      expect(isAboveHeadCut(1, WALKER.eyeHeight, { x: 0, z: 0 }, bounds, false)).toBe(false);
    });

    it("shows the element again once the walker leaves its footprint", () => {
      expect(isAboveHeadCut(3, WALKER.eyeHeight, { x: 2.5, z: 0 }, bounds, false)).toBe(false);
      expect(isAboveHeadCut(3, WALKER.eyeHeight, { x: 0, z: -2.5 }, bounds, false)).toBe(false);
    });
  });

  describe("outward steering bias", () => {
    // Wall along x; its near face is z = .1.
    const wall = { min: { x: -2, z: -.1 }, max: { x: 2, z: .1 } };

    it("curves a walker walking parallel to a wall gently outwards without changing speed", () => {
      const steer = steerAwayFromElements({ x: WALKER.walkSpeed, z: 0 }, { x: 0, z: .45 }, [wall], 1.4, .45);
      expect(steer.x).toBeCloseTo(0, 5); // nothing along the movement
      expect(steer.z).toBeGreaterThan(0); // pushed away from the wall
      expect(Math.hypot(steer.x, steer.z)).toBeLessThanOrEqual(.45 + 1e-9);
    });

    it("weakens the bias with distance and drops it beyond the awareness distance", () => {
      const near = steerAwayFromElements({ x: WALKER.walkSpeed, z: 0 }, { x: 0, z: .9 }, [wall], 1.4, .45);
      const middle = steerAwayFromElements({ x: WALKER.walkSpeed, z: 0 }, { x: 0, z: 1.2 }, [wall], 1.4, .45);
      const far = steerAwayFromElements({ x: WALKER.walkSpeed, z: 0 }, { x: 0, z: 1.6 }, [wall], 1.4, .45);
      expect(Math.hypot(middle.x, middle.z)).toBeLessThan(Math.hypot(near.x, near.z));
      expect(far).toEqual({ x: 0, z: 0 });
    });

    it("curves a walker heading into a wall outwards but never forces a stop", () => {
      const steer = steerAwayFromElements({ x: 1, z: -1 }, { x: 0, z: .45 }, [wall], 1.4, .45);
      const combined = { x: 1 + steer.x, z: -1 + steer.z };
      expect(combined.z).toBeGreaterThan(-1); // pushed outwards, away from the wall
      expect(combined.x).toBeGreaterThan(0); // still moving forward
      expect(Math.hypot(steer.x, steer.z)).toBeLessThanOrEqual(.45 + 1e-9);
    });

    it("centers a walker between two parallel walls", () => {
      const left = { min: { x: -2, z: -1.1 }, max: { x: 2, z: -.9 } };
      const right = { min: { x: -2, z: .9 }, max: { x: 2, z: 1.1 } };
      const steer = steerAwayFromElements({ x: WALKER.walkSpeed, z: 0 }, { x: 0, z: .5 }, [left, right], 2, .45);
      expect(steer.z).toBeLessThan(0); // pushed back toward the corridor middle
    });

    it("returns no bias when standing still or when nothing is near", () => {
      expect(steerAwayFromElements({ x: 0, z: 0 }, { x: 0, z: .45 }, [wall], 1.4, .45)).toEqual({ x: 0, z: 0 });
      expect(steerAwayFromElements({ x: WALKER.walkSpeed, z: 0 }, { x: 0, z: 5 }, [wall], 1.4, .45)).toEqual({ x: 0, z: 0 });
    });

    it("pushes outwards from the element center when the walker is inside its footprint", () => {
      const steer = steerAwayFromElements({ x: WALKER.walkSpeed, z: 0 }, { x: 0, z: .05 }, [wall], 1.4, .45);
      expect(steer.z).toBeGreaterThan(0); // away from the wall center toward the near face
    });
  });

  it("does not apply collision-producing floor pressure while standing and turning", () => {
    expect(nextVerticalVelocity(-.5, true, false, 1 / 60)).toBe(0);
    expect(nextVerticalVelocity(0, true, true, 1 / 60)).toBe(-.5);
  });

  it("resets released movement to zero before the next acceleration", () => {
    expect(nextPlanarVelocity(1.2, -.4, 0, 0, WALKER.arrowAcceleration, .1)).toEqual({ x: 0, z: 0 });
    expect(nextPlanarVelocity(0, 0, WALKER.walkSpeed, 0, WALKER.arrowAcceleration, .1))
      .toEqual({ x: .15000000000000002, z: 0 });
  });

  it("normalizes OBJ units but leaves glTF's metre contract unchanged", () => {
    expect(objUnitToMetres({ kind: "artifact-obj", unitToMetres: .001 })).toBe(.001);
    expect(objUnitToMetres({ kind: "artifact-glb", unitToMetres: .001 })).toBe(1);
  });

  it("accelerates without overshoot", () => {
    expect(moveToward(0, WALKER.walkSpeed, WALKER.acceleration * .1)).toBeCloseTo(.7);
  });

});

describe("green-ground walk start", () => {
  const ground = { min: { x: -10, z: -8 }, max: { x: 10, z: 8 } };
  const model = { min: { x: -3, z: -2 }, max: { x: 3, z: 2 } };

  it("keeps a camera projection that is already on the exterior ground", () => {
    expect(exteriorGroundPoint({ x: 7, z: 4 }, ground, model)).toEqual({ x: 7, z: 4 });
  });

  it("moves a projection over the model to the nearest visible green edge", () => {
    const point = exteriorGroundPoint({ x: 2.8, z: 0 }, ground, model, .5);
    expect(point).toEqual({ x: 3.5, z: 0 });
  });

  it("clamps an exterior camera projection inside the green ground", () => {
    expect(exteriorGroundPoint({ x: 20, z: -20 }, ground, model, .5)).toEqual({ x: 9.5, z: -7.5 });
  });
});
