import { ArcRotateCamera, Mesh, UniversalCamera, Vector3 } from "@babylonjs/core";
import { WalkAvatar, dampAngle } from "./avatar";
import { nextPlanarVelocity, nextTurnProgress, nextVerticalVelocity, steerAwayFromElements, WALKER } from "./navigation-model";
import type { Viewer } from "./renderer";

type Mode = "orbit" | "walk" | "fly";

export class Navigation {
  private walk?: UniversalCamera | ArcRotateCamera;
  private fly?: UniversalCamera;
  private body?: Mesh;
  private avatar?: WalkAvatar;
  private mode: Mode = "orbit";
  private readonly pressed = new Set<string>();
  private velocity = Vector3.Zero();
  private verticalVelocity = 0;
  private avoidance = Vector3.Zero();
  private destination?: Vector3;
  private destinationHandoff = false;
  private lastSafe?: Vector3;
  private resolvingHorizontalCollision = false;
  private horizontalCollision = false;
  private horizontalCollider?: Mesh;
  private collisionRecovery?: Vector3;
  private lastCollisionLogAt = 0;
  private lastCollisionLogMesh?: Mesh;
  private lastDescentDiagnosticAt = 0;
  private pendingTurn = 0;
  private turnTarget = 0;
  private turnProgress = 0;
  private walkResume?: { position: Vector3; direction: Vector3 };
  private walkMovementStarted = false;
  private cameraY = 0;
  private updateObserver?: import("@babylonjs/core").Observer<import("@babylonjs/core").Scene>;

  constructor(private viewer: Viewer, private canvas: HTMLCanvasElement) {
    window.addEventListener("keydown", event => {
      if (!this.activeFreeCamera() || !movementKeys.has(event.code)) return;
      if (event.code === "KeyR") this.recover();
      else {
        const newlyPressed = !this.pressed.has(event.code);
        this.pressed.add(event.code);
        if (newlyPressed && event.code === "ArrowLeft") this.pendingTurn--;
        if (newlyPressed && event.code === "ArrowRight") this.pendingTurn++;
        if (manualKeys.has(event.code)) {
          if (this.destination) this.destinationHandoff = true;
          this.destination = undefined;
        }
      }
      event.preventDefault();
    });
    window.addEventListener("keyup", event => {
      this.pressed.delete(event.code);
    });
    window.addEventListener("blur", () => this.clearPressedKeys());
    this.canvas.addEventListener("blur", () => this.clearPressedKeys());
    document.addEventListener("pointerlockchange", () => { if (document.pointerLockElement !== this.canvas) this.clearPressedKeys(); });
  }

  currentMode() { return this.mode; }

  orbit() {
    const source = this.activeFreeCamera();
    if (source) {
      this.viewer.camera.setPosition(source.position.clone());
      this.viewer.camera.setTarget(source.position.add(source.getForwardRay().direction));
    }
    this.detach();
    this.mode = "orbit";
    this.viewer.scene.activeCamera = this.viewer.camera;
    this.viewer.camera.attachControl(this.canvas, true);
  }

  walkMode() {
    const { position, target } = this.sourceView();
    const resume = this.currentWalkPose() ?? this.walkResume;
    const start = resume?.position.clone()
      ?? this.viewer.findVirtualGroundStart(position)
      ?? this.viewer.findWalkStart(position);
    if (!start) throw new Error("Walk needs rendered geometry with a clear exterior ground or walkable surface.");
    this.detach();
    const scene = this.viewer.scene;
    const direction = resume?.direction.clone() ?? target.subtract(position);
    if (direction.lengthSquared() < .00001) direction.set(0, 0, 1); else direction.normalize();
    const body = new Mesh("__walk_body", scene);
    body.isVisible = false;
    body.isPickable = false;
    body.ellipsoid = new Vector3(WALKER.radius, WALKER.height / 2, WALKER.radius);
    body.ellipsoidOffset = new Vector3(0, WALKER.height / 2, 0);
    body.position.copyFrom(start);
    body.onCollide = collided => this.handleWalkCollision(collided);
    const camera = this.makeThirdPersonCamera(start, direction);
    this.body = body;
    this.avatar = new WalkAvatar(scene);
    this.avatar.update(start, Vector3.Zero(), WALKER.maxDeltaSeconds, direction);
    this.avatar.setVisible(true);
    this.walk = camera;
    this.mode = "walk";
    this.lastSafe = start.clone();
    this.cameraY = camera.position.y;
    this.velocity.setAll(0);
    this.verticalVelocity = 0;
    this.avoidance.setAll(0);
    this.walkMovementStarted = false;
    this.attachUpdate();
    this.activate(camera);
  }

  flyMode() {
    const { position, target } = this.sourceView();
    this.detach();
    const camera = this.makeCamera("fly", position, target.subtract(position));
    this.fly = camera;
    this.mode = "fly";
    this.attachUpdate();
    this.activate(camera);
  }

  walkTo(target: Vector3) {
    if (!this.walk || !this.body || this.walk.isDisposed()) this.walkMode();
    this.destination = target.clone();
  }

  recover() {
    if (!this.body || !this.walk) return;
    const safe = this.lastSafe ?? this.viewer.findWalkStart();
    if (!safe) return;
    this.body.position.copyFrom(safe);
    this.velocity.setAll(0);
    this.verticalVelocity = 0;
    this.collisionRecovery = undefined;
    this.cameraY = safe.y + WALKER.eyeHeight;
    this.syncCamera(1);
  }

  reset() { this.detach(); this.walkResume = undefined; this.mode = "orbit"; }

  private attachUpdate() {
    const scene = this.viewer.scene;
    this.updateObserver = scene.onBeforeRenderObservable.add(() => {
      const dt = Math.min(scene.getEngine().getDeltaTime() / 1000, WALKER.maxDeltaSeconds);
      if (this.mode === "walk") this.updateWalk(dt);
      else if (this.mode === "fly") this.updateFly(dt);
    });
  }

  private updateWalk(dt: number) {
    const camera = this.walk, body = this.body;
    if (!camera || !body || this.viewer.scene.activeCamera !== camera) return;
    if (camera instanceof UniversalCamera) camera.rotation.x = Math.max(-1.48, Math.min(1.48, camera.rotation.x));
    if (this.pendingTurn) {
      this.turnTarget += -this.pendingTurn * WALKER.turnStep;
      this.pendingTurn = 0;
    }
    if (this.turnTarget !== this.turnProgress) {
      // Ease the follow camera toward the queued turn; the avatar tracks the
      // camera facing exactly, so the house and the figure rotate together.
      const easedTurn = nextTurnProgress(this.turnProgress, this.turnTarget, dt) - this.turnProgress;
      this.turnProgress += easedTurn;
      if (camera instanceof ArcRotateCamera) {
        const head = body.position.add(new Vector3(0, WALKER.followTargetHeight, 0));
        turnArcCameraAround(camera, head, easedTurn, WALKER.followLookAhead);
      }
      else camera.rotation.y += easedTurn;
      camera.computeWorldMatrix();
      // Debug trace while the avatar rotates in walk mode: the current rotation
      // and where the avatar's legs stand on the floorplan (the model is rebased
      // to the render origin, so scene x/z are floorplan metres).
      const facing = camera instanceof ArcRotateCamera
        ? new Vector3(-Math.cos(camera.alpha), 0, -Math.sin(camera.alpha))
        : camera.getForwardRay().direction;
      facing.y = 0;
      if (facing.lengthSquared() < .00001) facing.set(0, 0, 1); else facing.normalize();
      const rotationDegrees = (Math.atan2(facing.z, facing.x) * 180 / Math.PI + 360) % 360;
      console.log(`[walk] rotating ${JSON.stringify({
        rotationDegrees: Number(rotationDegrees.toFixed(1)),
        legsFloorplanMetres: { x: Number(body.position.x.toFixed(3)), z: Number(body.position.z.toFixed(3)) }
      })}`);
      if (this.turnProgress === this.turnTarget) this.turnProgress = this.turnTarget = 0;
    }
    const forward = camera.getForwardRay().direction;
    forward.y = 0;
    if (forward.lengthSquared() < .00001) forward.set(0, 0, 1);
    else forward.normalize();
    const right = Vector3.Cross(Vector3.Up(), forward).normalize();
    const desiredDirection = Vector3.Zero();
    const arrowThrottle = this.pressed.has("ArrowUp") || this.pressed.has("ArrowDown");
    let speed: number = this.pressed.has("ShiftLeft") || this.pressed.has("ShiftRight") ? WALKER.fastSpeed : WALKER.walkSpeed;

    if (this.destination) {
      const remaining = this.destination.subtract(body.position); remaining.y = 0;
      if (remaining.length() < .16) {
        // Arrived: hand the facing back to the camera (eased, see syncCamera).
        this.destination = undefined;
        this.destinationHandoff = true;
      }
      else desiredDirection.copyFrom(remaining.normalize());
    } else {
      if (this.pressed.has("KeyW") || this.pressed.has("ArrowUp")) desiredDirection.addInPlace(forward);
      if (this.pressed.has("KeyS") || this.pressed.has("ArrowDown")) { desiredDirection.subtractInPlace(forward); speed = WALKER.backwardSpeed; }
      if (this.pressed.has("KeyD")) desiredDirection.addInPlace(right);
      if (this.pressed.has("KeyA")) desiredDirection.subtractInPlace(right);
      if (desiredDirection.lengthSquared() > 0) desiredDirection.normalize();
    }

    if (desiredDirection.lengthSquared() > 0) this.walkMovementStarted = true;
    if (!this.walkMovementStarted) {
      this.syncCamera(dt);
      return;
    }

    const targetX = desiredDirection.x * speed, targetZ = desiredDirection.z * speed;
    if (targetX * this.velocity.x + targetZ * this.velocity.z < 0) {
      this.velocity.x = 0;
      this.velocity.z = 0;
    }
    const acceleration = arrowThrottle ? WALKER.arrowAcceleration : WALKER.acceleration;
    const nextVelocity = nextPlanarVelocity(this.velocity.x, this.velocity.z,
      targetX, targetZ, acceleration, dt);
    this.velocity.x = nextVelocity.x;
    this.velocity.z = nextVelocity.z;
    const ground = this.viewer.groundBelow(body.position, Math.max(WALKER.maxStep + WALKER.groundSnap, WALKER.maxStepDown));
    const grounded = !!ground && body.position.y - ground.y <= WALKER.maxStepDown + .04 && this.verticalVelocity <= 0;
    const planarMoving = this.velocity.x * this.velocity.x + this.velocity.z * this.velocity.z > 1e-8;
    this.verticalVelocity = nextVerticalVelocity(this.verticalVelocity, grounded, planarMoving, dt);

    // While moving, steer smoothly a little bit away from nearby elements.
    // The bias is advisory only: it curves the path outwards but never forces
    // the walker to stop, and collisions still resolve any remaining contact.
    if (planarMoving) {
      const obstacles = this.viewer.blockingBoundsNear(body.position, WALKER.avoidanceDistance);
      const steer = steerAwayFromElements({ x: this.velocity.x, z: this.velocity.z },
        { x: body.position.x, z: body.position.z }, obstacles, WALKER.avoidanceDistance, WALKER.avoidanceMaxSpeed);
      const blend = dt >= 1 ? 1 : 1 - Math.exp(-dt / WALKER.avoidanceSmoothingSeconds);
      this.avoidance.x += (steer.x - this.avoidance.x) * blend;
      this.avoidance.z += (steer.z - this.avoidance.z) * blend;
    } else this.avoidance.setAll(0);

    const horizontal = new Vector3((this.velocity.x + this.avoidance.x) * dt, 0, (this.velocity.z + this.avoidance.z) * dt);
    const vertical = new Vector3(0, this.verticalVelocity * dt, 0);
    if (horizontal.lengthSquared() > 0 || vertical.lengthSquared() > 0) this.moveBody(horizontal, vertical);
    else this.horizontalCollision = false;
    const landed = this.viewer.groundBelow(body.position, WALKER.maxStepDown + .08);
    if ((!landed && this.verticalVelocity < -.8) || (landed && body.position.y - landed.y > .12)) {
      const now = Date.now();
      if (now - this.lastDescentDiagnosticAt > 250) {
        this.lastDescentDiagnosticAt = now;
        console.info(`[walk] descent ${JSON.stringify({
          feet: diagnosticVector(body.position), verticalVelocity: Number(this.verticalVelocity.toFixed(3)),
          landing: landed && diagnosticVector(landed),
          surface: landed && this.viewer.walkableSurfaceAt?.(landed),
          collider: this.horizontalCollider && meshDiagnostic(this.horizontalCollider)
        })}`);
      }
    }
    if (landed && this.verticalVelocity <= 0) {
      body.position.y = landed.y + .02;
      this.verticalVelocity = 0;
      if (!this.horizontalCollision && !this.collisionRecovery && this.viewer.hasHeadroom(body.position, WALKER.height))
        this.lastSafe = body.position.clone();
    }
    if (body.position.y < -100) this.recover();
    // Inside a building, hide elements whose center is above the avatar's head.
    this.viewer.hideAboveHead(body.position, body.position.y + WALKER.eyeHeight);
    this.syncCamera(dt);
  }

  private moveBody(horizontal: Vector3, vertical: Vector3) {
    const body = this.body!;
    const before = body.position.clone();
    this.horizontalCollision = false;
    this.horizontalCollider = undefined;
    this.resolvingHorizontalCollision = true;
    moveWithCollisionsFromCurrent(body, horizontal);
    this.resolvingHorizontalCollision = false;
    let achievedX = body.position.x - before.x;
    let achievedZ = body.position.z - before.z;
    const attemptedLengthSquared = horizontal.lengthSquared();
    const forwardProgress = attemptedLengthSquared > 0
      ? (achievedX * horizontal.x + achievedZ * horizontal.z) / attemptedLengthSquared
      : 1;
    if (this.horizontalCollision && attemptedLengthSquared > 0) {
      const destinationCancelled = this.destination !== undefined;
      this.destination = undefined;
      this.destinationHandoff = true; // blocked: ease back to the camera facing too
      const impact = this.collisionRecovery === undefined;
      this.collisionRecovery ??= before.clone();
      // Babylon adds a small collision epsilon. With detailed, overlapping BIM
      // triangles that correction can point against the requested movement and
      // compound until the ellipsoid is wedged in a neighbouring wall. Return
      // to the first clear contact pose instead of accepting that displacement.
      if (forwardProgress < 0) {
        body.position.copyFrom(this.collisionRecovery);
        body.computeWorldMatrix(true);
        achievedX = body.position.x - before.x;
        achievedZ = body.position.z - before.z;
        this.velocity.setAll(0);
      } else if (impact) {
        // A fresh collision rebounds the walker gently along the direction the
        // obstacle blocked, so the avatar steps back instead of sticking to the wall.
        const blocked = new Vector3(horizontal.x - achievedX, 0, horizontal.z - achievedZ);
        const blockedLength = blocked.length();
        if (blockedLength > .0005) {
          const bounce = Math.min(WALKER.collisionBounce, blockedLength * 2);
          moveWithCollisionsFromCurrent(body, blocked.normalize().scale(-bounce));
        }
      }
      this.logHorizontalCollision(before, horizontal, destinationCancelled, forwardProgress);
    } else if (attemptedLengthSquared > 0) this.collisionRecovery = undefined;
    moveWithCollisionsFromCurrent(body, vertical);
  }

  private handleWalkCollision(collided: import("@babylonjs/core").AbstractMesh | undefined) {
    const body = this.body;
    if (!(collided instanceof Mesh) || !body) return;
    const openedDoor = this.viewer.openDoor(collided, body.position);
    if (openedDoor) {
      console.log(`[walk] door opened ${JSON.stringify({
        ...meshDiagnostic(collided),
        walkerPosition: diagnosticVector(body.position),
        collisionDisabled: !collided.checkCollisions
      })}`);
      return;
    }
    const walkable = this.viewer.isWalkable(collided);
    if (this.resolvingHorizontalCollision && !walkable) {
      this.horizontalCollision = true;
      this.horizontalCollider = collided;
    }
  }

  private logHorizontalCollision(before: Vector3, attempted: Vector3,
      destinationCancelled: boolean, progressRatio: number) {
    const collider = this.horizontalCollider;
    const now = Date.now();
    if (collider === this.lastCollisionLogMesh && now - this.lastCollisionLogAt < 500) return;
    this.lastCollisionLogMesh = collider;
    this.lastCollisionLogAt = now;
    const after = this.body!.position;
    const achieved = after.subtract(before);
    console.log(`[walk] collision resolved ${JSON.stringify({
      ...(collider ? meshDiagnostic(collider) : { mesh: "unknown" }),
      attemptedDisplacement: diagnosticVector(attempted),
      achievedDisplacement: diagnosticVector(achieved),
      progressRatio: Number(progressRatio.toFixed(3)),
      position: diagnosticVector(after),
      velocity: diagnosticVector(this.velocity),
      lastSafePosition: this.lastSafe ? diagnosticVector(this.lastSafe) : undefined,
      destinationCancelled
    })}`);
  }

  private syncCamera(dt: number) {
    const body = this.body!, camera = this.walk!;
    const cameraFacing = camera instanceof ArcRotateCamera
      ? new Vector3(-Math.cos(camera.alpha), 0, -Math.sin(camera.alpha))
      : undefined;
    // While walking to a clicked point the figure faces its own steps (the eased
    // velocity heading); tracking the camera facing would make it slide sideways
    // with the legs scissoring along the wrong axis.
    let avatarFacing = cameraFacing && !this.destination ? cameraFacing : undefined;
    if (avatarFacing && this.destinationHandoff) {
      // A finished (or cancelled) click-walk hands the facing back to the camera;
      // ease that turn so the avatar turns back instead of popping between headings.
      const target = Math.atan2(avatarFacing.x, avatarFacing.z);
      const current = this.avatar ? this.avatar.root.rotation.y : target;
      const eased = dampAngle(current, target, 1 - Math.exp(-dt * 12));
      avatarFacing = new Vector3(Math.sin(eased), 0, Math.cos(eased));
      if (Math.abs(Math.atan2(Math.sin(target - eased), Math.cos(target - eased))) < .001)
        this.destinationHandoff = false;
    }
    this.avatar?.update(body.position, this.velocity, Math.min(dt, WALKER.maxDeltaSeconds), avatarFacing);
    this.avatar?.setVisible(true);
    if (camera instanceof ArcRotateCamera) {
      camera.target.copyFrom(body.position
        .add(cameraFacing!.scale(WALKER.followLookAhead))
        .add(new Vector3(0, WALKER.followTargetHeight, 0)));
      return;
    }
    const targetY = body.position.y + WALKER.eyeHeight;
    const blend = dt >= 1 ? 1 : 1 - Math.exp(-dt / WALKER.cameraSmoothingSeconds);
    this.cameraY += (targetY - this.cameraY) * blend;
    camera.position.set(body.position.x, this.cameraY, body.position.z);
  }

  private updateFly(dt: number) {
    const camera = this.fly;
    if (!camera || this.viewer.scene.activeCamera !== camera) return;
    camera.rotation.x = Math.max(-1.48, Math.min(1.48, camera.rotation.x));
    const forward = camera.getForwardRay().direction.normalize();
    const level = forward.clone(); level.y = 0; if (level.lengthSquared()) level.normalize();
    const right = Vector3.Cross(Vector3.Up(), level).normalize();
    const direction = Vector3.Zero();
    if (this.pressed.has("KeyW")) direction.addInPlace(forward);
    if (this.pressed.has("KeyS")) direction.subtractInPlace(forward);
    if (this.pressed.has("KeyD")) direction.addInPlace(right);
    if (this.pressed.has("KeyA")) direction.subtractInPlace(right);
    if (this.pressed.has("KeyE")) direction.y += 1;
    if (this.pressed.has("KeyQ")) direction.y -= 1;
    if (direction.lengthSquared()) camera.position.addInPlace(direction.normalize().scale(8 * dt));
  }

  private sourceView() {
    const source = this.activeFreeCamera() ?? this.viewer.camera;
    source.computeWorldMatrix();
    const position = source.globalPosition.clone();
    return { position, target: position.add(source.getForwardRay().direction) };
  }

  private makeCamera(name: string, position: Vector3, direction: Vector3) {
    const camera = new UniversalCamera(name, position, this.viewer.scene);
    camera.inputs.removeByType("FreeCameraKeyboardMoveInput");
    camera.angularSensibility = 4500;
    camera.inertia = .18;
    camera.minZ = .05;
    camera.setTarget(position.add(direction));
    return camera;
  }

  private makeThirdPersonCamera(position: Vector3, direction: Vector3) {
    const level = direction.clone(); level.y = 0;
    if (level.lengthSquared() < .00001) level.set(0, 0, 1); else level.normalize();
    const behind = level.scale(-1);
    const camera = new ArcRotateCamera("walk-avatar", Math.atan2(behind.z, behind.x), WALKER.followBeta, WALKER.followDistance,
      position.add(level.scale(WALKER.followLookAhead)).add(new Vector3(0, WALKER.followTargetHeight, 0)), this.viewer.scene);
    camera.lowerBetaLimit = .55;
    camera.upperBetaLimit = 2.05;
    camera.lowerRadiusLimit = WALKER.minimumFollowDistance;
    camera.upperRadiusLimit = 4;
    camera.wheelDeltaPercentage = .05;
    camera.inertia = .18;
    camera.minZ = .05;
    configureWalkCamera(camera);
    camera.inputs.removeByType("ArcRotateCameraKeyboardMoveInput");
    return camera;
  }

  private activate(camera: UniversalCamera | ArcRotateCamera) {
    this.viewer.scene.activeCamera = camera;
    camera.attachControl(this.canvas, true);
    this.canvas.focus();
    if (camera instanceof ArcRotateCamera) { document.exitPointerLock?.(); return; }
    try { const request = this.canvas.requestPointerLock?.({ unadjustedMovement: true }); if (request) void request.catch(() => this.canvas.requestPointerLock?.()); }
    catch { this.canvas.requestPointerLock?.(); }
  }

  private detach() {
    const pose = this.currentWalkPose();
    if (pose) this.walkResume = pose;
    this.clearPressedKeys();
    this.destination = undefined;
    this.destinationHandoff = false;
    this.viewer.clearHeadCut();
    this.updateObserver && this.viewer.scene.onBeforeRenderObservable.remove(this.updateObserver);
    this.updateObserver = undefined;
    this.viewer.camera.detachControl();
    this.walk?.dispose();
    this.fly?.dispose();
    this.body?.dispose();
    this.avatar?.dispose();
    this.walk = undefined; this.fly = undefined; this.body = undefined;
    this.avatar = undefined;
    this.collisionRecovery = undefined;
    this.horizontalCollider = undefined;
    this.lastCollisionLogMesh = undefined;
    this.lastCollisionLogAt = 0;
    this.pendingTurn = 0;
    this.turnTarget = 0;
    this.turnProgress = 0;
    this.resolvingHorizontalCollision = false;
    this.horizontalCollision = false;
    this.walkMovementStarted = false;
    document.exitPointerLock?.();
  }

  private currentWalkPose() {
    if (this.mode !== "walk" || !this.body || !this.walk || this.walk.isDisposed()) return undefined;
    const direction = this.walk instanceof ArcRotateCamera
      ? new Vector3(-Math.cos(this.walk.alpha), 0, -Math.sin(this.walk.alpha))
      : this.walk.getForwardRay().direction;
    direction.y = 0;
    return direction.lengthSquared() > .00001
      ? { position: this.body.position.clone(), direction: direction.normalize() }
      : undefined;
  }

  private activeFreeCamera() { const camera = this.walk ?? this.fly; return camera && !camera.isDisposed() && this.viewer.scene.activeCamera === camera ? camera : undefined; }
  private clearPressedKeys() { this.pressed.clear(); this.pendingTurn = 0; }
}

const manualKeys = new Set(["KeyW", "KeyA", "KeyS", "KeyD", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"]);
const movementKeys = new Set([...manualKeys, "KeyE", "KeyQ", "KeyR", "ShiftLeft", "ShiftRight"]);

/** Babylon caches absolute transforms, so direct rollback must be synchronized before another collision move. */
export function moveWithCollisionsFromCurrent(mesh: Mesh, displacement: Vector3) {
  mesh.computeWorldMatrix(true);
  mesh.moveWithCollisions(displacement);
}

/** The avatar body owns physical collision; its follow camera must not steer the walker. */
export function configureWalkCamera(camera: ArcRotateCamera) {
  camera.checkCollisions = false;
  return camera;
}

/** Turns the view around the avatar's head while preserving its forward look-ahead. */
export function turnArcCameraAround(camera: ArcRotateCamera, pivot: Vector3, radians: number, lookAhead: number) {
  camera.computeWorldMatrix();
  const cosine = Math.cos(radians), sine = Math.sin(radians);
  const offset = camera.position.subtract(pivot);
  const turnedPosition = pivot.add(new Vector3(
    offset.x * cosine - offset.z * sine,
    offset.y,
    offset.x * sine + offset.z * cosine
  ));
  const facing = new Vector3(-Math.cos(camera.alpha), 0, -Math.sin(camera.alpha));
  const turnedFacing = new Vector3(
    facing.x * cosine - facing.z * sine,
    0,
    facing.x * sine + facing.z * cosine
  );
  camera.target.copyFrom(pivot.add(turnedFacing.scale(lookAhead)));
  camera.setPosition(turnedPosition);
  camera.computeWorldMatrix();
}

function diagnosticVector(vector: Vector3) {
  return {
    x: Number(vector.x.toFixed(4)),
    y: Number(vector.y.toFixed(4)),
    z: Number(vector.z.toFixed(4))
  };
}

function meshDiagnostic(mesh: Mesh) {
  const metadata = mesh.metadata?.ifcToLbd;
  return {
    mesh: mesh.name,
    rdfElement: metadata?.rdfElement,
    geometryResource: metadata?.geometryResource,
    navigationRole: metadata?.navigationRole,
    collisionEnabled: mesh.checkCollisions
  };
}

