import { ArcRotateCamera, Camera, Color3, Color4, DirectionalLight, Engine, HemisphericLight, Matrix, Mesh, MeshBuilder, MultiMaterial, Plane, Scene, StandardMaterial, SubMesh, Texture, Vector3, VertexBuffer, VertexData } from "@babylonjs/core";
import { CadDocument, fields, starterDocument, type BoxFaceKey, type Element, type Field, type Kind, type MaterialDefinition } from "./model";
import { contrastEdge, defaultFinish, finishes, type Finish } from "./finishes";
import { featureEdges } from "./feature-edges";
import { analyzeMesh } from "./mesh-analysis";
import { exportDxfPlan, exportObj, exportQuantitiesCsv, exportStl, type ExportPart, type QuantityRow } from "./exchange";
import { meshToIfcObj } from "./geometry-export";
import { cutWallOpenings } from "./wall-cut";
import { moveXZ, snapXZ } from "./snap";
import { parseCoordinates, parseDegrees, parseLength, parseRectangleDimensions, rectangleFromCorners, type GroundPoint } from "./sketch";
import { boxFaceFromNormal, pullBoxFace, type BoxFace } from "./solid-edit";
import { materialBox } from "./material-box";
import { hostedInsert } from "./hosted-insert";
import { readMtl, type ObjUnit, type ObjUpAxis } from "./obj-import";
import { createBuiltInTextures } from "./material-textures";
import { defaultTile, initialElementTexture, textureLabels, texturePresets, type TexturePreset } from "./texture-presets";
import { applyProjectedUvs, projectTriangleUvs } from "./projected-uvs";
import "./style.css";
import "./editor-theme.css";
import "./cad-workspace.css";

const app = document.querySelector<HTMLDivElement>("#app")!;
const icon = (path: string) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${path}"/></svg>`;
app.innerHTML = `<header><div class="brand"><span class="mark">▱</span><div><strong>LBD CAD</strong><small>Linked building design</small></div></div><div class="header-actions"><button id="new">${icon("M12 4v16M4 12h16")}<span>New</span></button><button id="open">${icon("M3 6h7l2 2h9v11H3zM3 10h18")}<span>Open Turtle</span></button><button id="save" class="primary">${icon("M4 3h14l3 3v15H4zM8 3v6h9V3M8 21v-8h9v8")}<span>Save Turtle</span></button><input id="file" type="file" accept=".ttl,text/turtle" hidden></div></header>
<div class="shell"><aside class="left"><section><h2>Create</h2><div class="tools"><button data-add="Wall">${icon("M3 5h18v14H3zM3 12h18M9 5v7m6 0v7")}<span>Wall</span></button><button data-add="Slab">${icon("m3 8 9-4 9 4-9 4-9-4Zm0 0v5l9 4 9-4V8")}<span>Slab</span></button><button data-add="Space">${icon("M4 4h6m4 0h6v6m0 4v6h-6m-4 0H4v-6m0-4V4")}<span>Space</span></button><button data-add="Box">${icon("m12 2 9 5v10l-9 5-9-5V7zM3 7l9 5 9-5m-9 5v10")}<span>Box</span></button><button data-add="Door" title="Add a door to a wall">${icon("M5 21V3h14v18M8 21V6h8v15m-3-8h.01")}<span>Door</span></button><button data-add="Window" title="Add a window to a wall">${icon("M3 4h18v16H3zM12 4v16M3 12h18")}<span>Window</span></button></div><p id="create-hint">Choose Door or Window, then click a wall.</p></section><section class="outline"><h2>Model <span id="count"></span></h2><div id="list"></div></section></aside>
<main><canvas id="viewport"></canvas><div class="view-label">PERSPECTIVE <span>·</span> metres</div><div class="view-hint">Drag empty space to orbit · Scroll to zoom · Shift: move vertically / snap rotation</div><div class="viewport-tools" role="group" aria-label="Mouse tools"><button data-tool="select" title="Select (Space)">${icon("m5 3 14 9-7 1-3 7z")}<span>Select</span></button><button data-tool="move" title="Move (M)">${icon("M12 2v20m0-20-3 3m3-3 3 3M2 12h20m-20 0 3-3m-3 3 3 3m17-3-3-3m3 3-3 3M12 22l-3-3m3 3 3-3")}<span>Move</span></button><button data-tool="rotate" title="Rotate (R)">${icon("M20 9a8 8 0 1 0 1 5M20 4v5h-5")}<span>Rotate</span></button><button data-tool="scale" title="Scale (S)">${icon("M4 20 20 4M11 4h9v9M4 11v9h9")}<span>Scale</span></button><button data-tool="rectangle" title="Draw Rectangle">${icon("M4 5h16v14H4z")}<span>Rectangle</span></button><button data-tool="pushpull" title="Push/Pull">${icon("M4 19h16M4 14h16M12 14V3m-4 4 4-4 4 4")}<span>Push/Pull</span></button></div><div class="viewport-actions"><button id="snap" title="Toggle snapping" aria-pressed="false">${icon("M4 4v6h6M20 20v-6h-6M4 20l16-16")}<span>Snap</span></button><button id="plan" title="Toggle plan view" aria-pressed="false">${icon("M4 4h16v16H4zM4 10h16M10 4v16")}<span>Plan</span></button><button id="fit">${icon("M4 9V4h5m6 0h5v5m0 6v5h-5m-6 0H4v-5")}<span>Fit view</span></button><button id="undo" title="Undo (Ctrl+Z)">↶</button><button id="redo" title="Redo (Ctrl+Shift+Z)">↷</button></div><label class="measure-control">Measurements <input id="measure" aria-label="Measurements in metres" placeholder="4,3 or 2.5 m" autocomplete="off"></label></main>
<aside class="right"><section><h2>Properties</h2><div id="properties" class="empty">Select an element to edit its geometry and appearance.</div></section></aside></div><div id="status" role="status">Ready</div>`;

document.querySelector(".header-actions")!.insertAdjacentHTML("beforeend",
  `<button id="import-obj" title="Import OBJ with MTL and images">${icon("M4 4h11l5 5v11H4zM14 4v6h6M8 15h8m-4-4v8")}<span>Import OBJ</span></button><input id="obj-files" type="file" accept=".obj,.mtl,.png,.jpg,.jpeg" multiple hidden>`);
app.insertAdjacentHTML("beforeend", `<dialog id="obj-dialog" class="import-dialog"><h2>Import OBJ</h2>
  <p id="obj-file-summary"></p><p>Select the OBJ, its MTL file, and any PNG/JPG texture files together.</p>
  <label>Source units<select id="obj-unit"><option value="m">Metres</option><option value="cm">Centimetres</option><option value="mm">Millimetres</option><option value="in">Inches</option><option value="ft">Feet</option></select></label>
  <label>Up axis<select id="obj-up"><option value="y">Y up</option><option value="z">Z up</option></select></label>
  <div class="dialog-actions"><button id="obj-cancel">Cancel</button><button id="obj-apply" class="primary">Import</button></div></dialog>`);

document.querySelector("aside.left .outline")!.insertAdjacentHTML("beforebegin", `<section class="workflow">
  <div class="workflow-step">MODEL WORKFLOW <span>01 — 03</span></div>
  <strong>Shape your building</strong>
  <p>Draw a footprint, pull it into a solid, then place doors and windows on a wall.</p>
  <div class="workflow-actions">
    <button data-tool="rectangle">${icon("M4 5h16v14H4z")}<span>Draw footprint</span></button>
    <button data-tool="pushpull">${icon("M4 19h16M4 14h16M12 14V3m-4 4 4-4 4 4")}<span>Push / Pull</span></button>
  </div>
</section>`);
document.querySelector("aside.left .outline h2")!.insertAdjacentHTML("afterend", '<div class="group-action"><input id="group-name" aria-label="Group name" placeholder="Group name"><button id="group-selected" title="Group selected model items">Group</button></div><p class="group-help">Ctrl-click model items to select several.</p>');
document.querySelector(".viewport-tools")!.insertAdjacentHTML("afterbegin", '<span class="toolbar-kicker">MODELING TOOLS</span>');
document.querySelector(".viewport-tools")!.insertAdjacentHTML("beforeend", `<button data-tool="paint" title="Paint face (P)">${icon("M4 16l9-9 4 4-9 9H4v-4Zm9-9 2-2 4 4-2 2M17 4l3 3")}<span>Paint</span></button>`);
document.querySelector("aside.right")!.insertAdjacentHTML("afterbegin", `<section class="view-panel">
  <h2>Camera &amp; display</h2>
  <div class="view-presets" role="group" aria-label="Standard views">
    <button data-view="iso" title="Isometric perspective">${icon("m12 2 9 5v10l-9 5-9-5V7zM3 7l9 5 9-5m-9 5v10")}<span>Iso</span></button>
    <button data-view="top" title="Top orthographic view">${icon("M4 4h16v16H4zM4 10h16M10 4v16")}<span>Top</span></button>
    <button data-view="front" title="Front orthographic view">${icon("M4 5h16v14H4zM4 11h16")}<span>Front</span></button>
    <button data-view="right" title="Right orthographic view">${icon("M5 4h14v16H5zM11 4v16")}<span>Right</span></button>
  </div>
  <label class="field display-style-label">Display style
    <select id="display-style"><option value="shaded">Shaded with edges</option><option value="hidden">Hidden line</option><option value="wireframe">Wireframe</option><option value="xray">X-ray</option></select>
  </label>
</section>`);
document.querySelector(".view-panel")!.insertAdjacentHTML("beforeend", '<div class="scene-actions"><input id="scene-name" aria-label="Scene name" placeholder="Scene name"><button id="save-scene">Save view</button></div><div id="scenes"></div>');
document.querySelector(".scene-actions")!.insertAdjacentHTML("beforebegin", '<div class="section-controls"><label><input id="section-enabled" type="checkbox"> Live section</label><label class="section-height">Cut height <output id="section-value">1.50 m</output><input id="section-height" type="range" min="-2" max="10" step="0.05" value="1.5"></label></div>');
document.querySelector("main")!.insertAdjacentHTML("beforeend", '<div class="axis-compass" aria-label="Model axes"><span class="axis-x">X</span><span class="axis-y">Y</span><span class="axis-z">Z</span></div><div id="axis-lock" class="axis-lock" hidden></div>');
document.querySelector("aside.right")!.insertAdjacentHTML("beforeend", `<section class="project-panel">
  <h2>Layers &amp; output</h2><div id="layers"></div>
  <div class="export-actions"><button id="quantities">Quantities CSV</button><button id="export-obj">OBJ</button><button id="export-stl">STL</button><button id="export-dxf">Plan DXF</button></div><div id="quantity-summary" class="quantity-summary">Surface and volume are measured from the mesh on export.</div>
  <p class="export-note">Exports use metres. Plan DXF projects visible model edges.</p>
</section>`);
const modelPanel = document.querySelector<HTMLElement>("aside.left .outline")!;
const rightDock = document.querySelector<HTMLElement>("aside.right")!;
rightDock.insertBefore(modelPanel, rightDock.querySelector(".view-panel"));
rightDock.insertBefore(rightDock.querySelector("section:not(.view-panel):not(.outline)")!, modelPanel);
document.querySelectorAll<HTMLButtonElement>("aside.left button").forEach(button => {
  const label = button.textContent?.trim();
  if (label) { button.title ||= label; button.setAttribute("aria-label", label); }
});
document.querySelectorAll<HTMLButtonElement>(".viewport-tools button, .viewport-actions button").forEach(button => {
  const label = button.title || button.textContent?.trim();
  if (label) button.setAttribute("aria-label", label);
});
document.querySelector<HTMLButtonElement>("#undo")!.setAttribute("aria-label", "Undo");
document.querySelector<HTMLButtonElement>("#redo")!.setAttribute("aria-label", "Redo");

const canvas = document.querySelector<HTMLCanvasElement>("#viewport")!;
const engine = new Engine(canvas, true);
const scene = new Scene(engine);
scene.clearColor = new Color4(.78, .81, .81, 1);
const builtInTextures = createBuiltInTextures(scene);
const importedImages = new Map<string, { data: string; texture: Texture }>();
function materialTexture(definition: MaterialDefinition): Texture | undefined {
  if (definition.texture === "none") return;
  if (definition.texture !== "image") return builtInTextures[definition.texture];
  if (!definition.imageData) return;
  const cached = importedImages.get(definition.id);
  if (cached?.data === definition.imageData) return cached.texture;
  cached?.texture.dispose();
  const texture = new Texture(definition.imageData, scene, false, true);
  texture.hasAlpha = definition.imageData.startsWith("data:image/png;");
  texture.wrapU = Texture.WRAP_ADDRESSMODE;
  texture.wrapV = Texture.WRAP_ADDRESSMODE;
  importedImages.set(definition.id, { data: definition.imageData, texture });
  return texture;
}
new HemisphericLight("ambient", new Vector3(.2, 1, .4), scene).intensity = .83;
new DirectionalLight("workbench-light", new Vector3(-.45, -1, .35), scene).intensity = .68;
const camera = new ArcRotateCamera("camera", -Math.PI / 3, Math.PI / 3.1, 13, Vector3.Zero(), scene);
camera.attachControl(canvas, true);
camera.wheelDeltaPercentage = .015;
camera.lowerRadiusLimit = 1;
const ground = MeshBuilder.CreateGround("grid-ground", { width: 200, height: 200 }, scene);
ground.isPickable = false;
ground.position.y = -.26;
const groundMaterial = new StandardMaterial("ground", scene);
groundMaterial.diffuseColor = Color3.FromHexString("#87918f");
groundMaterial.specularColor = Color3.Black();
ground.material = groundMaterial;
const grid = new StandardMaterial("grid", scene);
grid.diffuseColor = Color3.FromHexString("#c4cecc");
grid.alpha = .34;
for (let n = -20; n <= 20; n++) {
  for (const axis of ["x", "z"] as const) {
    const line = axis === "x" ? [new Vector3(-20, -.255, n), new Vector3(20, -.255, n)] : [new Vector3(n, -.255, -20), new Vector3(n, -.255, 20)];
    const mesh = MeshBuilder.CreateLines(`grid-${axis}-${n}`, { points: line }, scene);
    mesh.color = n === 0 ? Color3.FromHexString(axis === "x" ? "#d95b54" : "#65b875") : Color3.FromHexString("#385064");
    mesh.alpha = n === 0 ? .9 : .3;
    mesh.isPickable = false;
  }
}
const verticalAxis = MeshBuilder.CreateLines("grid-y-axis", { points: [new Vector3(0, 0, 0), new Vector3(0, 5, 0)] }, scene);
verticalAxis.color = Color3.FromHexString("#5d9bea");
verticalAxis.alpha = .85;
verticalAxis.isPickable = false;
engine.runRenderLoop(() => { if (planView) updatePlanBounds(); scene.render(); });
window.addEventListener("resize", () => engine.resize());
new ResizeObserver(() => engine.resize()).observe(canvas);

let doc = starterDocument();
let selected: string | undefined;
let selectedIds = new Set<string>();
let meshes = new Map<string, Mesh>();
let undo: ReturnType<CadDocument["snapshot"]>[] = [];
let redo: ReturnType<CadDocument["snapshot"]>[] = [];
type Tool = "select" | "move" | "rotate" | "scale" | "rectangle" | "pushpull" | "paint";
let tool: Tool = "move";
let paintMaterialId: string | undefined;
let placing: "Door" | "Window" | undefined;
type Drag = { pointerId: number; id: string; startX: number; startY: number; start: Element; offset?: Vector3;
  preview: Mesh; temporary?: boolean; face?: BoxFace; screenAxis?: { x: number; y: number };
  pending?: ReturnType<typeof pullBoxFace> };
let drag: Drag | undefined;
let rectangleStart: GroundPoint | undefined;
let rectanglePreview: Mesh | undefined;
let snapEnabled = false;
let planView = false;
type ViewPreset = "iso" | "top" | "front" | "right";
type DisplayStyle = "shaded" | "hidden" | "wireframe" | "xray";
let activeView: ViewPreset = "iso";
let displayStyle: DisplayStyle = "shaded";
let axisLock: "x" | "z" | undefined;
let sectionEnabled = false;
let sectionHeight = 1.5;
const $ = (selector: string) => document.querySelector<HTMLElement>(selector)!;
const status = (message: string) => { $("#status").textContent = message; };
const safe = (value: string) => value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;").replaceAll(">", "&gt;");
const imageData = (file: File) => new Promise<string>((resolve, reject) => {
  const reader = new FileReader();
  reader.onload = () => resolve(String(reader.result));
  reader.onerror = () => reject(reader.error);
  reader.readAsDataURL(file);
});
const colors: Record<Kind, string> = { Wall: finishes.plaster.color, Slab: finishes.concrete.color, Space: "#9a9cdb",
  Door: finishes.timber.color, Window: finishes.glass.color, Opening: "#e6c879", Face: finishes.paint.color,
  Box: finishes.metal.color, Mesh: finishes.paint.color };
const toolHints: Record<Tool, string> = {
  select: "Select a part to inspect and edit it. Drag empty space to orbit the model.",
  move: "Drag freely to move a part · Shift moves vertically · Snap is optional · Measurements accepts X,Z.",
  rotate: "Drag a part to rotate · Shift snaps to 15° · Measurements accepts degrees.",
  scale: "Drag a part horizontally to resize all three dimensions.",
  rectangle: "Click two corners on the ground, or enter width,depth after the first click.",
  pushpull: "Drag a face of a CAD solid outward, or type an exact height in Measurements.",
  paint: "Choose a material in Properties, then click a face to paint it."
};

function fallbackMaterial(e: Element): MaterialDefinition {
  const finish = e.finish ?? defaultFinish(e.kind);
  const [tileWidth, tileHeight] = defaultTile(finish);
  return { id: "", label: finishes[finish].label, finish,
    color: e.color ?? finishes[finish].color, tileWidth, tileHeight, grainDegrees: 0,
    opacity: finishes[finish].alpha, texture: initialElementTexture(finish, !!e.imported) };
}
function surfaceMaterial(e: Element, definition: MaterialDefinition, index: number): StandardMaterial {
  const result = new StandardMaterial(`surface-${e.id}-${index}`, scene);
  const finish = finishes[definition.finish];
  const color = displayStyle === "hidden" ? "#f5f6f2" : e.color ?? definition.color;
  const surfaceColor = Color3.FromHexString(color);
  result.diffuseColor = surfaceColor;
  result.specularColor = Color3.FromHexString(finish.specular);
  result.specularPower = finish.power;
  const texture = displayStyle === "shaded" ? materialTexture(definition) : undefined;
  if (texture) {
    result.diffuseTexture = texture;
    result.emissiveTexture = texture;
    result.diffuseColor = Color3.Black();
    result.useAlphaFromDiffuseTexture = definition.texture === "image";
  }
  result.disableLighting = displayStyle === "hidden" || definition.finish === "glass" || !!result.emissiveTexture;
  result.emissiveColor = result.disableLighting ? surfaceColor : Color3.Black();
  result.alpha = e.kind === "Space" ? .13 : e.kind === "Opening" ? .18 :
    displayStyle === "xray" ? .38 : definition.opacity;
  result.wireframe = displayStyle === "wireframe";
  result.backFaceCulling = false;
  result.twoSidedLighting = true;
  return result;
}

function draw() {
  for (const mesh of meshes.values()) {
    const materials = mesh.material instanceof MultiMaterial ? mesh.material.subMaterials : [mesh.material];
    const shared = [...Object.values(builtInTextures), ...[...importedImages.values()].map(item => item.texture)];
    for (const material of materials) if (material instanceof StandardMaterial) {
      if (shared.some(texture => texture === material.diffuseTexture)) material.diffuseTexture = null;
      if (shared.some(texture => texture === material.emissiveTexture)) material.emissiveTexture = null;
    }
    mesh.dispose(false, true);
  }
  meshes = new Map();
  const elements = doc.elements;
  for (const e of elements) {
    const imported = e.imported ? doc.importedMesh(e.id, e) : undefined;
    const hosted = e.kind === "Wall" ? doc.hostedOpenings(e.id, elements) : [];
    const originalVoids = hosted.filter(opening => opening.imported).flatMap(opening => {
      const original = doc.originalOpening(opening.id);
      return original ? [original] : [];
    });
    const openings = hosted.filter(opening => !opening.imported || doc.originalOpening(opening.id));
    const insert = !imported && (e.kind === "Door" || e.kind === "Window");
    const simpleBox = !imported && e.kind !== "Face" && !insert && !openings.length;
    const faceMaterials = simpleBox ? (["px", "nx", "py", "ny", "pz", "nz"] as BoxFaceKey[]).map(face =>
      doc.faceMaterial(e.id, face) ?? doc.material(e.materialId ?? "") ?? fallbackMaterial(e)) : [];
    const leafMaterial = doc.material(e.materialId ?? "") ?? fallbackMaterial(e);
    const frameMaterial: MaterialDefinition = { ...fallbackMaterial({ ...e, color: undefined,
      finish: e.kind === "Window" ? "paint" : "timber" }),
      color: e.kind === "Window" ? "#e7e2d8" : "#8d6546", texture: "none", opacity: 1 };
    let mesh = insert ? hostedInsert(e, leafMaterial, frameMaterial, scene) :
      simpleBox ? materialBox(e, faceMaterials, scene) : imported ? new Mesh(e.id, scene) : e.kind === "Face" ?
      MeshBuilder.CreateGround(e.id, { width: e.width, height: e.depth }, scene) :
      MeshBuilder.CreateBox(e.id, { width: e.width, height: e.height, depth: e.depth }, scene);
    if (imported) {
      const data = new VertexData();
      data.positions = imported.positions;
      data.indices = imported.indices;
      if (e.kind === "Mesh") {
        const uvs = imported.uvs.slice();
        const bindings = doc.objMaterials(e.id);
        for (const run of imported.runs) {
          const definition = bindings.get(run.name?.trim() || "Default OBJ") ?? leafMaterial;
          for (let index = run.start; index < run.start + run.count; index += 3) {
            const points = [0, 1, 2].map(offset => imported.positions.slice((index + offset) * 3, (index + offset) * 3 + 3));
            const projected = projectTriangleUvs(points, definition);
            const angle = definition.grainDegrees * Math.PI / 180;
            for (let corner = 0; corner < 3; corner++) {
              const offset = (index + corner) * 2;
              const u = imported.uvs[offset], v = imported.uvs[offset + 1];
              if (Number.isFinite(u) && Number.isFinite(v)) {
                uvs[offset] = (u * Math.cos(angle) - v * Math.sin(angle)) / definition.tileWidth;
                uvs[offset + 1] = (u * Math.sin(angle) + v * Math.cos(angle)) / definition.tileHeight;
              } else {
                uvs[offset] = projected[corner * 2];
                uvs[offset + 1] = projected[corner * 2 + 1];
              }
            }
          }
        }
        data.uvs = uvs;
      }
      const normals: number[] = [];
      VertexData.ComputeNormals(data.positions, data.indices, normals);
      data.normals = normals;
      data.applyToMesh(mesh);
    }
    if (openings.length) {
      try {
        const cut = cutWallOpenings(mesh, e, openings, scene, originalVoids);
        mesh.dispose();
        mesh = cut;
      } catch (error) { console.warn("Could not cut wall opening", e.id, error); }
    }
    mesh.position.set(e.x, e.y, e.z);
    mesh.rotation.y = e.imported ? 0 : e.rotation * Math.PI / 180;
    const material = new StandardMaterial(`material-${e.id}`, scene);
    const elementMaterial = doc.material(e.materialId ?? "") ?? fallbackMaterial(e);
    const finishKey = e.finish ?? defaultFinish(e.kind);
    const finish = finishes[finishKey];
    const baseColor = e.color && /^#[0-9a-fA-F]{6}$/.test(e.color) ? e.color :
      doc.material(e.materialId ?? "")?.color ?? (e.finish ? finish.color : colors[e.kind]);
    const visibleColor = displayStyle === "hidden" ? "#f5f6f2" : baseColor;
    const surfaceColor = Color3.FromHexString(visibleColor);
    material.diffuseColor = surfaceColor;
    material.specularColor = Color3.FromHexString(finish.specular);
    material.specularPower = finish.power;
    if (!simpleBox && !insert && e.kind !== "Mesh" && elementMaterial.texture !== "none" && displayStyle === "shaded") {
      applyProjectedUvs(mesh, elementMaterial);
      const texture = materialTexture(elementMaterial);
      if (texture) {
        material.diffuseTexture = texture;
        material.emissiveTexture = texture;
        material.diffuseColor = Color3.Black();
        material.useAlphaFromDiffuseTexture = elementMaterial.texture === "image";
      }
    }
    // Architectural textures retain their chosen color on IFC and CAD faces.
    // Their image supplies the detail; the material color tints the emissive map.
    material.disableLighting = displayStyle === "hidden" || !!material.emissiveTexture ||
      e.imported || openings.length > 0 || e.kind === "Face";
    material.emissiveColor = material.disableLighting ? surfaceColor : Color3.Black();
    material.alpha = e.kind === "Space" ? .13 : e.kind === "Opening" ? .18 :
      displayStyle === "xray" ? .38 : doc.material(e.materialId ?? "")?.opacity ?? (e.kind === "Face" ? .75 : finish.alpha);
    material.wireframe = displayStyle === "wireframe";
    material.backFaceCulling = false;
    material.twoSidedLighting = true;
    if (simpleBox || insert || e.kind === "Mesh") {
      material.dispose();
      const multi = new MultiMaterial(`faces-${e.id}`, scene);
      multi.subMaterials = e.kind === "Mesh" && imported ? imported.runs.map((run, index) =>
        surfaceMaterial(e, doc.objMaterials(e.id).get(run.name?.trim() || "Default OBJ") ?? leafMaterial, index)) :
        insert ? [surfaceMaterial(e, leafMaterial, 0),
        surfaceMaterial({ ...e, color: undefined }, frameMaterial, 1)] :
        faceMaterials.map((definition, index) => surfaceMaterial(e, definition, index));
      mesh.material = multi;
      if (e.kind === "Mesh" && imported) {
        mesh.subMeshes = [];
        imported.runs.forEach((run, index) => new SubMesh(index, run.start, run.count, run.start, run.count, mesh));
      }
    } else mesh.material = material;
    mesh.renderOutline = selected === e.id;
    mesh.outlineColor = Color3.FromHexString("#ffd575");
    mesh.outlineWidth = .045;
    if (displayStyle !== "wireframe") {
      const positions = mesh.getVerticesData(VertexBuffer.PositionKind);
      const indices = mesh.getIndices();
      if (positions?.length && indices?.length) {
        const borders = featureEdges(positions, indices);
        if (borders.length) {
          const lines = MeshBuilder.CreateLineSystem(`borders-${e.id}`, {
            lines: borders.map(([a, b]) => [new Vector3(...a), new Vector3(...b)])
          }, scene);
          lines.parent = mesh;
          lines.color = Color3.FromHexString(selected === e.id ? "#ffd575" :
            finishKey === "timber" ? "#634d39" : contrastEdge(visibleColor));
          lines.alpha = 1;
          lines.isPickable = false;
          lines.isVisible = !e.hidden && e.kind !== "Opening";
          if (lines.material) lines.material.zOffset = -2;
        }
      }
    }
    mesh.isVisible = !e.hidden && e.kind !== "Opening";
    meshes.set(e.id, mesh);
  }
  $("#count").textContent = String(elements.length);
  $("#list").innerHTML = elements.map(e => `<button class="item ${e.id === selected || selectedIds.has(e.id) ? "active" : ""} ${e.hidden ? "is-hidden" : ""}" data-id="${safe(e.id)}"><span class="kind ${e.kind.toLowerCase()}">${e.kind === "Space" ? "▢" : "▣"}</span><span><strong>${safe(e.label)}</strong><small>${e.kind}${e.imported ? e.kind === "Mesh" ? " · OBJ mesh" : " · IFC mesh" : ""}${e.hidden ? " · hidden" : ""} · ${e.width.toFixed(2)} × ${e.height.toFixed(2)} × ${e.depth.toFixed(2)} m</small></span></button>`).join("") || '<p class="empty">No elements yet.</p>';
  $("#list").querySelectorAll<HTMLButtonElement>("[data-id]").forEach(button => button.onclick = event => chooseElement(button.dataset.id, event.ctrlKey || event.metaKey));
  renderLayers(elements);
  renderScenes();
  renderProperties();
}

function renderScenes() {
  $("#scenes").innerHTML = doc.scenes.map(scene => `<div class="scene-row"><button data-scene="${safe(scene.id)}">${safe(scene.label)}</button><button data-remove-scene="${safe(scene.id)}" title="Delete scene" aria-label="Delete ${safe(scene.label)}">×</button></div>`).join("");
  $("#scenes").querySelectorAll<HTMLButtonElement>("[data-scene]").forEach(button => button.onclick = () => {
    const saved = doc.scenes.find(scene => scene.id === button.dataset.scene);
    if (!saved) return;
    camera.mode = saved.orthographic ? Camera.ORTHOGRAPHIC_CAMERA : Camera.PERSPECTIVE_CAMERA;
    planView = saved.orthographic;
    camera.alpha = saved.alpha; camera.beta = saved.beta; camera.radius = saved.radius;
    camera.setTarget(new Vector3(saved.targetX, saved.targetY, saved.targetZ));
    sectionEnabled = saved.sectionEnabled; sectionHeight = saved.sectionHeight;
    document.querySelector<HTMLInputElement>("#section-enabled")!.checked = sectionEnabled;
    document.querySelector<HTMLInputElement>("#section-height")!.value = String(sectionHeight);
    updateSection();
    if (planView) updatePlanBounds();
    document.querySelectorAll<HTMLButtonElement>("[data-view]").forEach(action => action.classList.remove("active"));
    $(".view-label").textContent = `${saved.label.toUpperCase()} · ${planView ? "ORTHOGRAPHIC" : "PERSPECTIVE"}`;
    status(`Restored scene ${saved.label}`);
  });
  $("#scenes").querySelectorAll<HTMLButtonElement>("[data-remove-scene]").forEach(button => button.onclick = () =>
    mutate(() => doc.removeScene(button.dataset.removeScene!)));
}

function renderLayers(elements: readonly Element[]) {
  const layers = new Map<string, Element[]>();
  for (const element of elements) {
    const name = element.layer ?? "Default";
    layers.set(name, [...(layers.get(name) ?? []), element]);
  }
  $("#layers").innerHTML = [...layers].map(([name, members]) => `<div class="layer-row"><span>${safe(name)} <small>${members.length}</small></span><button data-layer="${safe(name)}" title="Toggle layer visibility" aria-label="Toggle ${safe(name)} visibility">${members.every(member => member.hidden) ? "◌" : "◉"}</button></div>`).join("") || '<p class="empty">No layers yet.</p>';
  $("#layers").querySelectorAll<HTMLButtonElement>("[data-layer]").forEach(button => button.onclick = () => {
    const members = layers.get(button.dataset.layer ?? "") ?? [];
    const hide = members.some(member => !member.hidden);
    mutate(() => members.forEach(member => doc.setHidden(member.id, hide)));
    status(`${hide ? "Hid" : "Showed"} layer ${button.dataset.layer}`);
  });
}

function renderProperties() {
  const e = doc.elements.find(item => item.id === selected);
  if (!e) {
    $("#properties").innerHTML = '<p class="empty">Select an element to edit its geometry and appearance.</p>';
    return;
  }
  const host = doc.hostWall(e.id);
  const opening = e.kind === "Door" || e.kind === "Window" ? doc.relatedOpening(e.id) : undefined;
  $("#properties").innerHTML = `<label class="field">Name<input id="label" value="${safe(e.label)}"></label><label class="field">Element color override<input id="color" type="color" value="${e.color && /^#[0-9a-fA-F]{6}$/.test(e.color) ? e.color : colors[e.kind]}"></label><div class="meta"><span>Type</span><strong>${e.kind}${e.imported ? e.kind === "Mesh" ? " · OBJ mesh" : " · IFC mesh" : ""}</strong></div>${host ? `<div class="meta"><span>Host wall</span><strong>${safe(doc.elements.find(item => item.id === host)?.label ?? "Linked wall")}</strong></div>` : ""}${opening ? `<div class="meta"><span>Opening</span><strong>${safe(doc.elements.find(item => item.id === opening)?.label ?? "Linked opening")}</strong></div>` : ""}<h3>Position <small>m</small></h3><div class="number-grid">${fields.slice(0, 3).map(field => numberInput(e, field)).join("")}</div><h3>Rotation <small>degrees about Y</small></h3><div class="number-grid">${numberInput(e, "rotation")}</div><h3>Dimensions <small>m</small></h3><div class="number-grid">${fields.slice(3).map(field => numberInput(e, field)).join("")}</div>${(e.imported && e.kind !== "Mesh") || e.kind === "Opening" ? "" : '<button id="delete" class="danger">Delete element</button>'}`;
  const activeMaterial = doc.material(e.materialId ?? "");
  const libraryMaterial = doc.material(paintMaterialId ?? "") ?? activeMaterial;
  $("#color").closest("label")!.insertAdjacentHTML("beforebegin", `<div class="material-editor"><h3>Materials</h3><label class="field">Element material<select id="element-material"><option value="">By element type</option>${doc.materials.map(item => `<option value="${safe(item.id)}" ${item.id === e.materialId ? "selected" : ""}>${safe(item.label)}</option>`).join("")}</select></label><div class="material-create"><input id="new-material-name" aria-label="New material name" placeholder="New material name"><button id="new-material">Create</button></div><label class="field">Paint with<select id="paint-material">${doc.materials.map(item => `<option value="${safe(item.id)}" ${item.id === libraryMaterial?.id ? "selected" : ""}>${safe(item.label)}</option>`).join("")}</select></label>${libraryMaterial ? `<label class="field">Material name<input id="material-name" value="${safe(libraryMaterial.label)}"></label><label class="field">Material type<select id="material-finish">${Object.entries(finishes).map(([key, value]) => `<option value="${key}" ${key === libraryMaterial.finish ? "selected" : ""}>${value.label}</option>`).join("")}</select></label><label class="field">Material color<input id="material-color" type="color" value="${libraryMaterial.color}"></label><label class="field">Texture<select id="material-texture">${texturePresets.map(key => `<option value="${key}" ${key === libraryMaterial.texture ? "selected" : ""}>${textureLabels[key]}</option>`).join("")}</select></label><label class="field">Opacity · %<input id="material-opacity" type="number" min="0" max="100" step="1" value="${Math.round(libraryMaterial.opacity * 100)}"></label><div class="number-grid"><label class="field">Texture width · m<input id="tile-width" type="number" min="0.001" step="0.01" value="${libraryMaterial.tileWidth}"></label><label class="field">Texture height · m<input id="tile-height" type="number" min="0.001" step="0.01" value="${libraryMaterial.tileHeight}"></label><label class="field">Grain angle · °<input id="grain-degrees" type="number" step="1" value="${libraryMaterial.grainDegrees}"></label></div>` : ""}<p>Material edits update every element using that material. Paint uses the selected material on one face.</p></div>`);
  $(".material-editor .material-create").insertAdjacentHTML("afterend",
    '<button id="import-image" type="button">Import PNG/JPG as material</button><input id="image-file" type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" hidden>');
  $("#import-image").onclick = () => (document.querySelector<HTMLInputElement>("#image-file")!).click();
  document.querySelector<HTMLInputElement>("#image-file")!.onchange = async event => {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    try {
      if (file.size > 8_000_000) throw new Error("Image must be under 8 MB");
      const data = await imageData(file);
      mutate(() => {
        const material = doc.addImageMaterial(file.name.replace(/\.(png|jpe?g)$/i, ""), data);
        doc.assignMaterial(e.id, material.id);
        paintMaterialId = material.id;
      });
      status(`Imported image material ${file.name}`);
    } catch (error) { status(`Could not import image: ${String(error)}`); }
  };
  $("#element-material").addEventListener("change", event => mutate(() => {
    const id = (event.target as HTMLSelectElement).value || undefined;
    doc.assignMaterial(e.id, id); paintMaterialId = id;
  }));
  $("#paint-material").addEventListener("change", event => {
    paintMaterialId = (event.target as HTMLSelectElement).value;
    renderProperties();
  });
  $("#new-material").addEventListener("click", () => {
    const name = document.querySelector<HTMLInputElement>("#new-material-name")!.value.trim();
    try { mutate(() => { const created = doc.addMaterial(name, libraryMaterial?.finish ?? e.finish ?? defaultFinish(e.kind), libraryMaterial?.color); doc.assignMaterial(e.id, created.id); paintMaterialId = created.id; }); }
    catch (error) { status(String(error)); }
  });
  if (libraryMaterial) {
    const edit = (changes: Partial<MaterialDefinition>) => { try { mutate(() => doc.updateMaterial(libraryMaterial.id, changes)); } catch (error) { status(String(error)); renderProperties(); } };
    $("#material-name").addEventListener("change", event => edit({ label: (event.target as HTMLInputElement).value }));
    $("#material-finish").addEventListener("change", event => edit({ finish: (event.target as HTMLSelectElement).value as Finish }));
    $("#material-color").addEventListener("change", event => edit({ color: (event.target as HTMLInputElement).value }));
    $("#material-texture").addEventListener("change", event => edit({ texture: (event.target as HTMLSelectElement).value as TexturePreset }));
    $("#material-opacity").addEventListener("change", event => edit({ opacity: Number((event.target as HTMLInputElement).value) / 100 }));
    for (const [id, field] of [["tile-width", "tileWidth"], ["tile-height", "tileHeight"], ["grain-degrees", "grainDegrees"]] as const)
      $("#" + id).addEventListener("change", event => edit({ [field]: Number((event.target as HTMLInputElement).value) }));
  }
  const currentFinish = e.finish;
  $("#color").closest("label")!.insertAdjacentHTML("beforebegin", `<label class="field">Finish<select id="finish"><option value="" ${!currentFinish ? "selected" : ""}>By element type</option>${Object.entries(finishes)
    .map(([key, value]) => `<option value="${key}" ${key === currentFinish ? "selected" : ""}>${value.label}</option>`).join("")}</select></label>`);
  $("#finish").closest("label")!.insertAdjacentHTML("beforebegin", `<div class="model-tags"><label class="field">Layer<input id="layer" value="${safe(e.layer ?? "Default")}"></label><label class="field">Group<input id="group" placeholder="Optional" value="${safe(e.group ?? "")}"></label></div><label class="visibility-field"><input id="visible" type="checkbox" ${e.hidden ? "" : "checked"}> Visible in viewport</label>`);
  document.querySelector<HTMLInputElement>("#color")!.value = e.color && /^#[0-9a-fA-F]{6}$/.test(e.color) ? e.color :
    activeMaterial?.color ?? (currentFinish ? finishes[currentFinish].color : colors[e.kind]);
  $("#label").addEventListener("change", event => mutate(() => doc.setLabel(e.id, (event.target as HTMLInputElement).value.trim() || e.label)));
  $("#layer").addEventListener("change", event => mutate(() => doc.setLayer(e.id, (event.target as HTMLInputElement).value)));
  $("#group").addEventListener("change", event => mutate(() => doc.setGroup(e.id, (event.target as HTMLInputElement).value)));
  $("#visible").addEventListener("change", event => mutate(() => doc.setHidden(e.id, !(event.target as HTMLInputElement).checked)));
  $("#finish").addEventListener("change", event => mutate(() => {
    const value = (event.target as HTMLSelectElement).value;
    doc.setFinish(e.id, value ? value as Finish : undefined);
  }));
  $("#color").addEventListener("change", event => mutate(() => doc.setColor(e.id, (event.target as HTMLInputElement).value)));
  $("#properties").querySelectorAll<HTMLInputElement>("[data-field]").forEach(input => input.addEventListener("change", () => {
    try { mutate(() => doc.setNumber(e.id, input.dataset.field as Field, Number(input.value))); }
    catch (error) { status(String(error)); renderProperties(); }
  }));
  if ((!e.imported || e.kind === "Mesh") && e.kind !== "Opening") $("#delete").onclick = () => mutate(() => { doc.remove(e.id); selected = undefined; selectedIds.delete(e.id); });
}
function numberInput(e: Element, field: Field) { return `<label class="field">${field.toUpperCase()}<input type="number" step="0.1" ${["width", "height", "depth"].includes(field) ? 'min="0.001"' : ""} data-field="${field}" value="${e[field]}"></label>`; }
function select(id?: string, additive = false) {
  if (!additive) selectedIds.clear();
  if (id && additive && selectedIds.has(id)) selectedIds.delete(id);
  else if (id) selectedIds.add(id);
  selected = id && selectedIds.has(id) ? id : [...selectedIds].at(-1);
  draw();
}
function clearPlacement() {
  placing = undefined;
  document.querySelectorAll<HTMLButtonElement>("[data-add='Door'],[data-add='Window']").forEach(button => {
    button.classList.remove("active");
    button.setAttribute("aria-pressed", "false");
  });
  $("#create-hint").textContent = "Choose Door or Window, then click a wall.";
  canvas.style.cursor = tool === "select" ? "default" : "crosshair";
  $(".view-hint").textContent = toolHints[tool];
}
function placeOnWall(wallId: string, point?: { x: number; y: number; z: number }) {
  const kind = placing;
  if (!kind) return;
  clearPlacement();
  mutate(() => { selected = doc.addHosted(kind, wallId, point).id; selectedIds = new Set([selected]); });
  status(`Added ${kind.toLowerCase()} to wall`);
}
function chooseElement(id?: string, additive = false) {
  if (placing) {
    const wall = doc.elements.find(element => element.id === id && element.kind === "Wall");
    if (wall) placeOnWall(wall.id);
    else status(`Choose a wall in the model list or viewport for the ${placing.toLowerCase()}`);
    return;
  }
  select(id, additive);
}
function mutate(change: () => void) { cancelDrag(); const before = doc.snapshot(); change(); undo.push(before); redo = []; draw(); status("Document changed"); }
function restore(stack: typeof undo, opposite: typeof redo) { cancelDrag(); const previous = stack.pop(); if (!previous) return; opposite.push(doc.snapshot()); doc = new CadDocument(previous); selectedIds = new Set([...selectedIds].filter(id => doc.elements.some(e => e.id === id))); if (!doc.elements.some(e => e.id === selected)) selected = undefined; draw(); }
function fit() {
  const all = doc.elements.filter(element => !element.hidden);
  if (!all.length) { camera.setTarget(Vector3.Zero()); camera.radius = 13; return; }
  const min = new Vector3(Infinity, Infinity, Infinity), max = new Vector3(-Infinity, -Infinity, -Infinity);
  for (const e of all) {
    const angle = e.rotation * Math.PI / 180;
    const halfX = (Math.abs(Math.cos(angle)) * e.width + Math.abs(Math.sin(angle)) * e.depth) / 2;
    const halfZ = (Math.abs(Math.sin(angle)) * e.width + Math.abs(Math.cos(angle)) * e.depth) / 2;
    min.minimizeInPlace(new Vector3(e.x - halfX, e.y - e.height / 2, e.z - halfZ));
    max.maximizeInPlace(new Vector3(e.x + halfX, e.y + e.height / 2, e.z + halfZ));
  }
  camera.setTarget(min.add(max).scale(.5)); camera.radius = Math.max(5, max.subtract(min).length() * 1.7);
}
function exportParts(visibleOnly: boolean): ExportPart[] {
  const result: ExportPart[] = [];
  for (const element of doc.elements) {
    if (element.kind === "Opening" || visibleOnly && element.hidden) continue;
    const mesh = meshes.get(element.id);
    const positions = mesh?.getVerticesData(VertexBuffer.PositionKind);
    const indices = mesh?.getIndices();
    if (!mesh || !positions?.length || !indices?.length) continue;
    const matrix = mesh.computeWorldMatrix(true);
    const world: number[] = [];
    for (let i = 0; i < positions.length; i += 3) {
      const point = Vector3.TransformCoordinates(new Vector3(positions[i], positions[i + 1], positions[i + 2]), matrix);
      world.push(point.x, point.y, point.z);
    }
    result.push({ name: element.label, kind: element.kind, layer: element.layer ?? "Default", positions: world, indices: Array.from(indices) });
  }
  return result;
}
function downloadText(filename: string, type: string, data: string) {
  const url = URL.createObjectURL(new Blob([data], { type }));
  const link = document.createElement("a");
  link.href = url; link.download = filename; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function quantityRows(): QuantityRow[] {
  return exportParts(false).map(part => {
    const analysis = analyzeMesh(part.positions, part.indices);
    return { name: part.name, kind: part.kind ?? "Element", layer: part.layer,
      area: analysis.surfaceArea, volume: analysis.volume, closed: analysis.volume !== undefined };
  });
}
function updatePlanBounds() {
  const halfHeight = Math.max(1, camera.radius * .55);
  const aspect = Math.max(.1, engine.getRenderWidth() / Math.max(1, engine.getRenderHeight()));
  camera.orthoTop = halfHeight;
  camera.orthoBottom = -halfHeight;
  camera.orthoLeft = -halfHeight * aspect;
  camera.orthoRight = halfHeight * aspect;
}
function updateSection() {
  scene.clipPlane = sectionEnabled ? new Plane(0, -1, 0, sectionHeight) : null;
  $("#section-value").textContent = `${sectionHeight.toFixed(2)} m`;
}
function setViewPreset(preset: ViewPreset) {
  activeView = preset;
  planView = preset !== "iso";
  const angles: Record<ViewPreset, [number, number]> = {
    iso: [-Math.PI / 3, Math.PI / 3.1], top: [-Math.PI / 2, .01],
    front: [-Math.PI / 2, Math.PI / 2], right: [0, Math.PI / 2]
  };
  camera.mode = planView ? Camera.ORTHOGRAPHIC_CAMERA : Camera.PERSPECTIVE_CAMERA;
  [camera.alpha, camera.beta] = angles[preset];
  if (planView) updatePlanBounds();
  $("#plan").classList.toggle("active", preset === "top");
  $("#plan").setAttribute("aria-pressed", String(preset === "top"));
  document.querySelectorAll<HTMLButtonElement>("[data-view]").forEach(button => {
    const active = button.dataset.view === preset;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  $(".view-label").innerHTML = `${preset === "iso" ? "ISOMETRIC · PERSPECTIVE" : `${preset.toUpperCase()} · ORTHOGRAPHIC`} <span>·</span> metres`;
  status(`${preset[0].toUpperCase() + preset.slice(1)} view`);
}
function cancelRectangle() {
  rectangleStart = undefined;
  if (rectanglePreview) { rectanglePreview.material?.dispose(); rectanglePreview.dispose(); rectanglePreview = undefined; }
}
function groundPoint(event: PointerEvent): GroundPoint | undefined {
  const point = pointOnPlane(event, 0);
  if (!point) return;
  const snapped = snapEnabled ? snapXZ(point.x, point.z, doc.elements, .25,
    Math.max(.12, camera.radius * .012)) : { x: point.x, z: point.z, source: "grid" as const };
  return { x: snapped.x, z: snapped.z };
}
function previewRectangle(second: GroundPoint) {
  if (!rectangleStart) return;
  let rectangle;
  try { rectangle = rectangleFromCorners(rectangleStart, second); }
  catch { return; }
  if (!rectanglePreview) {
    rectanglePreview = MeshBuilder.CreateGround("rectangle-preview", { width: 1, height: 1 }, scene);
    const material = new StandardMaterial("rectangle-preview", scene);
    material.diffuseColor = Color3.FromHexString("#88d1b3");
    material.alpha = .55;
    material.backFaceCulling = false;
    rectanglePreview.material = material;
    rectanglePreview.isPickable = false;
  }
  rectanglePreview.scaling.set(rectangle.width, 1, rectangle.depth);
  rectanglePreview.position.set(rectangle.x, .006, rectangle.z);
  status(`Rectangle · ${rectangle.width.toFixed(2)} × ${rectangle.depth.toFixed(2)} m · click or type dimensions`);
}
function commitRectangle(second: GroundPoint) {
  if (!rectangleStart) return;
  let rectangle;
  try { rectangle = rectangleFromCorners(rectangleStart, second); }
  catch (error) { status(String(error)); return; }
  cancelRectangle();
  mutate(() => {
    const face = doc.add("Face", { x: rectangle.x, y: 0, z: rectangle.z });
    doc.setNumber(face.id, "width", rectangle.width);
    doc.setNumber(face.id, "depth", rectangle.depth);
    selected = face.id; selectedIds = new Set([face.id]);
  });
  status(`Drew ${rectangle.width.toFixed(2)} × ${rectangle.depth.toFixed(2)} m face · Push/Pull to extrude`);
}
function setTool(next: Tool) {
  cancelDrag(); cancelRectangle(); clearPlacement(); tool = next;
  axisLock = undefined;
  $("#axis-lock").hidden = true;
  $(".view-hint").textContent = toolHints[tool];
  document.querySelectorAll<HTMLButtonElement>("[data-tool]").forEach(button => {
    const active = button.dataset.tool === tool;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  canvas.style.cursor = tool === "select" ? "default" : "crosshair";
  status(tool === "paint" ? "Paint · choose a material in Properties, then click a CAD face" :
    tool === "rectangle" ? "Rectangle · click two corners on the ground plane" :
    tool === "pushpull" ? "Push/Pull · drag a CAD face outward, or type a height" :
    `${tool[0].toUpperCase()}${tool.slice(1)} tool · drag an element${tool === "move" ? " to reposition it" : tool === "rotate" ? " to turn it about Y" : tool === "scale" ? " to resize it" : " to orbit"}`);
}
function pointOnPlane(event: PointerEvent, y: number): Vector3 | undefined {
  const rect = canvas.getBoundingClientRect();
  const ray = scene.createPickingRay(event.clientX - rect.left, event.clientY - rect.top, Matrix.Identity(), camera);
  if (Math.abs(ray.direction.y) < 1e-5) return;
  const distance = (y - ray.origin.y) / ray.direction.y;
  return distance > 0 ? ray.origin.add(ray.direction.scale(distance)) : undefined;
}
function faceDirection(face: BoxFace, rotation: number): Vector3 {
  const angle = rotation * Math.PI / 180;
  if (face.axis === "x") return new Vector3(face.sign * Math.cos(angle), 0, -face.sign * Math.sin(angle));
  if (face.axis === "z") return new Vector3(face.sign * Math.sin(angle), 0, face.sign * Math.cos(angle));
  return new Vector3(0, face.sign, 0);
}
function projectedAxis(point: Vector3, direction: Vector3): { x: number; y: number } {
  const viewport = camera.viewport.toGlobal(engine.getRenderWidth(), engine.getRenderHeight());
  const matrix = scene.getTransformMatrix();
  const start = Vector3.Project(point, Matrix.Identity(), matrix, viewport);
  const end = Vector3.Project(point.add(direction), Matrix.Identity(), matrix, viewport);
  const rect = canvas.getBoundingClientRect();
  const axis = { x: (end.x - start.x) * rect.width / engine.getRenderWidth(),
    y: (end.y - start.y) * rect.height / engine.getRenderHeight() };
  return Math.hypot(axis.x, axis.y) < 4 ? { x: 0, y: -50 } : axis;
}
function cancelDrag() {
  if (!drag) return;
  if (canvas.hasPointerCapture(drag.pointerId)) canvas.releasePointerCapture(drag.pointerId);
  if (drag.temporary) { drag.preview.material?.dispose(); drag.preview.dispose(); }
  drag = undefined;
  camera.attachControl(canvas, true);
  draw();
}
canvas.addEventListener("pointerdown", event => {
  if (placing && event.button === 0) {
    const rect = canvas.getBoundingClientRect();
    const wallIds = new Set(doc.elements.filter(element => element.kind === "Wall").map(element => element.id));
    const picked = scene.pick(event.clientX - rect.left, event.clientY - rect.top, mesh => wallIds.has(mesh.name));
    if (picked?.hit && picked.pickedMesh && wallIds.has(picked.pickedMesh.name)) {
      event.preventDefault(); event.stopImmediatePropagation();
      const point = picked.pickedPoint;
      placeOnWall(picked.pickedMesh.name, point ? { x: point.x, y: point.y, z: point.z } : undefined);
    } else status(`Click a wall surface or choose a wall in the model list`);
    return;
  }
  if (tool === "rectangle" && event.button === 0) {
    const point = groundPoint(event);
    if (!point) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if (rectangleStart) commitRectangle(point);
    else { rectangleStart = point; status("Choose the opposite corner, or type width,depth in Measurements"); }
    return;
  }
  if (tool === "paint" && event.button === 0) {
    const rect = canvas.getBoundingClientRect();
    const eligible = new Set(doc.elements.filter(e => !e.imported && e.kind !== "Face" &&
      !(e.kind === "Wall" && doc.hostedOpenings(e.id).length)).map(e => e.id));
    const picked = scene.pick(event.clientX - rect.left, event.clientY - rect.top, mesh => eligible.has(mesh.name));
    const id = picked?.pickedMesh?.name;
    if (!id || !eligible.has(id)) { status("Paint works on CAD solid faces without openings"); return; }
    event.preventDefault(); event.stopImmediatePropagation();
    const element = doc.elements.find(e => e.id === id)!;
    const materialId = doc.material(paintMaterialId ?? "")?.id ?? element.materialId ?? doc.materials[0]?.id;
    if (!materialId) { status("Create a material in Properties first"); select(id); return; }
    const face = boxFaceFromNormal(picked?.getNormal(true) ?? Vector3.Up(), element.rotation);
    const key = `${face.sign > 0 ? "p" : "n"}${face.axis}` as BoxFaceKey;
    mutate(() => doc.setFaceMaterial(id, key, materialId));
    selected = id; selectedIds = new Set([id]); draw();
    status(`Painted ${element.label} ${key} face`);
    return;
  }
  if (tool === "pushpull" && event.button === 0) {
    const rect = canvas.getBoundingClientRect();
    const allowed = new Set(doc.elements.filter(e => !e.imported && ["Face", "Box", "Wall", "Slab"].includes(e.kind)).map(e => e.id));
    const picked = scene.pick(event.clientX - rect.left, event.clientY - rect.top, mesh => allowed.has(mesh.name));
    const id = picked?.pickedMesh?.name;
    if (!id || !allowed.has(id)) { status("Push/Pull works on CAD faces, boxes, walls, and slabs"); return; }
    event.preventDefault(); event.stopImmediatePropagation();
    if (selected !== id) select(id);
    const start = doc.elements.find(e => e.id === id)!;
    let preview = meshes.get(id)!;
    const temporary = start.kind === "Face";
    if (temporary) {
      preview.isVisible = false;
      preview = MeshBuilder.CreateBox("pushpull-preview", { width: start.width, height: 1, depth: start.depth }, scene);
      const material = new StandardMaterial("pushpull-preview", scene);
      material.diffuseColor = Color3.FromHexString(colors.Box);
      material.alpha = .7;
      material.backFaceCulling = false;
      material.twoSidedLighting = true;
      preview.material = material;
      preview.rotation.y = start.rotation * Math.PI / 180;
      preview.scaling.y = start.height;
      preview.position.set(start.x, start.y, start.z);
      preview.isPickable = false;
    }
    const face = temporary ? { axis: "y", sign: 1 } as const :
      boxFaceFromNormal(picked?.getNormal(true) ?? Vector3.Up(), start.rotation);
    const point = picked?.pickedPoint ?? preview.position;
    drag = { pointerId: event.pointerId, id, startX: event.clientX, startY: event.clientY,
      start, preview, temporary, face,
      screenAxis: projectedAxis(point, faceDirection(face, start.rotation)) };
    camera.detachControl();
    canvas.setPointerCapture(event.pointerId);
    return;
  }
  if (event.button !== 0 || tool === "select") return;
  const rect = canvas.getBoundingClientRect();
  const picked = scene.pick(event.clientX - rect.left, event.clientY - rect.top, mesh => meshes.has(mesh.name));
  const id = picked?.pickedMesh?.name;
  if (!id || !meshes.has(id)) return;
  event.preventDefault(); event.stopImmediatePropagation();
  if (selected !== id) select(id);
  const start = doc.elements.find(e => e.id === id)!;
  const preview = meshes.get(id)!;
  const planePoint = pointOnPlane(event, start.y);
  drag = { pointerId: event.pointerId, id, startX: event.clientX, startY: event.clientY, start, preview,
    offset: planePoint ? new Vector3(start.x - planePoint.x, 0, start.z - planePoint.z) : undefined };
  camera.detachControl();
  canvas.setPointerCapture(event.pointerId);
}, true);
canvas.addEventListener("pointermove", event => {
  if (tool === "rectangle" && rectangleStart && !drag) {
    const point = groundPoint(event);
    if (point) previewRectangle(point);
  }
  if (!drag || event.pointerId !== drag.pointerId) return;
  if (tool === "pushpull") {
    const face = drag.face!, axis = drag.screenAxis!;
    const pixels = (event.clientX - drag.startX) * axis.x + (event.clientY - drag.startY) * axis.y;
    const dimension = face.axis === "x" ? drag.start.width : face.axis === "y" ? drag.start.height : drag.start.depth;
    let distance = Math.max(.001 - dimension, pixels / (axis.x * axis.x + axis.y * axis.y));
    if (event.shiftKey) distance = Math.max(.001 - dimension, Math.round(distance / .25) * .25);
    const target = pullBoxFace(drag.start, face, distance);
    drag.pending = target;
    drag.preview.scaling.set(
      (target.width ?? drag.start.width) / drag.start.width,
      (target.height ?? drag.start.height) / (drag.temporary ? 1 : drag.start.height),
      (target.depth ?? drag.start.depth) / drag.start.depth);
    drag.preview.position.set(target.x ?? drag.start.x, target.y ?? drag.start.y, target.z ?? drag.start.z);
    status(`Push/Pull · ${face.axis.toUpperCase()} ${(dimension + distance).toFixed(2)} m · Shift snaps to 0.25 m`);
  } else if (tool === "move") {
    if (event.shiftKey) {
      drag.preview.position.set(drag.start.x, drag.start.y - (event.clientY - drag.startY) * .02, drag.start.z);
      status(`Move · Y ${drag.preview.position.y.toFixed(2)} m`);
      return;
    }
    const point = pointOnPlane(event, drag.start.y);
    if (!point || !drag.offset) return;
    const candidateX = point.x + drag.offset.x, candidateZ = point.z + drag.offset.z;
    const snapped = moveXZ(candidateX, candidateZ, doc.elements, snapEnabled, drag.id,
      Math.max(.08, camera.radius * .008));
    drag.preview.position.x = axisLock === "z" ? drag.start.x : snapped.x;
    drag.preview.position.y = drag.start.y;
    drag.preview.position.z = axisLock === "x" ? drag.start.z : snapped.z;
    status(`Move · X ${drag.preview.position.x.toFixed(2)} m · Z ${drag.preview.position.z.toFixed(2)} m`);
  } else if (tool === "rotate") {
    const degrees = drag.start.rotation + (event.clientX - drag.startX) * .5;
    const angle = event.shiftKey ? Math.round(degrees / 15) * 15 : degrees;
    drag.preview.rotation.y = (drag.start.imported ? angle - drag.start.rotation : angle) * Math.PI / 180;
    status(`Rotate · ${angle.toFixed(1)}° about Y`);
  } else if (tool === "scale") {
    const factor = Math.max(.05, 1 + (event.clientX - drag.startX) / 150);
    drag.preview.scaling.setAll(factor);
    status(`Scale · ${(factor * 100).toFixed(0)}%`);
  }
});
canvas.addEventListener("pointerup", event => {
  if (!drag || event.pointerId !== drag.pointerId) return;
  const { start, preview, id } = drag;
  const x = Number(preview.position.x.toFixed(4)), y = Number(preview.position.y.toFixed(4)), z = Number(preview.position.z.toFixed(4));
  const rotation = Number(((start.imported ? start.rotation : 0) + preview.rotation.y * 180 / Math.PI).toFixed(3));
  const factor = preview.scaling.x;
  const pulled = drag.pending;
  const width = Math.max(.001, Number((start.width * factor).toFixed(4)));
  const height = Math.max(.001, Number((start.height * factor).toFixed(4)));
  const depth = Math.max(.001, Number((start.depth * factor).toFixed(4)));
  const changed = tool === "pushpull" ? !!pulled && Object.entries(pulled).some(([field, value]) =>
    Number(value.toFixed(4)) !== start[field as Field]) : tool === "move" ? x !== start.x || y !== start.y || z !== start.z
    : tool === "rotate" ? rotation !== start.rotation
    : tool === "scale" ? width !== start.width || height !== start.height || depth !== start.depth : false;
  if (changed) {
    try {
      const before = doc.snapshot();
      if (tool === "move") doc.moveGroup(id, { x, y, z });
      else doc.setGeometry(id, tool === "pushpull" ? pulled! : tool === "rotate" ? { rotation } : { width, height, depth });
      if (tool === "pushpull" && start.kind === "Face") doc.setKind(id, "Box");
      undo.push(before); redo = [];
    } catch (error) { cancelDrag(); status(`Could not edit geometry: ${String(error)}`); return; }
  }
  cancelDrag();
  if (changed) status(`${tool === "pushpull" ? "Extruded" : tool === "move" ? "Moved" : tool === "rotate" ? "Rotated" : "Scaled"} ${start.label}`);
});
canvas.addEventListener("pointercancel", cancelDrag);
scene.onPointerDown = (_event, pick) => { if (tool === "select" && pick?.hit && pick.pickedMesh && meshes.has(pick.pickedMesh.name)) select(pick.pickedMesh.name); };
document.querySelectorAll<HTMLButtonElement>("[data-tool]").forEach(button => button.onclick = () => setTool(button.dataset.tool as Tool));
document.querySelectorAll<HTMLButtonElement>("[data-add]").forEach(button => button.onclick = () => {
  const kind = button.dataset.add as Kind;
  if (kind === "Door" || kind === "Window") {
    const walls = doc.elements.filter(element => element.kind === "Wall");
    if (!walls.length) { status("Add a wall first, then add a door or window"); return; }
    const wall = walls.find(element => element.id === selected) ?? (walls.length === 1 ? walls[0] : undefined);
    placing = kind;
    if (wall) { placeOnWall(wall.id); return; }
    document.querySelectorAll<HTMLButtonElement>("[data-add='Door'],[data-add='Window']").forEach(action => {
      const active = action === button;
      action.classList.toggle("active", active);
      action.setAttribute("aria-pressed", String(active));
    });
    $("#create-hint").textContent = `Click a wall in the viewport or model list to add a ${kind.toLowerCase()}.`;
    $(".view-hint").textContent = `Click a wall surface or choose a wall in the model list to place a ${kind.toLowerCase()}.`;
    canvas.style.cursor = "crosshair";
    status(`Choose a wall in the model list or viewport for the ${kind.toLowerCase()}`);
    return;
  }
  clearPlacement();
  mutate(() => {
    const center = camera.target;
    const e = doc.add(kind, { x: Math.round(center.x), y: kind === "Slab" ? -.125 : kind === "Box" ? .5 : 1.5, z: Math.round(center.z) });
    selected = e.id; selectedIds = new Set([e.id]);
  });
});
$("#group-selected").onclick = () => {
  const ids = selectedIds.size ? [...selectedIds] : selected ? [selected] : [];
  if (!ids.length) { status("Select model items first; Ctrl-click to select several"); return; }
  const input = document.querySelector<HTMLInputElement>("#group-name")!;
  const name = input.value.trim() || `Group ${new Set(doc.elements.map(element => element.group).filter(Boolean)).size + 1}`;
  mutate(() => ids.forEach(id => doc.setGroup(id, name)));
  input.value = "";
  status(`Grouped ${ids.length} elements as ${name}`);
};
$("#new").onclick = () => { cancelDrag(); clearPlacement(); doc = new CadDocument(); selected = undefined; selectedIds.clear(); undo = []; redo = []; draw(); fit(); status("New document"); };
let pendingObjFiles: File[] = [];
const objDialog = document.querySelector<HTMLDialogElement>("#obj-dialog")!;
$("#import-obj").onclick = () => (document.querySelector<HTMLInputElement>("#obj-files")!).click();
document.querySelector<HTMLInputElement>("#obj-files")!.onchange = event => {
  pendingObjFiles = Array.from((event.target as HTMLInputElement).files ?? []);
  const obj = pendingObjFiles.find(file => /\.obj$/i.test(file.name));
  if (!obj) { status("Choose an OBJ file with any matching MTL and PNG/JPG files"); return; }
  $("#obj-file-summary").textContent = `${obj.name} · ${pendingObjFiles.filter(file => /\.mtl$/i.test(file.name)).length} MTL · ${pendingObjFiles.filter(file => /\.(png|jpe?g)$/i.test(file.name)).length} images`;
  objDialog.showModal();
};
$("#obj-cancel").onclick = () => objDialog.close();
$("#obj-apply").onclick = async () => {
  const obj = pendingObjFiles.find(file => /\.obj$/i.test(file.name));
  if (!obj) return;
  const button = document.querySelector<HTMLButtonElement>("#obj-apply")!;
  button.disabled = true;
  try {
    if (obj.size > 30_000_000) throw new Error("OBJ must be under 30 MB for a Turtle project");
    const mtl = (await Promise.all(pendingObjFiles.filter(file => /\.mtl$/i.test(file.name)).map(file => file.text()))).join("\n");
    const images: Record<string, string> = {};
    for (const file of pendingObjFiles.filter(file => /\.(png|jpe?g)$/i.test(file.name))) {
      if (file.size > 8_000_000) throw new Error(`${file.name} must be under 8 MB`);
      images[file.name] = await imageData(file);
    }
    const source = await obj.text();
    const available = new Set(Object.keys(images).map(name => name.toLowerCase()));
    const missingImages = readMtl(mtl).flatMap(material => material.imageName &&
      !available.has(material.imageName.replaceAll("\\", "/").split("/").at(-1)!.toLowerCase()) ? [material.imageName] : []);
    const missingMtl = !mtl && /^\s*mtllib\s+/m.test(source);
    mutate(() => {
      const added = doc.importObj(obj.name.replace(/\.obj$/i, ""), source,
        (document.querySelector<HTMLSelectElement>("#obj-unit")!).value as ObjUnit,
        (document.querySelector<HTMLSelectElement>("#obj-up")!).value as ObjUpAxis, mtl, images);
      selected = added.id; selectedIds = new Set([added.id]);
    });
    objDialog.close(); pendingObjFiles = []; fit();
    status(`Imported ${obj.name}${missingMtl ? " · MTL file was not selected" :
      missingImages.length ? ` · missing ${missingImages.length} image file(s)` : ""} · save Turtle to keep it`);
  } catch (error) { status(`Could not import OBJ: ${String(error)}`); }
  finally { button.disabled = false; (document.querySelector<HTMLInputElement>("#obj-files")!).value = ""; }
};
$("#open").onclick = () => (document.querySelector<HTMLInputElement>("#file")!).click();
document.querySelector<HTMLInputElement>("#file")!.onchange = async event => {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return;
  try { cancelDrag(); clearPlacement(); doc = CadDocument.parse(await file.text()); selected = undefined; selectedIds.clear(); undo = []; redo = []; draw(); fit(); const elements = doc.elements; status(`Opened ${file.name} · ${elements.length} editable elements, including ${elements.filter(e => e.imported).length} IFC meshes`); }
  catch (error) { status(`Could not read Turtle: ${String(error)}`); }
  input.value = "";
};
$("#save").onclick = async () => {
  try {
    for (const wall of doc.elements.filter(e => e.kind === "Wall" && doc.hasDerivedWall(e.id))) {
      const mesh = meshes.get(wall.id);
      if (!mesh) throw new Error(`Wall mesh missing: ${wall.label}`);
      doc.setDerivedWallObj(wall.id, meshToIfcObj(mesh));
    }
    const blob = new Blob([await doc.serialize()], { type: "text/turtle" });
    const url = URL.createObjectURL(blob); const link = document.createElement("a"); link.href = url; link.download = "lbd-cad-model.ttl"; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); status("Saved Turtle");
  } catch (error) { status(`Could not save Turtle: ${String(error)}`); }
};
$("#quantities").onclick = () => {
  const rows = quantityRows();
  downloadText("lbd-cad-quantities.csv", "text/csv", exportQuantitiesCsv(rows));
  const area = rows.reduce((sum, row) => sum + row.area, 0);
  const volume = rows.reduce((sum, row) => sum + (row.volume ?? 0), 0);
  $("#quantity-summary").textContent = `${rows.length} elements · ${area.toFixed(2)} m² surface · ${volume.toFixed(2)} m³ closed mesh volume`;
  status(`Exported measured quantities for ${rows.length} elements`);
};
$("#export-obj").onclick = () => {
  const parts = exportParts(true);
  downloadText("lbd-cad-model.obj", "text/plain", exportObj(parts));
  status(`Exported ${parts.length} visible elements to OBJ`);
};
$("#export-stl").onclick = () => {
  const parts = exportParts(true);
  downloadText("lbd-cad-model.stl", "model/stl", exportStl(parts));
  status(`Exported ${parts.length} visible elements to STL`);
};
$("#export-dxf").onclick = () => {
  const parts = exportParts(true);
  downloadText("lbd-cad-plan.dxf", "application/dxf", exportDxfPlan(parts));
  status(`Exported top projection of ${parts.length} visible elements to DXF`);
};
$("#fit").onclick = fit;
$("#snap").onclick = () => {
  snapEnabled = !snapEnabled;
  $("#snap").classList.toggle("active", snapEnabled);
  $("#snap").setAttribute("aria-pressed", String(snapEnabled));
  status(`Snapping ${snapEnabled ? "on" : "off"}`);
};
$("#plan").onclick = () => setViewPreset(activeView === "top" ? "iso" : "top");
document.querySelectorAll<HTMLButtonElement>("[data-view]").forEach(button =>
  button.onclick = () => setViewPreset(button.dataset.view as ViewPreset));
$("#save-scene").onclick = () => {
  const input = document.querySelector<HTMLInputElement>("#scene-name")!;
  const label = input.value.trim() || `Scene ${doc.scenes.length + 1}`;
  mutate(() => doc.addScene(label, { alpha: camera.alpha, beta: camera.beta, radius: camera.radius,
    targetX: camera.target.x, targetY: camera.target.y, targetZ: camera.target.z,
    orthographic: camera.mode === Camera.ORTHOGRAPHIC_CAMERA, sectionEnabled, sectionHeight }));
  input.value = "";
  status(`Saved scene ${label}`);
};
document.querySelector<HTMLInputElement>("#section-enabled")!.onchange = event => {
  sectionEnabled = (event.currentTarget as HTMLInputElement).checked;
  updateSection();
  status(sectionEnabled ? `Section cut at ${sectionHeight.toFixed(2)} m` : "Section cut off");
};
document.querySelector<HTMLInputElement>("#section-height")!.oninput = event => {
  sectionHeight = Number((event.currentTarget as HTMLInputElement).value);
  updateSection();
};
document.querySelector<HTMLSelectElement>("#display-style")!.onchange = event => {
  displayStyle = (event.currentTarget as HTMLSelectElement).value as DisplayStyle;
  draw();
  status(`Display style: ${displayStyle === "xray" ? "X-ray" : displayStyle === "hidden" ? "Hidden line" : displayStyle}`);
};
document.querySelector<HTMLInputElement>("#measure")!.addEventListener("keydown", event => {
  if (event.key !== "Enter") return;
  event.preventDefault();
  const input = event.currentTarget as HTMLInputElement;
  if (tool === "rectangle" && rectangleStart) {
    const dimensions = parseRectangleDimensions(input.value);
    if (!dimensions) { status("Enter rectangle width,depth in metres, for example 4,3"); return; }
    commitRectangle({ x: rectangleStart.x + dimensions[0], z: rectangleStart.z + dimensions[1] });
  } else if (tool === "pushpull" && selected) {
    const height = parseLength(input.value);
    const element = doc.elements.find(item => item.id === selected);
    if (!height || !element || element.imported || !["Face", "Box", "Wall", "Slab"].includes(element.kind)) {
      status("Select a CAD face or solid and enter a positive height, for example 2.5 m"); return;
    }
    mutate(() => {
      doc.setGeometry(element.id, { height, y: element.y - element.height / 2 + height / 2 });
      if (element.kind === "Face") doc.setKind(element.id, "Box");
    });
    status(`Set height to ${height} m`);
  } else if (tool === "move" && selected) {
    const coordinates = parseCoordinates(input.value);
    if (!coordinates) { status("Enter absolute X,Z coordinates, for example -2,3"); return; }
    const current = doc.elements.find(element => element.id === selected)!;
    mutate(() => doc.moveGroup(selected!, { x: coordinates[0], y: current.y, z: coordinates[1] }));
    status(`Moved to X ${coordinates[0]} m, Z ${coordinates[1]} m`);
  } else if (tool === "rotate" && selected) {
    const degrees = parseDegrees(input.value);
    if (degrees === undefined) { status("Enter an angle in degrees, for example 45°"); return; }
    mutate(() => doc.setGeometry(selected!, { rotation: degrees }));
    status(`Rotated to ${degrees}°`);
  } else { status("Choose Rectangle, Push/Pull, Move, or Rotate before entering a measurement"); return; }
  input.value = "";
  input.blur();
});
$("#undo").onclick = () => restore(undo, redo);
$("#redo").onclick = () => restore(redo, undo);
window.addEventListener("keydown", event => {
  if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
  if (event.key === "Escape") { cancelDrag(); setTool("select"); return; }
  if (!event.ctrlKey && !event.metaKey && !event.altKey) {
    const axis = event.key.toLowerCase();
    if (tool === "move" && (axis === "x" || axis === "z")) {
      axisLock = axisLock === axis ? undefined : axis;
      $("#axis-lock").hidden = !axisLock;
      $("#axis-lock").textContent = axisLock ? `${axisLock.toUpperCase()} AXIS LOCKED` : "";
      status(axisLock ? `Move constrained to ${axisLock.toUpperCase()} axis` : "Axis lock cleared");
      return;
    }
    if (event.key.toLowerCase() === "m") setTool("move");
    if (event.key.toLowerCase() === "r") setTool("rotate");
    if (event.key.toLowerCase() === "s") setTool("scale");
    if (event.key.toLowerCase() === "p") setTool("paint");
    if (event.code === "Space") { event.preventDefault(); setTool("select"); }
  }
  if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "z") return;
  event.preventDefault(); restore(event.shiftKey ? redo : undo, event.shiftKey ? undo : redo);
});
draw(); fit(); setViewPreset("iso"); setTool("move");
