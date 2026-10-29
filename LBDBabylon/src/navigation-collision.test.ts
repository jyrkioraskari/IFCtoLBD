import { ArcRotateCamera, FreeCamera, Mesh, MeshBuilder, NullEngine, Scene, StandardMaterial, Vector3 } from "@babylonjs/core";
import { describe, expect, it, vi } from "vitest";
import { configureWalkCamera, moveWithCollisionsFromCurrent, Navigation, turnArcCameraAround } from "./navigation";
import { WALKER } from "./navigation-model";

describe("walk collision movement", () => {
  it("keeps the avatar follow camera out of collision resolution", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const camera = new ArcRotateCamera("follow", 0, 1, 2, Vector3.Zero(), scene);
    camera.checkCollisions = true;

    expect(configureWalkCamera(camera).checkCollisions).toBe(false);

    scene.dispose();
    engine.dispose();
  });

  it("turns the follow camera around the avatar head rather than the look-ahead point", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const head = new Vector3(0, 1.65, 0);
    const camera = new ArcRotateCamera("follow", Math.PI, 1.7, 2.3,
      head.add(new Vector3(0, 0, .65)), scene);
    camera.computeWorldMatrix();
    const radiusBefore = Vector3.Distance(camera.position, head);

    turnArcCameraAround(camera, head, Math.PI / 18, .65);

    expect(Vector3.Distance(camera.position, head)).toBeCloseTo(radiusBefore, 5);
    expect(camera.target.y).toBeCloseTo(head.y, 5);
    expect(Math.hypot(camera.target.x - head.x, camera.target.z - head.z)).toBeCloseTo(.65, 5);

    scene.dispose();
    engine.dispose();
  });

  it("can reverse directly from Babylon's resolved collision position", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    scene.activeCamera = new FreeCamera("test-camera", Vector3.Zero(), scene);

    const wall = MeshBuilder.CreateBox("wall", { width: .2, height: 3, depth: 4 }, scene);
    wall.position.set(1, 1.5, 0);
    wall.material = new StandardMaterial("wall-material", scene);
    wall.checkCollisions = true;

    const body = new Mesh("body", scene);
    body.ellipsoid = new Vector3(.32, .875, .32);
    body.ellipsoidOffset = new Vector3(0, .875, 0);
    body.position.set(.55, .02, 0);
    scene.render();

    moveWithCollisionsFromCurrent(body, new Vector3(.05, 0, 0));
    const contact = body.position.clone();
    expect(contact.x).toBeGreaterThan(.55);
    expect(contact.x).toBeLessThan(.6);

    // Reversing uses Babylon's resolved contact position directly; there is no
    // second forced-retreat state that can collide with overlapping IFC meshes.
    body.position.copyFrom(contact);
    moveWithCollisionsFromCurrent(body, new Vector3(-.3, 0, 0));
    expect(body.position.x).toBeCloseTo(contact.x - .3);

    scene.dispose();
    engine.dispose();
  });

  it("does not erase gradual acceleration when the first movement is below collision epsilon", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    scene.activeCamera = new FreeCamera("test-camera", Vector3.Zero(), scene);

    const ground = MeshBuilder.CreateGround("ground", { width: 10, height: 10 }, scene);
    ground.checkCollisions = true;
    const body = new Mesh("body", scene);
    body.ellipsoid = new Vector3(.32, .875, .32);
    body.ellipsoidOffset = new Vector3(0, .875, 0);
    body.position.set(0, .02, 0);

    const navigation = Object.create(Navigation.prototype) as {
      body: Mesh;
      velocity: Vector3;
      horizontalCollision: boolean;
      moveBody(horizontal: Vector3, vertical: Vector3): void;
    };
    navigation.body = body;
    navigation.velocity = new Vector3(.02, 0, 0);
    navigation.horizontalCollision = false;
    scene.render();

    navigation.moveBody(new Vector3(.0003, 0, 0), Vector3.Zero());

    expect(navigation.velocity.x).toBe(.02);

    scene.dispose();
    engine.dispose();
  });

  it("never disables a collider or expands a tiny reverse step into a forced escape", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    scene.activeCamera = new FreeCamera("test-camera", Vector3.Zero(), scene);

    const wall = MeshBuilder.CreateBox("thin-wall", { width: 4, height: 3, depth: .012 }, scene);
    wall.position.y = 1.5;
    wall.checkCollisions = true;
    const body = new Mesh("body", scene);
    body.ellipsoid = new Vector3(.32, .875, .32);
    body.ellipsoidOffset = new Vector3(0, .875, 0);
    body.position.set(0, .02, -.34);

    const navigation = Object.create(Navigation.prototype) as {
      body: Mesh;
      velocity: Vector3;
      horizontalCollision: boolean;
      resolvingHorizontalCollision: boolean;
      collisionRecovery?: Vector3;
      moveBody(horizontal: Vector3, vertical: Vector3): void;
    };
    navigation.body = body;
    navigation.velocity = Vector3.Zero();
    navigation.horizontalCollision = false;
    navigation.resolvingHorizontalCollision = false;
    body.onCollide = collided => {
      if (navigation.resolvingHorizontalCollision && collided instanceof Mesh)
        navigation.horizontalCollision = true;
    };
    scene.render();

    navigation.moveBody(new Vector3(.006, 0, .03), Vector3.Zero());
    const contactZ = body.position.z;
    navigation.moveBody(new Vector3(0, 0, -.0004), Vector3.Zero());

    expect(Math.abs(body.position.z - contactZ)).toBeLessThan(.01);
    expect(wall.checkCollisions).toBe(true);

    scene.dispose();
    engine.dispose();
  });

  it("bounces the walker gently back when walking into a wall", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    scene.collisionsEnabled = true;
    scene.activeCamera = new FreeCamera("test-camera", Vector3.Zero(), scene);

    const wall = MeshBuilder.CreateBox("wall", { width: .2, height: 3, depth: 4 }, scene);
    wall.position.set(1, 1.5, 0); // face at x = .9; ellipsoid contact at x = .58
    wall.checkCollisions = true;
    const body = new Mesh("body", scene);
    body.ellipsoid = new Vector3(WALKER.radius, WALKER.height / 2, WALKER.radius);
    body.ellipsoidOffset = new Vector3(0, WALKER.height / 2, 0);
    body.position.set(.4, .02, 0);

    const navigation = Object.create(Navigation.prototype) as {
      body: Mesh;
      velocity: Vector3;
      horizontalCollision: boolean;
      resolvingHorizontalCollision: boolean;
      collisionRecovery?: Vector3;
      moveBody(horizontal: Vector3, vertical: Vector3): void;
    };
    navigation.body = body;
    navigation.velocity = Vector3.Zero();
    navigation.horizontalCollision = false;
    navigation.resolvingHorizontalCollision = false;
    body.onCollide = () => {
      if (navigation.resolvingHorizontalCollision) navigation.horizontalCollision = true;
    };
    // Record every collision move so the rebound is observed directly.
    const displacements: Vector3[] = [];
    const originalMove = body.moveWithCollisions.bind(body);
    body.moveWithCollisions = displacement => {
      displacements.push(displacement.clone());
      return originalMove(displacement);
    };
    scene.render();

    let maxX = body.position.x;
    const log = vi.spyOn(console, "log").mockImplementation(() => {});
    try {
      for (let frame = 0; frame < 60; frame++) {
        navigation.moveBody(new Vector3(.031, 0, 0), Vector3.Zero());
        maxX = Math.max(maxX, body.position.x);
      }
    } finally {
      log.mockRestore();
    }

    expect(maxX).toBeGreaterThan(.63); // reached the wall with the narrower body
    expect(maxX).toBeLessThan(.67); // but never passed its contact plane at about .66
    const rebounds = displacements.filter(displacement => displacement.x < -.0005);
    expect(rebounds.length).toBeGreaterThan(0); // the walker stepped back off the wall
    expect(rebounds.some(displacement => Math.abs(displacement.x) > .005)).toBe(true); // a visible, gentle rebound
    for (const rebound of rebounds)
      expect(Math.abs(rebound.x)).toBeLessThanOrEqual(WALKER.collisionBounce + .001); // never a strong shove

    scene.dispose();
    engine.dispose();
  });

  it("rolls a backward collision correction back to the clear contact anchor", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const wall = new Mesh("corner-wall", scene);
    const body = new Mesh("body", scene);
    body.position.set(2, 0, 3);

    const navigation = Object.create(Navigation.prototype) as {
      body: Mesh;
      velocity: Vector3;
      horizontalCollision: boolean;
      resolvingHorizontalCollision: boolean;
      collisionRecovery?: Vector3;
      moveBody(horizontal: Vector3, vertical: Vector3): void;
    };
    navigation.body = body;
    navigation.velocity = new Vector3(1, 0, 0);
    navigation.horizontalCollision = false;
    navigation.resolvingHorizontalCollision = false;
    body.moveWithCollisions = displacement => {
      if (displacement.lengthSquared() === 0 || displacement.y !== 0) return body;
      body.position.x -= .0032;
      navigation.horizontalCollision = true;
      return body;
    };

    navigation.moveBody(new Vector3(.02, 0, 0), Vector3.Zero());

    expect(body.position.x).toBe(2);
    expect(body.position.z).toBe(3);
    expect(navigation.velocity.lengthSquared()).toBe(0);
    expect(navigation.collisionRecovery?.equals(new Vector3(2, 0, 3))).toBe(true);

    scene.dispose();
    engine.dispose();
  });
});
