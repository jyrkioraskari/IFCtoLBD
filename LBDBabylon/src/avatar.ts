import { Color3, MeshBuilder, StandardMaterial, TransformNode, Vector3, type Scene } from "@babylonjs/core";

// Keep the avatar in the model's normal rendering group so scene geometry
// writes and tests the same depth buffer. A wall between camera and avatar must
// therefore occlude the avatar instead of letting it render through the wall.
export const AVATAR_RENDERING_GROUP = 0;

/** A small code-native architectural scale figure; no downloaded model or texture. */
export class WalkAvatar {
  readonly root: TransformNode;
  private readonly leftArm: TransformNode;
  private readonly rightArm: TransformNode;
  private readonly leftLeg: TransformNode;
  private readonly rightLeg: TransformNode;
  private readonly torso: TransformNode;
  private readonly materials: StandardMaterial[];
  private phase = 0;
  private heading = 0;

  constructor(scene: Scene) {
    this.root = new TransformNode("__walk_avatar", scene);
    this.root.scaling.setAll(.9);
    const coat = avatarMaterial("avatar-coat", "#d88c6a", scene);
    const trousers = avatarMaterial("avatar-trousers", "#29423d", scene);
    const skin = avatarMaterial("avatar-skin", "#d9b398", scene);
    const shoes = avatarMaterial("avatar-shoes", "#182622", scene);
    this.materials = [coat, trousers, skin, shoes];

    this.torso = new TransformNode("avatar-torso", scene);
    this.torso.parent = this.root;
    const chest = MeshBuilder.CreateCapsule("avatar-chest", { height: .62, radius: .20, tessellation: 10 }, scene);
    chest.parent = this.torso; chest.position.y = 1.18; chest.scaling.z = .68; style(chest, coat);
    const head = MeshBuilder.CreateSphere("avatar-head", { diameter: .28, segments: 12 }, scene);
    head.parent = this.torso; head.position.y = 1.64; style(head, skin);

    this.leftArm = limb("avatar-left-arm", new Vector3(-.23, 1.39, 0), .52, .065, coat, this.root, scene);
    this.rightArm = limb("avatar-right-arm", new Vector3(.23, 1.39, 0), .52, .065, coat, this.root, scene);
    this.leftLeg = limb("avatar-left-leg", new Vector3(-.10, .89, 0), .76, .085, trousers, this.root, scene);
    this.rightLeg = limb("avatar-right-leg", new Vector3(.10, .89, 0), .76, .085, trousers, this.root, scene);
    shoe("avatar-left-shoe", shoes, this.leftLeg, scene);
    shoe("avatar-right-shoe", shoes, this.rightLeg, scene);
    this.setVisible(false);
  }

  setVisible(visible: boolean) { this.root.setEnabled(visible); }

  update(position: Vector3, velocity: Vector3, dt: number, facing?: Vector3) {
    this.root.position.copyFrom(position);
    const speed = Math.hypot(velocity.x, velocity.z);
    const hasFacing = !!facing && facing.x * facing.x + facing.z * facing.z > .0001;
    const heading = hasFacing ? facing : velocity;
    let turnRate = 0;
    if (heading.x * heading.x + heading.z * heading.z > .0016) {
      const target = Math.atan2(heading.x, heading.z);
      // An explicit facing comes from the follow camera, which turns instantly;
      // adopt it at once so the figure never trails the orbiting view. Only
      // velocity-derived headings ease into a turn for a natural walk cycle.
      const previous = this.heading;
      this.heading = hasFacing ? target : dampAngle(this.heading, target, 1 - Math.exp(-dt * 12));
      const turned = Math.abs(Math.atan2(Math.sin(this.heading - previous), Math.cos(this.heading - previous)));
      turnRate = turned / Math.max(dt, .0001);
    }
    // The walk cycle advances with planar speed; turning in place advances it
    // with the turn rate, so the legs keep stepping on the floorplan and never
    // freeze mid-turn.
    const walking = speed > .04;
    const turning = turnRate > .05;
    if (walking) this.phase += speed * dt * 5.4;
    else if (turning) this.phase += turnRate * dt * 5.4;
    this.root.rotation.y = this.heading;
    const amount = Math.min(Math.max(speed / 1.4, turnRate / 2.2), 1) * .64;
    const swing = Math.sin(this.phase) * amount;
    const settle = 1 - Math.exp(-dt * 10);
    this.leftLeg.rotation.x += (swing - this.leftLeg.rotation.x) * settle;
    this.rightLeg.rotation.x += (-swing - this.rightLeg.rotation.x) * settle;
    this.leftArm.rotation.x += (-swing * .72 - this.leftArm.rotation.x) * settle;
    this.rightArm.rotation.x += (swing * .72 - this.rightArm.rotation.x) * settle;
    this.torso.position.y = walking || turning ? Math.abs(Math.sin(this.phase * 2)) * .014 : 0;
  }

  dispose() {
    this.root.dispose(false, true);
    this.materials.forEach(material => material.dispose());
  }
}

export function dampAngle(current: number, target: number, amount: number) {
  const delta = Math.atan2(Math.sin(target - current), Math.cos(target - current));
  return current + delta * amount;
}

function limb(name: string, joint: Vector3, length: number, radius: number, material: StandardMaterial,
    parent: TransformNode, scene: Scene) {
  const pivot = new TransformNode(name, scene); pivot.parent = parent; pivot.position.copyFrom(joint);
  const mesh = MeshBuilder.CreateCapsule(`${name}-mesh`, { height: length, radius, tessellation: 8 }, scene);
  mesh.parent = pivot; mesh.position.y = -length / 2; style(mesh, material);
  return pivot;
}

function shoe(name: string, material: StandardMaterial, parent: TransformNode, scene: Scene) {
  const mesh = MeshBuilder.CreateBox(name, { width: .17, height: .10, depth: .29 }, scene);
  mesh.parent = parent; mesh.position.set(0, -.72, .055); style(mesh, material);
}

function avatarMaterial(name: string, color: string, scene: Scene) {
  const material = new StandardMaterial(name, scene);
  material.diffuseColor = Color3.FromHexString(color);
  material.specularColor = Color3.Black();
  return material;
}

function style(mesh: import("@babylonjs/core").Mesh, material: StandardMaterial) {
  mesh.material = material;
  mesh.renderingGroupId = AVATAR_RENDERING_GROUP;
  mesh.isPickable = false;
  mesh.checkCollisions = false;
  mesh.receiveShadows = false;
}
