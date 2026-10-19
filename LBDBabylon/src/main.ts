import "./style.css";
import "./icons.css";
import { parseTurtle } from "./rdf";
import { Viewer } from "./renderer";
import { Navigation } from "./navigation";
import { $, layout, status } from "./ui";
import type { ModelData } from "./types";

layout(); let data: ModelData | undefined; let selected: string | undefined;
const canvas = $<HTMLCanvasElement>("#canvas"); const viewer = new Viewer(canvas, select); const nav = new Navigation(viewer, canvas);
function select(id: string) { selected = id; viewer.select(id); status(`Selected ${id}`); }
async function load(file: File) { status("Reading LBD Turtle…"); try { data = await parseTurtle(await file.text()); status("Rendering supported geometry…"); viewer.clear(); const result = await viewer.render(data, new Map()); const elements = new Set([...viewer.byElement.keys()]).size; $("#drop").style.display = result.meshes ? "none" : "grid"; status(result.meshes ? `Loaded ${file.name}: ${data.entities.length} entities, ${elements} elements rendered, ${result.meshes} meshes.` : "No renderable geometry found — semantic entities remain available.", !result.meshes); } catch (e) { status(`Could not parse ${file.name}: ${(e as Error).message}`, true); } }
$("#upload").onclick = () => $<HTMLInputElement>("#ttl").click(); $<HTMLInputElement>("#ttl").onchange = e => { const f = (e.target as HTMLInputElement).files?.[0]; if (f) void load(f); };
const drop = $("#viewport"); drop.ondragover = e => e.preventDefault(); drop.ondrop = e => { e.preventDefault(); const f = [...e.dataTransfer!.files].find(x => x.name.toLowerCase().endsWith(".ttl")); if (f) void load(f); };
document.querySelectorAll<HTMLButtonElement>("[data-mode]").forEach(b => b.onclick = () => { try { b.dataset.mode === "walk" ? nav.walkMode() : b.dataset.mode === "fly" ? nav.flyMode() : nav.orbit(); status(`${b.textContent} mode active`); } catch (e) { status((e as Error).message, true); } });
$("#fit").onclick = () => viewer.fit(); $("#hide").onclick = () => selected && viewer.visibility(selected, false); $("#isolate").onclick = () => { if (selected) viewer.byElement.forEach((_m, id) => viewer.visibility(id, id === selected)); }; $("#show").onclick = () => viewer.allVisible();
window.setInterval(() => nav.recover(), 500);
