import { FreeCamera, Vector3, type Scene } from "@babylonjs/core";
import type { Viewer } from "./renderer";

export class Navigation {
  private walk?: FreeCamera; private fly?: FreeCamera; private readonly pressed = new Set<string>();
  constructor(private viewer: Viewer, private canvas: HTMLCanvasElement) {
    window.addEventListener("keydown", event => { if (this.activeFreeCamera() && movementKeys.has(event.code)) { this.pressed.add(event.code); event.preventDefault(); } });
    window.addEventListener("keyup", event => { this.pressed.delete(event.code); });
    window.addEventListener("blur", () => this.clearPressedKeys());
    canvas.addEventListener("blur", () => this.clearPressedKeys());
    document.addEventListener("pointerlockchange", () => { if (document.pointerLockElement !== canvas) this.clearPressedKeys(); });
  }
  orbit() { const source = this.activeFreeCamera(); if (source) { this.viewer.camera.setPosition(source.position.clone()); this.viewer.camera.setTarget(source.position.add(source.getForwardRay().direction)); } this.detach(); this.viewer.scene.activeCamera = this.viewer.camera; this.viewer.camera.attachControl(this.canvas, true); }
  walkMode() { const scene = this.viewer.scene; const c = this.freeCamera("walk", scene); c.ellipsoid = new Vector3(.32, .85, .32); c.ellipsoidOffset = Vector3.Zero(); c.applyGravity = false; c.checkCollisions = false; this.attachMovement(c, true); this.activate(c); this.walk = c; }
  flyMode() { const c = this.freeCamera("fly", this.viewer.scene); this.attachMovement(c, false); this.activate(c); this.fly = c; }
  recover() { if (this.walk && this.walk.position.y < -100) this.walk.position.copyFrom(safeStart(this.viewer.scene) ?? Vector3.Zero().add(new Vector3(0, 2, 0))); }
  private detach() { this.pressed.clear(); this.viewer.camera.detachControl(); this.walk?.dispose(); this.fly?.dispose(); this.walk = undefined; this.fly = undefined; document.exitPointerLock?.(); }
  private freeCamera(name: string, scene: Scene) { const source = this.activeFreeCamera() ?? this.viewer.camera; source.computeWorldMatrix(); const position = source.globalPosition.clone(); const target = position.add(source.getForwardRay().direction); this.detach(); const c = new FreeCamera(name, position, scene); c.inputs.removeByType("FreeCameraKeyboardMoveInput"); c.angularSensibility = 5500; c.inertia = 0; c.setTarget(target); return c; }
  private activate(camera: FreeCamera) { this.viewer.scene.activeCamera = camera; camera.attachControl(this.canvas, true); this.canvas.focus(); this.canvas.requestPointerLock?.(); }
  private activeFreeCamera() { const camera = this.walk ?? this.fly; return camera && !camera.isDisposed() && this.viewer.scene.activeCamera === camera ? camera : undefined; }
  private attachMovement(camera: FreeCamera, walking: boolean) { const observer = camera.getScene().onBeforeRenderObservable.add(() => { if (this.viewer.scene.activeCamera !== camera) return; const viewForward = camera.getForwardRay().direction.normalize(); const levelForward = viewForward.clone(); levelForward.y = 0; if (levelForward.lengthSquared() === 0) return; levelForward.normalize(); const right = Vector3.Cross(Vector3.Up(), levelForward).normalize(); const direction = Vector3.Zero(); const keyboardForward = walking ? levelForward : viewForward; if (this.pressed.has("KeyW")) direction.addInPlace(keyboardForward); if (this.pressed.has("KeyS")) direction.subtractInPlace(keyboardForward); if (this.pressed.has("ArrowUp")) direction.addInPlace(levelForward); if (this.pressed.has("ArrowDown")) direction.subtractInPlace(levelForward); if (this.pressed.has("KeyD") || this.pressed.has("ArrowRight")) direction.addInPlace(right); if (this.pressed.has("KeyA") || this.pressed.has("ArrowLeft")) direction.subtractInPlace(right); if (!walking && this.pressed.has("KeyE")) direction.y += 1; if (!walking && this.pressed.has("KeyQ")) direction.y -= 1; if (direction.lengthSquared() > 0) camera.cameraDirection.addInPlace(direction.normalize().scale((walking ? 4 : 8) * camera.getScene().getEngine().getDeltaTime() / 1000)); }); camera.onDisposeObservable.addOnce(() => camera.getScene().onBeforeRenderObservable.remove(observer)); }
  private clearPressedKeys() { this.pressed.clear(); }
}

const movementKeys = new Set(["KeyW", "KeyA", "KeyS", "KeyD", "KeyE", "KeyQ", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"]);
function safeStart(scene: Scene): Vector3 | undefined { const colliders = scene.meshes.filter(m => m.isVisible && m.checkCollisions); if (!colliders.length) return undefined; const candidate = colliders.map(m => m.getBoundingInfo().boundingBox).sort((a, b) => a.maximumWorld.y - b.maximumWorld.y).at(0); return candidate ? new Vector3((candidate.minimumWorld.x + candidate.maximumWorld.x) / 2, candidate.maximumWorld.y + 1.7, (candidate.minimumWorld.z + candidate.maximumWorld.z) / 2) : undefined; }
