import { ArcRotateCamera, Color3, DirectionalLight, Engine, HighlightLayer, HemisphericLight, Mesh, Scene, SceneLoader, StandardMaterial, Vector3 } from "@babylonjs/core";
import "@babylonjs/loaders/glTF";
import { chooseModelOrigin, decodeBase64, objToMesh, parseObj, type IfcPoint, type ParsedObj } from "./geometry";
import type { GeometryAsset, GeometrySource, ModelData } from "./types";

export class Viewer {
  readonly engine: Engine; scene: Scene; camera: ArcRotateCamera; private highlight: HighlightLayer; readonly byElement = new Map<string, Mesh[]>(); readonly byMesh = new Map<Mesh, string>();
  renderOriginIfc: IfcPoint = [0, 0, 0];
  constructor(private canvas: HTMLCanvasElement, private picked: (uri: string) => void) { this.engine = new Engine(canvas, true); this.scene = this.createScene(); this.camera = this.createCamera(); this.highlight = new HighlightLayer("selection", this.scene); this.engine.runRenderLoop(() => this.scene.render()); window.addEventListener("resize", () => this.engine.resize()); }
  private createScene() { const s = new Scene(this.engine); s.clearColor.set(0.05, 0.07, 0.1, 1); new HemisphericLight("sky", new Vector3(0, 1, 0), s).intensity = .8; const sun = new DirectionalLight("sun", new Vector3(-.4, -1, -.3), s); sun.position = new Vector3(20, 30, 10); return s; }
  private createCamera() { const c = new ArcRotateCamera("orbit", -.8, 1.1, 20, Vector3.Zero(), this.scene); c.attachControl(this.canvas, true); c.wheelDeltaPercentage = .01; c.lowerRadiusLimit = .4; return c; }
  clear() { this.highlight.dispose(); this.scene.dispose(); this.byElement.clear(); this.byMesh.clear(); this.renderOriginIfc = [0, 0, 0]; this.scene = this.createScene(); this.camera = this.createCamera(); this.highlight = new HighlightLayer("selection", this.scene); }
  async render(data: ModelData, assets: Map<string, GeometryAsset>): Promise<{ meshes: number; unsupported: number }> {
    let meshes = 0, unsupported = 0;
    const loaded: { source: GeometrySource; parsed: ParsedObj }[] = [];
    const selected = selectAvailableSources(data.geometries, assets);
    for (const source of selected.filter(source => source.kind.endsWith("obj"))) try {
      const payload = source.kind === "embedded-obj" ? decodeBase64(source.payload!) : await assetText(source, assets);
      if (!payload) { unsupported++; continue; }
      loaded.push({ source, parsed: parseObj(payload) });
    } catch (e) { console.warn("Geometry skipped", source, e); unsupported++; }
    this.renderOriginIfc = chooseModelOrigin(loaded.map(item => item.parsed));
    for (const { source, parsed } of loaded) try {
      const sourceMetadata = { sourceCoordinateSpace: source.coordinateSpace ?? "local-engineering",
        sourceCoordinateReferenceSystem: source.coordinateReferenceSystem, sourceLengthUnit: source.lengthUnit,
        sourceUnitToMetres: source.unitToMetres };
      const mesh = objToMesh(source.geometry, parsed, this.scene, this.renderOriginIfc, sourceMetadata);
      this.register(mesh, source.element, source); meshes++;
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
          this.register(mesh, source.element, source, true); meshes++;
        }
      } finally { URL.revokeObjectURL(url); }
    } catch (e) { console.warn("Geometry skipped", source, e); unsupported++; }
    this.fit(); return { meshes, unsupported };
  }
  private register(mesh: Mesh, uri: string, source?: GeometrySource, preserveMaterial = false) { if (!preserveMaterial || !mesh.material) mesh.material = material(this.scene, source?.diffuseColor, source?.alpha); mesh.isPickable = true; mesh.checkCollisions = true; mesh.metadata = { ...(mesh.metadata ?? {}), ifcToLbd: { ...(mesh.metadata?.ifcToLbd ?? {}), rdfElement: uri, geometryResource: source?.geometry, artifact: source?.asset } }; this.byMesh.set(mesh, uri); (this.byElement.get(uri) ?? this.byElement.set(uri, []).get(uri)!).push(mesh); mesh.actionManager = null; mesh.onDisposeObservable.add(() => this.byMesh.delete(mesh)); this.scene.onPointerObservable.add(info => { if (info.pickInfo?.hit && info.pickInfo.pickedMesh === mesh) this.picked(uri); }); }
  select(uri?: string) { this.highlight.removeAllMeshes(); if (uri) this.byElement.get(uri)?.forEach(m => this.highlight.addMesh(m, Color3.FromHexString("#ffbf47"))); }
  focus(uri: string) { const meshes = this.byElement.get(uri) ?? []; if (!meshes.length) return; const box = meshes[0].getHierarchyBoundingVectors(); this.camera.setTarget(box.min.add(box.max).scale(.5)); this.camera.radius = Math.max(Vector3.Distance(box.min, box.max) * 1.6, 2); }
  fit() { const all = [...this.byMesh.keys()]; if (!all.length) return; const min = all[0].getBoundingInfo().boundingBox.minimumWorld.clone(), max = all[0].getBoundingInfo().boundingBox.maximumWorld.clone(); all.slice(1).forEach(m => { const b = m.getBoundingInfo().boundingBox; min.minimizeInPlace(b.minimumWorld); max.maximizeInPlace(b.maximumWorld); }); this.camera.setTarget(min.add(max).scale(.5)); this.camera.radius = Math.max(Vector3.Distance(min, max) * 1.4, 4); }
  visibility(uri: string, visible: boolean) { this.byElement.get(uri)?.forEach(m => { m.isVisible = visible; m.checkCollisions = visible; }); }
  allVisible() { this.byMesh.forEach((_uri, m) => { m.isVisible = true; m.checkCollisions = true; }); }
  dispose() { this.engine.dispose(); }
}
function material(scene: Scene, diffuse = "#7cb6d7", alpha = 1) { const m = new StandardMaterial("lbd", scene); m.diffuseColor = Color3.FromHexString(diffuse); m.alpha = alpha; m.specularColor = Color3.Black(); return m; }
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
