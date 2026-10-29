import { ArcRotateCamera, Color3, DirectionalLight, Engine, HighlightLayer, HemisphericLight, Material, Mesh, MultiMaterial, PointerEventTypes, Ray, Scene, SceneLoader, StandardMaterial, TransformNode, Vector3, VertexData } from "@babylonjs/core";
import "@babylonjs/loaders/glTF";
import { chooseModelOrigin, decodeBase64, objToMesh, parseObj, type IfcPoint, type ParsedObj } from "./geometry";
import { exteriorGroundPoint, isAboveHeadCut, navigationRole, objUnitToMetres, WALKER } from "./navigation-model";
import { distanceToBounds, PROXIMITY_DEFAULT_DISTANCE, proximityVisibility } from "./proximity";
import type { Entity, GeometryAsset, GeometrySource, ModelData, NavigationRole } from "./types";

export class Viewer {
  readonly engine: Engine; scene: Scene; camera: ArcRotateCamera; private highlight: HighlightLayer; readonly byElement = new Map<string, Mesh[]>(); readonly byMesh = new Map<Mesh, string>();
  private readonly roles = new Map<Mesh, NavigationRole>();
  private readonly originalVisibility = new Map<Mesh, number>();
  private readonly openedDoors = new Set<string>();
  private readonly elementBounds = new Map<string, { min: Vector3; max: Vector3; centerY: number }>();
  private readonly headCut = new Set<Mesh>();
  private proximityEnabled = false;
  private proximityDistance = PROXIMITY_DEFAULT_DISTANCE;
  private proximityElapsed = 0;
  renderOriginIfc: IfcPoint = [0, 0, 0];
  constructor(private canvas: HTMLCanvasElement, private picked: (uri: string) => void) { this.engine = new Engine(canvas, true); this.scene = this.createScene(); this.camera = this.createCamera(); this.highlight = new HighlightLayer("selection", this.scene); this.engine.runRenderLoop(() => { this.proximityElapsed += this.engine.getDeltaTime(); if (this.proximityEnabled && this.proximityElapsed >= 100) { this.updateProximity(); this.proximityElapsed = 0; } this.scene.render(); }); window.addEventListener("resize", () => this.engine.resize()); }
  private createScene() { const s = new Scene(this.engine); s.collisionsEnabled = true; s.clearColor.set(0.05, 0.07, 0.1, 1); new HemisphericLight("sky", new Vector3(0, 1, 0), s).intensity = .8; const sun = new DirectionalLight("sun", new Vector3(-.4, -1, -.3), s); sun.position = new Vector3(20, 30, 10); s.onPointerObservable.add(info => { if (info.type !== PointerEventTypes.POINTERPICK) return; const mesh = info.pickInfo?.pickedMesh; if (info.pickInfo?.hit && mesh instanceof Mesh) { const uri = this.byMesh.get(mesh); if (uri) this.picked(uri); } }); return s; }
  private createCamera() { const c = new ArcRotateCamera("orbit", -.8, 1.1, 20, Vector3.Zero(), this.scene); c.attachControl(this.canvas, true); c.wheelDeltaPercentage = .01; c.lowerRadiusLimit = .4; return c; }
  clear() { this.highlight.dispose(); this.scene.dispose(); this.byElement.clear(); this.byMesh.clear(); this.roles.clear(); this.originalVisibility.clear(); this.openedDoors.clear(); this.elementBounds.clear(); this.headCut.clear(); this.renderOriginIfc = [0, 0, 0]; this.scene = this.createScene(); this.camera = this.createCamera(); this.highlight = new HighlightLayer("selection", this.scene); }
  async render(data: ModelData, assets: Map<string, GeometryAsset>): Promise<{ meshes: number; unsupported: number }> {
    let meshes = 0, unsupported = 0;
    const loaded: { source: GeometrySource; parsed: ParsedObj }[] = [];
    const entities = new Map(data.entities.map(entity => [entity.id, entity]));
    const selected = selectAvailableSources(data.geometries, assets)
      .filter(source => isRenderableGeometry(source, entities.get(source.element)));
    for (const source of selected.filter(source => source.kind.endsWith("obj"))) try {
      const payload = source.kind === "embedded-obj" ? decodeBase64(source.payload!) : await assetText(source, assets);
      if (!payload) { unsupported++; continue; }
      loaded.push({ source, parsed: parseObj(payload) });
    } catch (e) { console.warn("Geometry skipped", source, e); unsupported++; }
    this.renderOriginIfc = chooseModelOrigin(loaded.map(item => item.parsed));
    for (const { source, parsed } of loaded) try {
      const sourceMetadata = { sourceCoordinateSpace: source.coordinateSpace ?? "local-engineering",
        sourceCoordinateReferenceSystem: source.coordinateReferenceSystem, sourceLengthUnit: source.lengthUnit,
        sourceUnitToMetres: source.unitToMetres, normalizedToMetres: true };
      const mesh = objToMesh(source.geometry, parsed, this.scene, this.renderOriginIfc, sourceMetadata, objUnitToMetres(source));
      this.register(mesh, source.element, entities.get(source.element), source); meshes++;
    } catch (e) { console.warn("Geometry skipped", source, e); unsupported++; }
    for (const source of selected.filter(source => source.kind === "artifact-glb" || source.kind === "artifact-gltf")) try {
      const asset = findAsset(source, assets);
      if (!asset) { unsupported++; continue; }
      const blob = source.kind === "artifact-glb"
        ? new Blob([await asset.arrayBuffer()], { type: "model/gltf-binary" })
        : new Blob([await asset.text()], { type: "model/gltf+json" });
      const url = URL.createObjectURL(blob);
      try {
        const imported = await SceneLoader.ImportMeshAsync(null, "", url, this.scene, undefined,
          source.kind === "artifact-glb" ? ".glb" : ".gltf");
        for (const mesh of imported.meshes) if (mesh instanceof Mesh && mesh.getTotalVertices() > 0) {
          this.register(mesh, source.element, entities.get(source.element), source, true); meshes++;
        }
      } finally { URL.revokeObjectURL(url); }
    } catch (e) { console.warn("Geometry skipped", source, e); unsupported++; }
    this.createVirtualGround();
    this.fit(); if (this.proximityEnabled) this.updateProximity(); return { meshes, unsupported };
  }
  private register(mesh: Mesh, uri: string, entity?: Entity, source?: GeometrySource, preserveMaterial = false) { const role = navigationRole(entity?.types ?? []); const wallOpacity = isWallType(entity?.types ?? []) ? .9 : undefined; if (!preserveMaterial || !mesh.material) mesh.material = material(this.scene, source?.diffuseColor, wallOpacity === undefined ? source?.alpha : Math.min(source?.alpha ?? 1, wallOpacity)); configureTwoSidedMaterial(mesh.material); if (wallOpacity !== undefined) configureMaterialOpacity(mesh.material, wallOpacity); mesh.isPickable = true; mesh.checkCollisions = role.blocksMovement; mesh.metadata = { ...(mesh.metadata ?? {}), ifcToLbd: { ...(mesh.metadata?.ifcToLbd ?? {}), rdfElement: uri, geometryResource: source?.geometry, artifact: source?.asset, navigationRole: role, types: entity?.types ?? [] } }; this.roles.set(mesh, role); this.originalVisibility.set(mesh, mesh.visibility); this.byMesh.set(mesh, uri); (this.byElement.get(uri) ?? this.byElement.set(uri, []).get(uri)!).push(mesh); mesh.actionManager = null; mesh.onDisposeObservable.add(() => { this.byMesh.delete(mesh); this.roles.delete(mesh); this.originalVisibility.delete(mesh); }); }
  private createVirtualGround() {
    const rendered = [...this.byMesh.keys()];
    const walkable = this.walkableMeshes();
    if (!rendered.length) return;
    rendered.forEach(mesh => mesh.computeWorldMatrix(true));
    const first = rendered[0].getBoundingInfo().boundingBox;
    const min = first.minimumWorld.clone(), max = first.maximumWorld.clone();
    rendered.slice(1).forEach(mesh => {
      const box = mesh.getBoundingInfo().boundingBox;
      min.minimizeInPlace(box.minimumWorld);
      max.maximizeInPlace(box.maximumWorld);
    });
    const walkableTops = walkable.map(mesh => {
      mesh.computeWorldMatrix(true);
      return mesh.getBoundingInfo().boundingBox.maximumWorld.y;
    });
    const renderedBottoms = rendered.map(mesh => mesh.getBoundingInfo().boundingBox.minimumWorld.y);
    const spanX = Math.max(max.x - min.x, 1), spanZ = Math.max(max.z - min.z, 1);
    const padding = Math.max(8, Math.max(spanX, spanZ) * .25);
    const walls = rendered.filter(mesh => isWallType(mesh.metadata?.ifcToLbd?.types ?? []));
    const footprint = combinedBounds(walls.length ? walls : rendered);
    const footprintRect = { minX: footprint.min.x, maxX: footprint.max.x, minZ: footprint.min.z, maxZ: footprint.max.z };
    const exteriorDoors = rendered.filter(mesh => this.roles.get(mesh)?.openable).map(mesh => {
      const box = mesh.getBoundingInfo().boundingBox;
      return { minX: box.minimumWorld.x, maxX: box.maximumWorld.x, minZ: box.minimumWorld.z, maxZ: box.maximumWorld.z, minY: box.minimumWorld.y };
    }).filter(door => exteriorDoorSide(door, footprintRect) !== undefined);
    const alignment = entryGroundLevel(walkableTops, renderedBottoms, exteriorDoors.map(door => door.minY));
    const groundLevel = alignment.level;
    if (!alignment.aligned) console.warn("[walk] entry ground and door bottoms disagree", alignment);
    const thresholds = exteriorDoors.flatMap(door => exteriorDoorThreshold(door, footprintRect, groundLevel));
    const ground = new Mesh("__virtual_ground", this.scene);
    const groundData = exteriorGroundGeometry(footprint.min.x, footprint.max.x, footprint.min.z, footprint.max.z, padding);
    applyHorizontalGeometry(ground, groundData);
    ground.position.y = groundLevel;
    const groundMaterial = new StandardMaterial("__virtual_ground_material", this.scene);
    groundMaterial.diffuseColor = Color3.White();
    groundMaterial.specularColor = Color3.Black();
    groundMaterial.alpha = 1;
    groundMaterial.backFaceCulling = false;
    groundMaterial.twoSidedLighting = true;
    ground.material = groundMaterial;
    ground.isPickable = true;
    // The plane is visual only. groundBelow limits its walking support to the
    // exterior and doorways, so it cannot prevent descending interior stairs.
    ground.checkCollisions = false;
    const role: NavigationRole = { blocksMovement: true, walkableSurface: true, openable: false, reason: "virtual-ground" };
    ground.metadata = { ifcToLbd: { navigationRole: role, virtualGround: true } };
    this.roles.set(ground, role);
    ground.onDisposeObservable.add(() => this.roles.delete(ground));
    ground.computeWorldMatrix(true);

    ground.metadata.ifcToLbd.footprint = {
      minX: footprint.min.x, maxX: footprint.max.x,
      minZ: footprint.min.z, maxZ: footprint.max.z
    };
    ground.metadata.ifcToLbd.thresholds = thresholds;
    console.info("[walk] exterior ground created", JSON.stringify({
      groundLevel: Number(groundLevel.toFixed(4)),
      wallFootprint: {
        minX: Number(footprint.min.x.toFixed(3)), maxX: Number(footprint.max.x.toFixed(3)),
        minZ: Number(footprint.min.z.toFixed(3)), maxZ: Number(footprint.max.z.toFixed(3))
      },
      groundSource: alignment.source,
      nearestEntryFloor: alignment.nearestFloorTop === undefined ? undefined : Number(alignment.nearestFloorTop.toFixed(4)),
      entryDoorsAligned: alignment.aligned,
      entryDoorOutliers: alignment.outlierDoorBottoms.map(y => Number(y.toFixed(4))),
      exteriorDoorBottoms: exteriorDoors.map(door => Number(door.minY.toFixed(4))),
      doorwayThresholds: thresholds.length,
      groundPlaneTriangles: groundData.indices.length / 3,
      visualPlaneOnly: true
    }));
  }
  setProximity(enabled: boolean, distance = this.proximityDistance) { this.proximityEnabled = enabled; this.proximityDistance = Math.min(100, Math.max(1, Number.isFinite(distance) ? distance : PROXIMITY_DEFAULT_DISTANCE)); this.proximityElapsed = 0; if (enabled) this.updateProximity(); else this.restoreVisibility(); }
  private updateProximity() { const camera = this.scene.activeCamera; if (!camera) return; const position = camera.globalPosition; for (const meshes of this.byElement.values()) { let nearest = Infinity; for (const mesh of meshes) { mesh.computeWorldMatrix(); const box = mesh.getBoundingInfo().boundingBox; nearest = Math.min(nearest, distanceToBounds(position, box.minimumWorld, box.maximumWorld)); } const visibility = proximityVisibility(nearest, this.proximityDistance); meshes.forEach(mesh => mesh.visibility = this.headCut.has(mesh) ? 0 : visibility * (this.originalVisibility.get(mesh) ?? 1)); } }
  private restoreVisibility() { this.originalVisibility.forEach((visibility, mesh) => { if (!mesh.isDisposed()) mesh.visibility = this.headCut.has(mesh) ? 0 : visibility; }); }
  select(uri?: string) { this.highlight.removeAllMeshes(); if (uri) this.byElement.get(uri)?.forEach(m => this.highlight.addMesh(m, Color3.FromHexString("#ffbf47"))); }
  focus(uri: string) { const meshes = this.byElement.get(uri) ?? []; if (!meshes.length) return; const box = meshes[0].getHierarchyBoundingVectors(); this.camera.setTarget(box.min.add(box.max).scale(.5)); this.camera.radius = Math.max(Vector3.Distance(box.min, box.max) * 1.6, 2); }
  fit() { const all = [...this.byMesh.keys()]; if (!all.length) return; const min = all[0].getBoundingInfo().boundingBox.minimumWorld.clone(), max = all[0].getBoundingInfo().boundingBox.maximumWorld.clone(); all.slice(1).forEach(m => { const b = m.getBoundingInfo().boundingBox; min.minimizeInPlace(b.minimumWorld); max.maximizeInPlace(b.maximumWorld); }); this.camera.setTarget(min.add(max).scale(.5)); this.camera.radius = Math.max(Vector3.Distance(min, max) * 1.55, 4); }
  visibility(uri: string, visible: boolean) { this.byElement.get(uri)?.forEach(m => { m.isVisible = visible; m.checkCollisions = visible && !this.openedDoors.has(uri) && !!this.roles.get(m)?.blocksMovement; }); }
  allVisible() { this.byMesh.forEach((uri, m) => { m.isVisible = true; m.checkCollisions = !this.openedDoors.has(uri) && !!this.roles.get(m)?.blocksMovement; }); }
  centerOf(uri?: string) { const meshes = uri ? this.byElement.get(uri) ?? [] : []; if (!meshes.length) return undefined; const box = meshes[0].getHierarchyBoundingVectors(); return box.min.add(box.max).scale(.5); }
  blockingMeshes() { return [...this.roles].filter(([mesh, role]) => role.blocksMovement && mesh.isVisible && mesh.checkCollisions).map(([mesh]) => mesh); }
  walkableMeshes() { return [...this.roles].filter(([mesh, role]) => role.walkableSurface && mesh.isVisible).map(([mesh]) => mesh); }
  private walkStartMeshes() { return this.walkableMeshes().filter(mesh => !this.isVirtualGround(mesh)); }
  isBlocking(mesh: Mesh) { return !!this.roles.get(mesh)?.blocksMovement && mesh.isVisible && mesh.checkCollisions; }
  isWalkable(mesh: Mesh) { return !!this.roles.get(mesh)?.walkableSurface && mesh.isVisible; }
  private isVirtualGround(mesh: Mesh) { return mesh.metadata?.ifcToLbd?.virtualGround === true; }
  openDoor(mesh: Mesh, walkerPosition: Vector3) {
    const uri = this.byMesh.get(mesh);
    if (!uri || this.openedDoors.has(uri) || !this.roles.get(mesh)?.openable) return false;
    const leaves = (this.byElement.get(uri) ?? []).filter(leaf => !leaf.isDisposed());
    if (!leaves.length) return false;
    this.openedDoors.add(uri);
    leaves.forEach(leaf => leaf.checkCollisions = false);

    const bounds = combinedBounds(leaves);
    const size = bounds.max.subtract(bounds.min);
    const center = bounds.min.add(bounds.max).scale(.5);
    const hinge = size.x >= size.z
      ? new Vector3(bounds.min.x, center.y, center.z)
      : new Vector3(center.x, center.y, bounds.min.z);
    const pivot = new TransformNode(`__door_hinge_${this.openedDoors.size}`, this.scene);
    pivot.position.copyFrom(hinge);
    leaves.forEach(leaf => leaf.setParent(pivot));
    const direction = doorSwingDirection(hinge, center, walkerPosition);
    const duration = 320;
    let elapsed = 0;
    const observer = this.scene.onBeforeRenderObservable.add(() => {
      elapsed += this.engine.getDeltaTime();
      const progress = Math.min(1, elapsed / duration);
      const eased = progress * progress * (3 - 2 * progress);
      pivot.rotation.y = direction * Math.PI / 2 * eased;
      if (progress === 1) this.scene.onBeforeRenderObservable.remove(observer);
    });
    return true;
  }
  /**
   * While the avatar walks inside a building, hides every element whose center is
   * above the avatar's head and that the avatar stands under, so ceilings, roofs
   * and upper floors do not block the view. Bounds are cached per element.
   */
  hideAboveHead(position: Vector3, headY: number) {
    const walker = { x: position.x, z: position.z };
    for (const [uri, meshes] of this.byElement) {
      if (!meshes.length) continue;
      const box = this.boundsFor(uri, meshes);
      const walkable = !!this.roles.get(meshes[0])?.walkableSurface;
      const hide = isAboveHeadCut(box.centerY, headY, walker,
        { min: { x: box.min.x, z: box.min.z }, max: { x: box.max.x, z: box.max.z } }, walkable);
      for (const mesh of meshes) {
        const hidden = this.headCut.has(mesh);
        if (hide && !hidden) { this.headCut.add(mesh); mesh.visibility = 0; }
        else if (!hide && hidden) { this.headCut.delete(mesh); mesh.visibility = this.originalVisibility.get(mesh) ?? 1; }
      }
    }
  }
  /** Restores elements hidden by the above-head cut, e.g. when leaving walk mode. */
  clearHeadCut() {
    for (const mesh of this.headCut) if (!mesh.isDisposed()) mesh.visibility = this.originalVisibility.get(mesh) ?? 1;
    this.headCut.clear();
  }
  /**
   * World-space bounds of movement-blocking elements near a position and at the
   * walker's level. Walkable surfaces and openable doors are excluded: floors
   * and stairs are walked on, and doors are meant to be walked into. Feeds the
   * soft outward steering bias while walking.
   */
  blockingBoundsNear(position: Vector3, distance: number) {
    const bottom = position.y, top = position.y + WALKER.height;
    const near: { min: Vector3; max: Vector3 }[] = [];
    for (const [uri, meshes] of this.byElement) {
      if (!meshes.length) continue;
      const role = this.roles.get(meshes[0]);
      if (!role?.blocksMovement || role.walkableSurface || role.openable) continue;
      if (!meshes.some(mesh => mesh.isVisible && mesh.checkCollisions)) continue;
      const box = this.boundsFor(uri, meshes);
      if (box.max.y < bottom || box.min.y > top) continue;
      const closestX = Math.min(Math.max(position.x, box.min.x), box.max.x);
      const closestZ = Math.min(Math.max(position.z, box.min.z), box.max.z);
      if (Math.hypot(position.x - closestX, position.z - closestZ) >= distance) continue;
      near.push({ min: box.min, max: box.max });
    }
    return near;
  }
  private boundsFor(uri: string, meshes: Mesh[]) {
    let box = this.elementBounds.get(uri);
    if (!box) {
      const bounds = combinedBounds(meshes);
      box = { min: bounds.min, max: bounds.max, centerY: (bounds.min.y + bounds.max.y) / 2 };
      this.elementBounds.set(uri, box);
    }
    return box;
  }
  pickWalkable(x = this.scene.pointerX, y = this.scene.pointerY) { const pick = this.scene.pick(x, y, mesh => mesh instanceof Mesh && this.isWalkable(mesh)); return pick?.hit ? pick.pickedPoint?.clone() : undefined; }
  /** The world point under the pointer on any visible, pickable mesh, for click-to-walk. */
  pickPoint(x = this.scene.pointerX, y = this.scene.pointerY) { const pick = this.scene.pick(x, y, mesh => mesh instanceof Mesh && mesh.isVisible && mesh.isPickable); return pick?.hit ? pick.pickedPoint?.clone() : undefined; }
  groundBelow(position: Vector3, distance: number) {
    const ray = new Ray(position.add(new Vector3(0, .3, 0)), Vector3.Down(), distance + .3);
    const pick = this.scene.pickWithRay(ray, mesh => {
      if (!(mesh instanceof Mesh) || !this.isWalkable(mesh)) return false;
      if (!this.isVirtualGround(mesh)) return true;
      const footprint = mesh.metadata?.ifcToLbd?.footprint;
      return !footprint || position.x < footprint.minX || position.x > footprint.maxX ||
        position.z < footprint.minZ || position.z > footprint.maxZ ||
        mesh.metadata?.ifcToLbd?.thresholds?.some((rect: GroundRectangle) =>
          position.x >= rect.minX && position.x <= rect.maxX &&
          position.z >= rect.minZ && position.z <= rect.maxZ);
    }, false);
    return pick?.hit ? pick.pickedPoint?.clone() : undefined;
  }
  walkableSurfaceAt(point: Vector3) {
    const ray = new Ray(point.add(new Vector3(0, .08, 0)), Vector3.Down(), .16);
    const pick = this.scene.pickWithRay(ray, mesh => mesh instanceof Mesh && this.isWalkable(mesh), false);
    if (!pick?.hit || !(pick.pickedMesh instanceof Mesh)) return undefined;
    const mesh = pick.pickedMesh;
    return {
      mesh: mesh.name,
      rdfElement: this.byMesh.get(mesh),
      virtualGround: this.isVirtualGround(mesh),
      types: mesh.metadata?.ifcToLbd?.types
    };
  }
  findVirtualGroundStart(preferred = this.camera.globalPosition): Vector3 | undefined {
    const ground = this.walkableMeshes().find(mesh => this.isVirtualGround(mesh));
    const rendered = [...this.byMesh.keys()].filter(mesh => mesh.isVisible);
    if (!ground || !rendered.length) return undefined;
    ground.computeWorldMatrix(true);
    const groundBox = ground.getBoundingInfo().boundingBox;
    const model = combinedBounds(rendered);
    const point = exteriorGroundPoint(preferred, { min: groundBox.minimumWorld, max: groundBox.maximumWorld }, model);
    const start = new Vector3(point.x, groundBox.maximumWorld.y + .02, point.z);
    if (this.hasHeadroom(start, WALKER.height)) return start;
    return undefined;
  }
  hasHeadroom(position: Vector3, height: number) { const ray = new Ray(position.add(new Vector3(0, .04, 0)), Vector3.Up(), height); const pick = this.scene.pickWithRay(ray, mesh => mesh instanceof Mesh && this.isBlocking(mesh), true); return !pick?.hit; }
  findWalkStart(preferred = this.camera.target): Vector3 | undefined { const walkable = this.walkStartMeshes(); if (!walkable.length) return undefined; const top = Math.max(...walkable.map(mesh => mesh.getBoundingInfo().boundingBox.maximumWorld.y)) + 2; const bottom = Math.min(...walkable.map(mesh => mesh.getBoundingInfo().boundingBox.minimumWorld.y)) - 2; const candidates = [preferred, ...walkable.map(mesh => { const box = mesh.getBoundingInfo().boundingBox; return box.minimumWorld.add(box.maximumWorld).scale(.5); })]; for (const candidate of candidates) { const ray = new Ray(new Vector3(candidate.x, top, candidate.z), Vector3.Down(), Math.max(10, top - bottom)); const points = (this.scene.multiPickWithRay(ray, mesh => mesh instanceof Mesh && this.isWalkable(mesh) && !this.isVirtualGround(mesh)) ?? []).flatMap(pick => pick.hit && pick.pickedPoint ? [pick.pickedPoint] : []).filter(point => this.hasHeadroom(point, 1.75)); const below = points.filter(point => point.y <= candidate.y + .25).sort((a, b) => b.y - a.y); const point = below[0] ?? points.sort((a, b) => Math.abs(a.y - candidate.y) - Math.abs(b.y - candidate.y))[0]; if (point) return point.add(new Vector3(0, .02, 0)); } return undefined; }
  dispose() { this.engine.dispose(); }
}
function material(scene: Scene, diffuse = "#7cb6d7", alpha = 1) { const m = new StandardMaterial("lbd", scene); m.diffuseColor = Color3.FromHexString(diffuse); m.alpha = alpha; m.specularColor = Color3.Black(); return m; }
export function configureTwoSidedMaterial(value: Material | null | undefined) {
  if (!value) return;
  value.backFaceCulling = false;
  if ("twoSidedLighting" in value)
    (value as Material & { twoSidedLighting: boolean }).twoSidedLighting = true;
  if (value instanceof MultiMaterial) value.subMaterials.forEach(configureTwoSidedMaterial);
}
export function configureMaterialOpacity(value: Material | null | undefined, maximumOpacity: number) {
  if (!value) return;
  value.alpha = Math.min(value.alpha, maximumOpacity);
  if (value.alpha < 1) {
    value.transparencyMode = Material.MATERIAL_ALPHABLEND;
    // A pre-pass keeps depth coherent for overlapping, two-sided IFC wall
    // meshes while their color is still blended at the requested opacity.
    value.needDepthPrePass = true;
    value.forceDepthWrite = true;
  }
  if (value instanceof MultiMaterial)
    value.subMaterials.forEach(material => configureMaterialOpacity(material, maximumOpacity));
}
export function isWallType(types: readonly string[]) {
  return types.some(value => {
    const tail = value.split(/[\/#]/).pop() ?? value;
    return tail.toLowerCase().replace(/^ifc/, "").includes("wall");
  });
}
/** Spaces and openings are semantic volumes, not visible building components. */
export function isRenderableEntity(entity: Entity | undefined) {
  if (!entity) return true;
  return !entity.types.some(isHiddenVolumeType);
}
export function isRenderableGeometry(source: Pick<GeometrySource, "ifcMaterialClass">, entity: Entity | undefined) {
  return isRenderableEntity(entity) && !isHiddenVolumeType(source.ifcMaterialClass ?? "");
}
function isHiddenVolumeType(value: string) {
  const type = (value.split(/[\/#]/).pop() ?? value).toLowerCase().replace(/[^a-z0-9]/g, "");
  return type === "space" || type === "ifcspace" || type === "openingelement" || type === "ifcopeningelement";
}
export function virtualGroundLevel(walkableTops: readonly number[], renderedBottoms: readonly number[]) {
  if (walkableTops.length) return Math.min(...walkableTops) - .005;
  // Without usable IFC floor semantics, the absolute minimum is commonly the
  // underside of a footing. Use the most populated 10 cm elevation band so the
  // exterior ground follows the base shared by ordinary ground-storey objects.
  const bands = new Map<number, number[]>();
  for (const level of renderedBottoms) {
    const band = Math.round(level * 10);
    (bands.get(band) ?? bands.set(band, []).get(band)!).push(level);
  }
  const median = [...renderedBottoms].sort((a, b) => a - b)[Math.floor(renderedBottoms.length / 2)];
  const levels = [...bands.values()].sort((a, b) =>
    b.length - a.length || Math.abs(mean(a) - median) - Math.abs(mean(b) - median))[0];
  return mean(levels) - .005;
}
/** Find the entry door level and verify that nearby doors and a walkable floor agree with it. */
export function entryGroundLevel(walkableTops: readonly number[], renderedBottoms: readonly number[], exteriorDoorBottoms: readonly number[]) {
  const bottoms = exteriorDoorBottoms.filter(Number.isFinite).sort((a, b) => a - b);
  if (!bottoms.length) return {
    level: virtualGroundLevel(walkableTops, renderedBottoms), source: "walkable-floor" as const,
    nearestFloorTop: undefined as number | undefined, outlierDoorBottoms: [] as number[], aligned: true
  };
  const groups: number[][] = [];
  for (const bottom of bottoms) {
    const group = groups.find(values => Math.abs(bottom - values[0]) <= .03);
    if (group) group.push(bottom);
    else groups.push([bottom]);
  }
  const floorDistance = (values: number[]) => walkableTops.length
    ? Math.min(...walkableTops.map(top => Math.abs(top - mean(values)))) : Infinity;
  // Sort the supported groups first, then prefer the most common door level.
  groups.sort((a, b) =>
    Number(floorDistance(b) <= .06) - Number(floorDistance(a) <= .06) || b.length - a.length || a[0] - b[0]);
  const doorLevel = mean(groups[0]);
  const nearestFloorTop = walkableTops.length
    ? [...walkableTops].sort((a, b) => Math.abs(a - doorLevel) - Math.abs(b - doorLevel))[0] : undefined;
  const level = nearestFloorTop !== undefined && Math.abs(nearestFloorTop - doorLevel) <= .03
    ? nearestFloorTop : doorLevel;
  const outlierDoorBottoms = bottoms.filter(bottom => Math.abs(bottom - level) <= .75 && Math.abs(bottom - level) > .03);
  return {
    level, source: "exterior-door-bottom" as const, nearestFloorTop, outlierDoorBottoms,
    aligned: outlierDoorBottoms.length === 0 && nearestFloorTop !== undefined && Math.abs(nearestFloorTop - level) <= .06
  };
}
function mean(values: readonly number[]) { return values.reduce((sum, value) => sum + value, 0) / values.length; }
type GroundRectangle = { minX: number; maxX: number; minZ: number; maxZ: number };

/** Bridge the wall thickness at ground-level exterior doors, leaving the rest of the building clear. */
export function exteriorDoorThreshold(door: GroundRectangle & { minY: number }, footprint: GroundRectangle, groundLevel: number): GroundRectangle[] {
  if (Math.abs(door.minY - groundLevel) > WALKER.maxStepDown) return [];
  const side = exteriorDoorSide(door, footprint);
  if (!side) return [];
  const clearance = WALKER.radius;
  switch (side) {
    case "minX": return [{ minX: footprint.minX, maxX: Math.min(door.maxX + clearance, footprint.maxX), minZ: Math.max(footprint.minZ, door.minZ - clearance), maxZ: Math.min(footprint.maxZ, door.maxZ + clearance) }];
    case "maxX": return [{ minX: Math.max(door.minX - clearance, footprint.minX), maxX: footprint.maxX, minZ: Math.max(footprint.minZ, door.minZ - clearance), maxZ: Math.min(footprint.maxZ, door.maxZ + clearance) }];
    case "minZ": return [{ minX: Math.max(footprint.minX, door.minX - clearance), maxX: Math.min(footprint.maxX, door.maxX + clearance), minZ: footprint.minZ, maxZ: Math.min(door.maxZ + clearance, footprint.maxZ) }];
    default: return [{ minX: Math.max(footprint.minX, door.minX - clearance), maxX: Math.min(footprint.maxX, door.maxX + clearance), minZ: Math.max(door.minZ - clearance, footprint.minZ), maxZ: footprint.maxZ }];
  }
}
function exteriorDoorSide(door: GroundRectangle, footprint: GroundRectangle): "minX" | "maxX" | "minZ" | "maxZ" | undefined {
  const sides = [
    { distance: Math.abs(door.minX - footprint.minX), side: "minX" as const },
    { distance: Math.abs(door.maxX - footprint.maxX), side: "maxX" as const },
    { distance: Math.abs(door.minZ - footprint.minZ), side: "minZ" as const },
    { distance: Math.abs(door.maxZ - footprint.maxZ), side: "maxZ" as const }
  ].sort((a, b) => a.distance - b.distance);
  if (sides[0].distance > WALKER.radius) return undefined;
  const overlapX = door.maxX >= footprint.minX && door.minX <= footprint.maxX;
  const overlapZ = door.maxZ >= footprint.minZ && door.minZ <= footprint.maxZ;
  return overlapX && overlapZ ? sides[0].side : undefined;
}

/** One continuous flat plane at the entry elevation. */
export function exteriorGroundGeometry(minX: number, maxX: number, minZ: number, maxZ: number, padding: number) {
  return rectangularGroundGeometry(minX - padding, maxX + padding, minZ - padding, maxZ + padding);
}
export function rectangularGroundGeometry(minX: number, maxX: number, minZ: number, maxZ: number) {
  const positions: number[] = [], indices: number[] = [], colors: number[] = [];
  const columns = Math.max(1, Math.ceil((maxX - minX) / 2));
  const rows = Math.max(1, Math.ceil((maxZ - minZ) / 2));
  for (let row = 0; row <= rows; row++) for (let column = 0; column <= columns; column++) {
    const x = minX + (maxX - minX) * column / columns;
    const z = minZ + (maxZ - minZ) * row / rows;
    positions.push(x, 0, z);
    const variation = .035 * Math.sin(x * 1.7 + z * .8) + .025 * Math.sin(x * .6 - z * 1.9);
    colors.push(.38 + variation, .55 + variation, .29 + variation * .7, 1);
  }
  for (let row = 0; row < rows; row++) for (let column = 0; column < columns; column++) {
    const a = row * (columns + 1) + column;
    const b = a + 1, c = a + columns + 1, d = c + 1;
    indices.push(a, d, b, a, c, d);
  }
  return { positions, indices, colors };
}
function applyHorizontalGeometry(mesh: Mesh, data: { positions: number[]; indices: number[]; colors: number[] }) {
  const vertexData = new VertexData();
  vertexData.positions = data.positions;
  vertexData.indices = data.indices;
  vertexData.colors = data.colors;
  const normals: number[] = [];
  VertexData.ComputeNormals(data.positions, data.indices, normals);
  vertexData.normals = normals;
  vertexData.applyToMesh(mesh);
}
function combinedBounds(meshes: readonly Mesh[]) {
  meshes.forEach(mesh => mesh.computeWorldMatrix(true));
  const first = meshes[0].getBoundingInfo().boundingBox;
  const min = first.minimumWorld.clone(), max = first.maximumWorld.clone();
  meshes.slice(1).forEach(mesh => {
    const box = mesh.getBoundingInfo().boundingBox;
    min.minimizeInPlace(box.minimumWorld);
    max.maximizeInPlace(box.maximumWorld);
  });
  return { min, max };
}
function doorSwingDirection(hinge: Vector3, center: Vector3, walker: Vector3) {
  const offsetX = center.x - hinge.x, offsetZ = center.z - hinge.z;
  const positive = new Vector3(hinge.x + offsetZ, center.y, hinge.z - offsetX);
  const negative = new Vector3(hinge.x - offsetZ, center.y, hinge.z + offsetX);
  return Vector3.DistanceSquared(positive, walker) >= Vector3.DistanceSquared(negative, walker) ? 1 : -1;
}
async function assetText(source: GeometrySource, assets: Map<string, GeometryAsset>) { return findAsset(source, assets)?.text(); }
function findAsset(source: GeometrySource, assets: Map<string, GeometryAsset>) { const uri = source.asset!; const normalized = normalizeAssetReference(uri); return assets.get(uri) ?? assets.get(normalized) ?? assets.get(normalized.split("/").pop()!); }
function selectAvailableSources(sources: readonly GeometrySource[], assets: Map<string, GeometryAsset>) {
  const groups = new Map<string, GeometrySource[]>();
  for (const source of sources) (groups.get(source.element + "\n" + source.geometry) ?? groups.set(source.element + "\n" + source.geometry, []).get(source.element + "\n" + source.geometry)!).push(source);
  const priority = (source: GeometrySource) => source.kind === "artifact-glb" ? 0 : source.kind === "artifact-gltf" ? 1 : source.kind === "artifact-obj" ? 2 : 3;
  return [...groups.values()].map(group => group.filter(source => source.kind === "embedded-obj" || !!findAsset(source, assets)).sort((a, b) => priority(a) - priority(b))[0] ?? group.sort((a, b) => priority(a) - priority(b))[0]);
}
export function normalizeAssetReference(value: string) {
  let decoded: string;
  try { decoded = decodeURIComponent(value); } catch { throw new Error(`Malformed package path: ${value}`); }
  if (decoded.includes("\\")) throw new Error(`Unsafe package path: ${value}`);
  if (decoded.split(/[/?#]/).some(part => part === ".." || part === ".")) throw new Error(`Unsafe package path: ${value}`);
  let path = value;
  try { const uri = new URL(value); path = uri.pathname; } catch { /* relative reference */ }
  const parts = path.split("/").filter(Boolean);
  if (parts.some(part => part === ".." || part === ".")) throw new Error(`Unsafe package path: ${value}`);
  return parts.join("/");
}
