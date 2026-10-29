import { describe, expect, it } from "vitest";
import { ArcRotateCamera, NullEngine, Scene, TransformNode, Vector3 } from "@babylonjs/core";
import { AVATAR_RENDERING_GROUP, dampAngle, WalkAvatar } from "./avatar";
import { turnArcCameraAround } from "./navigation";
import { nextTurnProgress, WALKER } from "./navigation-model";

describe("avatar heading", () => {
  it("turns across the shortest side of the ±π boundary", () => {
    const current = Math.PI - .1;
    const next = dampAngle(current, -Math.PI + .1, .5);
    expect(next).toBeGreaterThan(current);
    expect(next - current).toBeCloseTo(.1);
  });

  it("builds a visual-only scale figure and animates resolved movement", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const avatar = new WalkAvatar(scene);
    avatar.setVisible(true);
    avatar.update(new Vector3(2, .02, 3), new Vector3(1.4, 0, 0), 1 / 60);
    expect(avatar.root.position.asArray()).toEqual([2, .02, 3]);
    expect(avatar.root.scaling.asArray()).toEqual([.9, .9, .9]);
    expect(avatar.root.isEnabled()).toBe(true);
    expect(scene.meshes.length).toBeGreaterThan(5);
    expect(scene.meshes.every(mesh => !mesh.isPickable && !mesh.checkCollisions)).toBe(true);
    expect(AVATAR_RENDERING_GROUP).toBe(0); // walls and the avatar share normal depth testing
    expect(scene.meshes.every(mesh => mesh.renderingGroupId === AVATAR_RENDERING_GROUP)).toBe(true);
    avatar.dispose();
    expect(scene.meshes).toHaveLength(0);
    engine.dispose();
  });

  it("faces away from the rear-follow camera even while standing or reversing", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const avatar = new WalkAvatar(scene);
    avatar.update(Vector3.Zero(), new Vector3(-1, 0, 0), 1, new Vector3(1, 0, 0));
    expect(avatar.root.rotation.y).toBeCloseTo(Math.PI / 2, 4);
    avatar.dispose();
    engine.dispose();
  });

  it("stays locked to the follow camera facing when the view turns", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const body = new Vector3(0, .02, 0);
    const level = new Vector3(0, 0, 1);
    const behind = level.scale(-1);
    const camera = new ArcRotateCamera("walk-avatar", Math.atan2(behind.z, behind.x), WALKER.followBeta,
      WALKER.followDistance,
      body.add(level.scale(WALKER.followLookAhead)).add(new Vector3(0, WALKER.followTargetHeight, 0)), scene);
    const avatar = new WalkAvatar(scene);
    const facing = () => new Vector3(-Math.cos(camera.alpha), 0, -Math.sin(camera.alpha));
    const facingError = () => {
      const target = Math.atan2(facing().x, facing().z);
      return Math.abs(Math.atan2(Math.sin(target - avatar.root.rotation.y), Math.cos(target - avatar.root.rotation.y)));
    };

    for (let i = 0; i < 180; i++) avatar.update(body, Vector3.Zero(), 1 / 60, facing());
    expect(facingError()).toBeLessThan(.001);

    // Same turn the arrow keys apply in walk mode; the camera snaps instantly.
    const head = body.add(new Vector3(0, WALKER.followTargetHeight, 0));
    turnArcCameraAround(camera, head, WALKER.turnStep, WALKER.followLookAhead);
    camera.computeWorldMatrix();
    avatar.update(body, Vector3.Zero(), 1 / 60, facing());

    expect(facingError()).toBeLessThan(.001);

    avatar.dispose();
    engine.dispose();
  });

  it("tracks an eased turn smoothly and stays in sync with the camera", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const body = new Vector3(0, .02, 0);
    const level = new Vector3(0, 0, 1);
    const behind = level.scale(-1);
    const camera = new ArcRotateCamera("walk-avatar", Math.atan2(behind.z, behind.x), WALKER.followBeta,
      WALKER.followDistance,
      body.add(level.scale(WALKER.followLookAhead)).add(new Vector3(0, WALKER.followTargetHeight, 0)), scene);
    const avatar = new WalkAvatar(scene);
    const facing = () => new Vector3(-Math.cos(camera.alpha), 0, -Math.sin(camera.alpha));
    const facingError = () => {
      const target = Math.atan2(facing().x, facing().z);
      return Math.abs(Math.atan2(Math.sin(target - avatar.root.rotation.y), Math.cos(target - avatar.root.rotation.y)));
    };
    const head = body.add(new Vector3(0, WALKER.followTargetHeight, 0));
    const alphaStart = camera.alpha;

    // Queue one arrow-key turn and ease it in like Navigation.updateWalk does.
    let turnProgress = 0;
    let frames = 0;
    while (turnProgress !== WALKER.turnStep && frames < 600) {
      const easedTurn = nextTurnProgress(turnProgress, WALKER.turnStep, 1 / 60) - turnProgress;
      turnProgress += easedTurn;
      turnArcCameraAround(camera, head, easedTurn, WALKER.followLookAhead);
      camera.computeWorldMatrix();
      avatar.update(body, Vector3.Zero(), 1 / 60, facing());
      expect(facingError()).toBeLessThan(.001);
      frames++;
    }

    expect(frames).toBeGreaterThan(2);
    expect(camera.alpha - alphaStart).toBeCloseTo(WALKER.turnStep, 5);
    expect(facingError()).toBeLessThan(.001);

    avatar.dispose();
    engine.dispose();
  });

  it("still eases velocity-derived headings into a turn", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const avatar = new WalkAvatar(scene);
    avatar.update(Vector3.Zero(), new Vector3(1, 0, 0), 1 / 60);
    const target = Math.PI / 2;
    expect(avatar.root.rotation.y).toBeGreaterThan(0);
    expect(avatar.root.rotation.y).toBeLessThan(target);
    avatar.dispose();
    engine.dispose();
  });

  it("steps the legs while rotating in place so they never freeze mid-turn", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const avatar = new WalkAvatar(scene);
    const floorPoint = new Vector3(1, .02, 2);
    const leftLeg = scene.getNodeByName("avatar-left-leg") as TransformNode;

    // Standing still and not turning: the legs stay settled.
    avatar.update(floorPoint, Vector3.Zero(), 1 / 60, new Vector3(0, 0, 1));
    avatar.update(floorPoint, Vector3.Zero(), 1 / 60, new Vector3(0, 0, 1));
    expect(Math.abs(leftLeg.rotation.x)).toBeLessThan(.001);

    // Rotating in place: the facing turns gradually with no movement, and the
    // legs step on the floorplan instead of freezing.
    let maxSwing = 0;
    for (let frame = 0; frame < 20; frame++) {
      const angle = frame * .02;
      avatar.update(floorPoint, Vector3.Zero(), 1 / 60, new Vector3(Math.sin(angle), 0, Math.cos(angle)));
      maxSwing = Math.max(maxSwing, Math.abs(leftLeg.rotation.x));
    }
    expect(maxSwing).toBeGreaterThan(.05);
    // The body itself stays planted on the floor point.
    expect(avatar.root.position.equals(floorPoint)).toBe(true);

    avatar.dispose();
    engine.dispose();
  });

  it("keeps the avatar planted at the same floor point while changing heading", () => {
    const engine = new NullEngine();
    const scene = new Scene(engine);
    const avatar = new WalkAvatar(scene);
    const floorPoint = new Vector3(4, .02, -3);

    avatar.update(floorPoint, Vector3.Zero(), 1, new Vector3(0, 0, 1));
    avatar.update(floorPoint, Vector3.Zero(), 1, new Vector3(1, 0, 0));

    expect(avatar.root.position.equals(floorPoint)).toBe(true);
    expect(avatar.root.rotation.y).toBeCloseTo(Math.PI / 2, 4);

    avatar.dispose();
    engine.dispose();
  });
});
