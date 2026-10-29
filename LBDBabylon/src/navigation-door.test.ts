import { ArcRotateCamera, Mesh, MeshBuilder, NullEngine, Scene, TransformNode, Vector3 } from "@babylonjs/core";
import { describe, expect, it, vi } from "vitest";
import { WalkAvatar } from "./avatar";
import { Navigation } from "./navigation";
import { WALKER } from "./navigation-model";

type StubViewer = {
  scene: Scene;
  groundBelow(position: Vector3, distance: number): Vector3 | undefined;
  hasHeadroom(position: Vector3, height: number): boolean;
  hideAboveHead(position: Vector3, headY: number): void;
  clearHeadCut(): void;
  openDoor(mesh: Mesh, walkerPosition: Vector3): boolean;
  isWalkable(mesh: Mesh): boolean;
  blockingBoundsNear(position: Vector3, distance: number): { min: Vector3; max: Vector3 }[];
};

type Rig = {
  navigation: {
    viewer: StubViewer; walk: ArcRotateCamera; body: Mesh; pressed: Set<string>;
    velocity: Vector3; verticalVelocity: number; avoidance: Vector3; walkMovementStarted: boolean;
    horizontalCollision: boolean; resolvingHorizontalCollision: boolean;
    pendingTurn: number;
    turnTarget: number; turnProgress: number; destination?: Vector3; destinationHandoff: boolean; avatar?: WalkAvatar;
    handleWalkCollision(collided: Mesh): void;
    updateWalk(dt: number): void;
  };
  body: Mesh; camera: ArcRotateCamera;
};

function makeRig(scene: Scene, bodyStart: Vector3, alpha: number,
    blocking: { min: Vector3; max: Vector3 }[] = []): Rig {
  const body = new Mesh("body", scene);
  body.ellipsoid = new Vector3(WALKER.radius, WALKER.height / 2, WALKER.radius);
  body.ellipsoidOffset = new Vector3(0, WALKER.height / 2, 0);
  body.position.copyFrom(bodyStart);
  const camera = new ArcRotateCamera("walk", alpha, WALKER.followBeta, WALKER.followDistance,
    bodyStart.add(new Vector3(0, WALKER.followTargetHeight, 0)), scene);
  scene.activeCamera = camera;
  const viewer: StubViewer = {
    scene,
    groundBelow: position => new Vector3(position.x, 0, position.z),
    hasHeadroom: () => true,
    hideAboveHead: () => {},
    clearHeadCut: () => {},
    openDoor: () => false,
    isWalkable: () => false,
    blockingBoundsNear: () => blocking
  };
  const navigation = Object.create(Navigation.prototype) as Rig["navigation"];
  navigation.viewer = viewer;
  navigation.walk = camera;
  navigation.body = body;
  navigation.pressed = new Set(["KeyW"]);
  navigation.velocity = Vector3.Zero();
  navigation.verticalVelocity = 0;
  navigation.avoidance = Vector3.Zero();
  navigation.walkMovementStarted = false;
  navigation.horizontalCollision = false;
  navigation.resolvingHorizontalCollision = false;
  navigation.pendingTurn = 0;
  navigation.turnTarget = 0;
  navigation.turnProgress = 0;
  navigation.destination = undefined;
  navigation.destinationHandoff = false;
  navigation.avatar = undefined;
  body.onCollide = collided => navigation.handleWalkCollision(collided as Mesh);
  return { navigation, body, camera };
}

function boundsOf(mesh: Mesh) {
  mesh.computeWorldMatrix(true);
  const box = mesh.getBoundingInfo().boundingBox;
  return { min: box.minimumWorld.clone(), max: box.maximumWorld.clone() };
}

/** Solid wall along x at z = 0 (face at z = .1) spanning x in [minX, maxX]. */
function buildWall(scene: Scene, minX: number, maxX: number) {
  const wall = MeshBuilder.CreateBox("wall", { width: maxX - minX, height: 3, depth: .2 }, scene);
  wall.position.set((minX + maxX) / 2, 1.5, 0);
  wall.checkCollisions = true;
  return { wall, blocking: [boundsOf(wall)] };
}

/** Wall along x at z = 0 with a realistic 0.813 m door opening; leaf thin in z. */
function buildWallWithDoor(scene: Scene, openingWidth = .813) {
  const wallWidth = (4 - openingWidth) / 2;
  const left = MeshBuilder.CreateBox("wall-left", { width: wallWidth, height: 3, depth: .2 }, scene);
  left.position.set(-openingWidth / 2 - wallWidth / 2, 1.5, 0); left.checkCollisions = true;
  const right = MeshBuilder.CreateBox("wall-right", { width: wallWidth, height: 3, depth: .2 }, scene);
  right.position.set(openingWidth / 2 + wallWidth / 2, 1.5, 0); right.checkCollisions = true;
  const door = MeshBuilder.CreateBox("door", { width: openingWidth, height: 2.2, depth: .1 }, scene);
  door.position.set(0, 1.1, 0); door.checkCollisions = true;
  door.metadata = { ifcToLbd: { rdfElement: "urn:test:door", navigationRole: { blocksMovement: true, walkableSurface: false, openable: true, reason: "physical-product" } } };
  // The walls steer the walker; the door leaf is openable and excluded, like the real Viewer.
  return { door, blocking: [boundsOf(left), boundsOf(right)] };
}

describe("walking through doors", () => {
  it("opens a door on contact and lets the walker straight through", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    const { door, blocking } = buildWallWithDoor(scene);

    // Face the door opening straight on: alpha = PI/2 gives facing (0, 0, -1).
    const rig = makeRig(scene, new Vector3(0, .02, .8), Math.PI / 2, blocking);
    rig.navigation.viewer.openDoor = collided => {
      if (collided !== door) return false;
      door.checkCollisions = false;
      return true;
    };
    scene.render();

    const log = vi.spyOn(console, "log").mockImplementation(() => {});
    try {
      for (let frame = 0; frame < 180; frame++) rig.navigation.updateWalk(1 / 60);

      expect(door.checkCollisions).toBe(false); // the door opened on contact
      expect(rig.navigation.horizontalCollision).toBe(false); // never touched the jambs
      expect(rig.body.position.z).toBeLessThan(0); // passed straight through the opening
      expect(log.mock.calls.some(([line]) => String(line).startsWith("[walk] door opened "))).toBe(true);
    } finally {
      log.mockRestore();
    }

    scene.dispose();
    engine.dispose();
  });

  it("documents a realistic narrow-door approach that hits the jamb before the leaf", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    // 0.813 m is the width of the first IfcDoor in Duplex_LBD.ttl. With a
    // 0.24 m walker radius, an approach 0.50 m off center still reaches the jamb
    // before the leaf and therefore cannot trigger the automatic door opening.
    const { door } = buildWallWithDoor(scene, .813);
    const rig = makeRig(scene, new Vector3(.5, .02, .8), Math.PI / 2);
    rig.navigation.viewer.openDoor = collided => {
      if (collided !== door) return false;
      door.checkCollisions = false;
      return true;
    };
    scene.render();

    const logs: string[] = [];
    const log = vi.spyOn(console, "log").mockImplementation(message => logs.push(String(message)));
    try {
      for (let frame = 0; frame < 180; frame++) rig.navigation.updateWalk(1 / 60);

      expect(rig.body.position.z).toBeGreaterThan(0); // blocked on the approach side
      const collision = logs.find(line => line.startsWith("[walk] collision resolved "));
      expect(collision).toBeDefined();
      expect(JSON.parse(collision!.slice("[walk] collision resolved ".length))).toMatchObject({
        mesh: "wall-right",
        destinationCancelled: false
      });
    } finally {
      log.mockRestore();
    }

    scene.dispose();
    engine.dispose();
  });
});

describe("walking down stairs", () => {
  it("keeps support across a 24.6 cm exported landing transition", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    const rig = makeRig(scene, new Vector3(0, .62, .8), Math.PI / 2);
    const levelAt = (z: number) => z > .55 ? .6 : z > .3 ? .3537 : z > .05 ? .1537 : 0;
    rig.navigation.viewer.groundBelow = position => new Vector3(position.x, levelAt(position.z), position.z);

    const heights: number[] = [];
    for (let frame = 0; frame < 90; frame++) {
      rig.navigation.updateWalk(1 / 60);
      heights.push(rig.body.position.y);
    }

    expect(rig.body.position.z).toBeLessThan(0);
    expect(rig.body.position.y).toBeCloseTo(.02, 3);
    expect(Math.max(...heights)).toBeLessThanOrEqual(.62);
    scene.dispose();
    engine.dispose();
  });
});

describe("walking along walls", () => {
  it("keeps a walker who is rotating along a wall on its own side of the wall", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    const { blocking } = buildWall(scene, -8, 8);

    // Walk along +x hugging the wall (wall face at z = .1, walker clear at z = .45).
    // alpha = PI gives facing (+1, 0, 0).
    const rig = makeRig(scene, new Vector3(-1.4, .02, .45), Math.PI, blocking);
    rig.body.onCollide = () => {
      if (rig.navigation.resolvingHorizontalCollision) rig.navigation.horizontalCollision = true;
    };
    scene.render();

    let pressedAgainstWall = false;
    let movedAlongWall = false;
    for (let frame = 0; frame < 240; frame++) {
      // ArrowRight turns the view toward -z, i.e. toward the wall, sweeping the
      // heading through 180 degrees.
      if (frame >= 30 && frame % 15 === 0) rig.navigation.pendingTurn++;
      rig.navigation.updateWalk(1 / 60);
      if (rig.navigation.horizontalCollision) pressedAgainstWall = true;
      if (rig.body.position.x > -.4) movedAlongWall = true;
      // The avatar must stay on its own side of the wall while rotating.
      expect(rig.body.position.z).toBeGreaterThan(0);
    }

    expect(rig.camera.alpha).not.toBeCloseTo(Math.PI, 5); // the rotation really happened
    expect(movedAlongWall).toBe(true); // the walker traveled along the wall
    expect(pressedAgainstWall).toBe(true); // and was pressed against the wall while turning
    expect(rig.body.position.z).toBeGreaterThan(0); // never jumped to the other side of the wall

    scene.dispose();
    engine.dispose();
  });
});

describe("rotation debug log", () => {
  it("prints the rotation and the leg position on the floorplan while the avatar rotates", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    // alpha = PI faces +x, so the rotation starts at 0 degrees.
    const rig = makeRig(scene, new Vector3(1.25, .02, -.75), Math.PI);
    scene.render();

    const logs: string[] = [];
    const spy = vi.spyOn(console, "log").mockImplementation(message => { logs.push(String(message)); });
    try {
      rig.navigation.updateWalk(1 / 60); // walking, not rotating: no rotation output
      expect(logs.filter(line => line.startsWith("[walk] rotating"))).toHaveLength(0);

      rig.navigation.pendingTurn++; // queue one arrow-key turn
      for (let frame = 0; frame < 10; frame++) rig.navigation.updateWalk(1 / 60);

      const rotating = logs.filter(line => line.startsWith("[walk] rotating "))
        .map(line => JSON.parse(line.slice("[walk] rotating ".length)));
      expect(rotating.length).toBeGreaterThan(1);
      // ArrowRight turns from 0 degrees toward -10 (350): the rotation eases, never snaps.
      expect(rotating[0].rotationDegrees).toBeGreaterThan(355);
      expect(rotating[rotating.length - 1].rotationDegrees).toBeLessThan(rotating[0].rotationDegrees);
      expect(rotating[rotating.length - 1].rotationDegrees).toBeGreaterThan(350);
      // The legs position is the avatar's footprint on the floorplan, moving forward.
      const legs = rotating[rotating.length - 1].legsFloorplanMetres;
      expect(legs.x).toBeGreaterThan(1.25);
      expect(legs.z).toBeLessThan(-.75);
    } finally {
      spy.mockRestore();
    }

    scene.dispose();
    engine.dispose();
  });
});

describe("click to walk", () => {
  it("walks the avatar toward a clicked point and arrives", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    const rig = makeRig(scene, new Vector3(0, .02, 0), Math.PI);
    scene.render();

    rig.navigation.destination = new Vector3(2, 0, 0); // the clicked point
    let frames = 0;
    while (rig.navigation.destination && frames < 600) {
      rig.navigation.updateWalk(1 / 60);
      frames++;
    }

    expect(rig.navigation.destination).toBeUndefined(); // arrived: the destination is consumed
    expect(Math.hypot(rig.body.position.x - 2, rig.body.position.z)).toBeLessThan(.2);

    scene.dispose();
    engine.dispose();
  });

  it("takes steps toward a clicked point but stops at a wall in the way", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    const wall = MeshBuilder.CreateBox("wall", { width: .2, height: 3, depth: 4 }, scene);
    wall.position.set(1, 1.5, 0); // face at x = .9; ellipsoid contact at x = .58
    wall.checkCollisions = true;
    const rig = makeRig(scene, new Vector3(0, .02, 0), Math.PI);
    rig.body.onCollide = () => {
      if (rig.navigation.resolvingHorizontalCollision) rig.navigation.horizontalCollision = true;
    };
    scene.render();

    rig.navigation.destination = new Vector3(2, 0, 0); // clicked through the wall
    let frames = 0;
    while (rig.navigation.destination && frames < 600) {
      rig.navigation.updateWalk(1 / 60);
      frames++;
    }

    expect(rig.navigation.destination).toBeUndefined(); // blocked: the walk cancels at the wall
    expect(rig.body.position.x).toBeGreaterThan(.4); // it did take steps toward the point
    expect(rig.body.position.x).toBeLessThan(.7); // but stopped at the wall, short of the clicked point

    scene.dispose();
    engine.dispose();
  });

  it("animates the steps toward the clicked point: the avatar turns to face its walk", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    // alpha = PI faces the camera +x; the clicked point is at -z.
    const rig = makeRig(scene, new Vector3(0, .02, 0), Math.PI);
    const avatar = new WalkAvatar(scene);
    avatar.setVisible(true);
    rig.navigation.avatar = avatar;
    const leftLeg = scene.getNodeByName("avatar-left-leg") as TransformNode;
    scene.render();

    rig.navigation.destination = new Vector3(0, 0, -2); // the clicked point
    let maxSwing = 0;
    let closestToWalkHeading = Infinity;
    let frames = 0;
    while (rig.navigation.destination && frames < 600) {
      rig.navigation.updateWalk(1 / 60);
      maxSwing = Math.max(maxSwing, Math.abs(leftLeg.rotation.x));
      closestToWalkHeading = Math.min(closestToWalkHeading, Math.abs(avatar.root.rotation.y - Math.PI));
      frames++;
    }

    expect(frames).toBeGreaterThan(0); // it walked to the clicked point
    expect(maxSwing).toBeGreaterThan(.2); // and the steps were animated
    // While walking, the avatar faced its steps (movement heading atan2(0, -1) = PI),
    // not the camera facing (+x = PI/2): it would slide sideways otherwise.
    expect(closestToWalkHeading).toBeLessThan(.1);
    // On arrival the avatar does not pop to the camera facing...
    const atArrival = avatar.root.rotation.y;
    expect(Math.abs(atArrival - Math.PI)).toBeLessThan(Math.abs(atArrival - Math.PI / 2));
    // ...it turns back to the camera facing smoothly over the next frames.
    rig.navigation.pressed.clear();
    for (let frame = 0; frame < 120; frame++) rig.navigation.updateWalk(1 / 60);
    expect(avatar.root.rotation.y).toBeCloseTo(Math.PI / 2, 5);

    avatar.dispose();
    scene.dispose();
    engine.dispose();
  });
});
