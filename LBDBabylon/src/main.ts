import "./style.css";
import "./object-info.css";
import "./icons.css";
import { parseTurtle } from "./rdf";
import { Viewer } from "./renderer";
import { Navigation } from "./navigation";
import { $, hideObjectInfo, layout, showObjectInfo, status } from "./ui";
import type { GeometryAsset, GeometrySource, ModelData } from "./types";

layout(); let data: ModelData | undefined; let selected: string | undefined; const assets = new Map<string, GeometryAsset>();
const canvas = $<HTMLCanvasElement>("#canvas"); const viewer = new Viewer(canvas, select); const nav = new Navigation(viewer, canvas);
function select(id: string) { selected = id; viewer.select(id); if (data) showObjectInfo(data, id); status(`Selected ${data?.entities.find(entity => entity.id === id)?.label ?? id}`); }
function clearSelection() { selected = undefined; viewer.select(); hideObjectInfo(); }
async function render(name = "model") { if (!data) return; status("Rendering supported geometry…"); clearSelection(); nav.reset(); setModeButton("orbit"); viewer.clear(); const result = await viewer.render(data, assets); const elements = new Set([...viewer.byElement.keys()]).size; $("#drop").style.display = result.meshes ? "none" : "grid"; status(result.meshes ? `Loaded ${name}: ${data.entities.length} entities, ${elements} elements rendered, ${result.meshes} meshes, ${viewer.walkableMeshes().length} walkable surfaces${result.unsupported ? `; ${result.unsupported} unavailable` : ""}.` : "No renderable geometry found. Check the linked remote artifacts.", !result.meshes); }
async function load(file: File) { status("Reading LBD Turtle…"); try { data = await parseTurtle(await file.text()); assets.clear(); const failures = await loadLinkedRemoteGeometry(); await render(file.name); if (failures.length) status(`Loaded ${file.name}, but ${failures.length} linked remote artifact(s) failed: ${failures[0]}`, true); } catch (e) { status(`Could not load ${file.name}: ${(e as Error).message}`, true); } }
$("#upload").onclick = () => $<HTMLInputElement>("#ttl").click(); $<HTMLInputElement>("#ttl").onchange = e => { const f = (e.target as HTMLInputElement).files?.[0]; if (f) void load(f); };
async function loadLinkedRemoteGeometry() {
  if (!data) return [];
  const linked = data.geometries.filter(source => source.kind.startsWith("artifact-")
    && /^https?:\/\//i.test(source.asset ?? "") && !assets.has(source.asset!));
  const remote = [...new Map(linked.map(source => [source.asset!, source])).values()];
  if (!remote.length) return [];
  status(`Loading ${remote.length} linked remote geometry artifact(s)…`);
  const failures: string[] = [];
  for (const source of remote) try {
    const response = await fetch(source.asset!, { credentials: "omit" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    validateRemoteContentType(source, response.headers.get("content-type") ?? "");
    const bytes = await response.arrayBuffer();
    assets.set(source.asset!, { text: async () => new TextDecoder().decode(bytes), arrayBuffer: async () => bytes });
  } catch (error) { failures.push(`${source.asset}: ${(error as Error).message}`); }
  return failures;
}
function validateRemoteContentType(source: GeometrySource, contentType: string) {
  const expected = source.kind === "artifact-obj" ? ["model/obj", "text/plain"]
    : source.kind === "artifact-glb" ? ["model/gltf-binary", "application/octet-stream"]
    : ["model/gltf+json", "application/json"];
  const extension = source.kind === "artifact-glb" ? ".glb" : source.kind === "artifact-gltf" ? ".gltf" : ".obj";
  if (!expected.some(value => contentType.includes(value)) && !source.asset!.toLowerCase().endsWith(extension))
    throw new Error(`unsupported content type ${contentType || "unknown"}`);
}
const drop = $("#viewport"); drop.ondragover = e => e.preventDefault(); drop.ondrop = e => { e.preventDefault(); const f = [...e.dataTransfer!.files].find(x => x.name.toLowerCase().endsWith(".ttl")); if (f) void load(f); };
function setModeButton(mode: string) { document.querySelectorAll<HTMLButtonElement>("[data-mode]").forEach(button => { const active = button.dataset.mode === mode; button.classList.toggle("active", active); button.setAttribute("aria-pressed", String(active)); }); }
document.querySelectorAll<HTMLButtonElement>("[data-mode]").forEach(b => b.onclick = () => { try { const mode = b.dataset.mode!; mode === "walk" ? nav.walkMode() : mode === "fly" ? nav.flyMode() : nav.orbit(); setModeButton(mode); status(`${b.textContent} mode active`); } catch (e) { status((e as Error).message, true); } });
let proximityEnabled = false;
$("#proximity").onclick = () => { proximityEnabled = !proximityEnabled; const button = $<HTMLButtonElement>("#proximity"); button.classList.toggle("active", proximityEnabled); button.setAttribute("aria-pressed", String(proximityEnabled)); viewer.setProximity(proximityEnabled); status(proximityEnabled ? "Only near objects enabled at 2 m." : "Only near objects disabled."); };
$("#info-close").onclick = clearSelection;
$("#fit").onclick = () => viewer.fit(); $("#hide").onclick = () => selected && viewer.visibility(selected, false); $("#isolate").onclick = () => { if (selected) viewer.byElement.forEach((_m, id) => viewer.visibility(id, id === selected)); }; $("#show").onclick = () => viewer.allVisible();
canvas.addEventListener("dblclick", () => { const target = viewer.pickWalkable(); if (!target) { status("Choose a rendered floor, slab, stair, ramp or exterior ground point for assisted walking.", true); return; } try { nav.walkTo(target); setModeButton("walk"); status("Walking to selected floor point. Press WASD to take over."); } catch (error) { status((error as Error).message, true); } });
let clickOrigin: { x: number; y: number } | undefined;
canvas.addEventListener("pointerdown", event => { clickOrigin = { x: event.clientX, y: event.clientY }; });
canvas.addEventListener("click", event => {
  if (nav.currentMode() !== "walk") return;
  // Dragging rotates the view; only a clean click walks the avatar.
  if (clickOrigin && Math.hypot(event.clientX - clickOrigin.x, event.clientY - clickOrigin.y) > 6) return;
  const target = viewer.pickPoint();
  if (!target) return;
  nav.walkTo(target);
  status("Walking to the clicked point. Press WASD to take over.");
});
document.addEventListener("pointerlockchange", () => $("#walk-hud").classList.toggle("active", document.pointerLockElement === canvas && nav.currentMode() === "walk"));
