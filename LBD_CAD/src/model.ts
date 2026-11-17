import { DataFactory as f, Parser, Store, Writer, type Quad } from "n3";
import { decodeObj, encodeObj, parseObj, rewriteObj, rewriteWireframe, type Point } from "./ifc-geometry";
import { defaultFinish, finishes, type Finish } from "./finishes";
import { defaultTexture, defaultTile, texturePresets, type TexturePreset } from "./texture-presets";
import { prepareObj, readMtl, readObjSurface, type ObjSurface, type ObjUnit, type ObjUpAxis } from "./obj-import";

export const NS = {
  cad: "https://w3id.org/ifctolbd/cad#",
  bot: "https://w3id.org/bot#",
  rdf: "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
  rdfs: "http://www.w3.org/2000/01/rdf-schema#",
  xsd: "http://www.w3.org/2001/XMLSchema#"
};
export type Kind = "Wall" | "Slab" | "Space" | "Door" | "Window" | "Opening" | "Face" | "Box" | "Mesh";
export type Field = "x" | "y" | "z" | "width" | "height" | "depth" | "rotation";
export const fields: Field[] = ["x", "y", "z", "width", "height", "depth"];
export const boxFaces = ["px", "nx", "py", "ny", "pz", "nz"] as const;
export type BoxFaceKey = typeof boxFaces[number];
export interface MaterialDefinition { id: string; label: string; finish: Finish; color: string;
  tileWidth: number; tileHeight: number; grainDegrees: number; opacity: number; texture: TexturePreset; imageData?: string }
export interface Element { id: string; kind: Kind; label: string; x: number; y: number; z: number; width: number; height: number; depth: number; rotation: number; color?: string; finish?: Finish; materialId?: string; layer?: string; group?: string; hidden?: boolean; imported?: boolean }
export interface SceneBookmark { id: string; label: string; alpha: number; beta: number; radius: number; targetX: number; targetY: number; targetZ: number; orthographic: boolean; sectionEnabled: boolean; sectionHeight: number }
const nn = f.namedNode;
const OMG_GEOMETRY = nn("https://w3id.org/omg#hasGeometry");
const FOG_OBJ = nn("https://w3id.org/fog#asObj_v3.0-obj");
const CAD_BASE_OBJ = nn(NS.cad + "baseObj");
const LBD = "https://linkedbuildingdata.org/LBD#";
const kinds: Kind[] = ["Wall", "Slab", "Space", "Door", "Window", "Opening", "Face", "Box", "Mesh"];
const defaults: Record<Kind, [number, number, number]> = {
  Wall: [4, 3, .2], Slab: [4, .25, 4], Space: [4, 3, 4],
  Door: [.9, 2.1, .1], Window: [1.2, 1.2, .1], Opening: [1, 2.1, .2], Face: [4, .001, 4], Box: [1, 1, 1], Mesh: [1, 1, 1]
};
function validImageData(value: string): boolean {
  return value.length <= 11_200_000 && /^data:image\/(?:png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/.test(value);
}

function linkedGeometryChange(parent: Pick<Element, Field>, target: Pick<Element, Field>, child: Pick<Element, Field>): Pick<Element, Field> {
  const delta = (target.rotation - parent.rotation) * Math.PI / 180;
  const cosine = Math.cos(delta), sine = Math.sin(delta);
  const localX = (child.x - parent.x) * target.width / parent.width;
  const localZ = (child.z - parent.z) * target.depth / parent.depth;
  return { x: target.x + localX * cosine + localZ * sine,
    y: target.y + (child.y - parent.y) * target.height / parent.height,
    z: target.z - localX * sine + localZ * cosine,
    width: child.width * target.width / parent.width,
    height: child.height * target.height / parent.height,
    depth: child.depth * target.depth / parent.depth,
    rotation: child.rotation + target.rotation - parent.rotation };
}

/** The RDF store is the document. Meshes are disposable projections of these triples. */
export class CadDocument {
  readonly store: Store;
  constructor(quads: Quad[] = []) { this.store = new Store(quads); }
  static parse(turtle: string): CadDocument { return new CadDocument(new Parser().parse(turtle)); }
  get materials(): MaterialDefinition[] {
    return this.store.getQuads(null, nn(NS.rdf + "type"), nn(NS.cad + "Material"), null)
      .filter(q => q.subject.termType === "NamedNode").map(q => {
        const id = q.subject.value;
        const finishValue = this.value(id, NS.cad + "finish");
        const finish = finishValue && Object.hasOwn(finishes, finishValue) ? finishValue as Finish : "paint";
        const positive = (name: string, fallback: number) => {
          const value = Number(this.value(id, NS.cad + name));
          return Number.isFinite(value) && value > 0 ? value : fallback;
        };
        const angle = Number(this.value(id, NS.cad + "grainDegrees"));
        const opacityValue = this.value(id, NS.cad + "opacity");
        const opacity = opacityValue === undefined ? finishes[finish].alpha : Number(opacityValue);
        const color = this.value(id, NS.cad + "color");
        const textureValue = this.value(id, NS.cad + "texture");
        const imageData = this.value(id, NS.cad + "imageData");
        return { id, label: this.value(id, NS.rdfs + "label") ?? "Material", finish,
          color: color && /^#[0-9a-fA-F]{6}$/.test(color) ? color : finishes[finish].color,
          tileWidth: positive("tileWidth", defaultTile(finish)[0]), tileHeight: positive("tileHeight", defaultTile(finish)[1]),
          grainDegrees: Number.isFinite(angle) ? angle : 0,
          opacity: Number.isFinite(opacity) && opacity >= 0 && opacity <= 1 ? opacity : finishes[finish].alpha,
          texture: texturePresets.includes(textureValue as TexturePreset) ? textureValue as TexturePreset : defaultTexture(finish),
          imageData: imageData && validImageData(imageData) ? imageData : undefined };
      });
  }
  material(id: string): MaterialDefinition | undefined { return this.materials.find(item => item.id === id); }
  addMaterial(label: string, finish: Finish, color: string = finishes[finish].color): MaterialDefinition {
    if (!label.trim() || label.length > 80) throw new Error("Material needs a name of at most 80 characters");
    if (!Object.hasOwn(finishes, finish)) throw new Error(`Unknown finish: ${finish}`);
    if (!/^#[0-9a-fA-F]{6}$/.test(color)) throw new Error("Material color must be a six digit hex value");
    const id = `urn:lbd-cad:material:${crypto.randomUUID()}`;
    this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.cad + "Material"));
    const [tileWidth, tileHeight] = defaultTile(finish);
    this.updateMaterial(id, { label: label.trim(), finish, color, tileWidth, tileHeight,
      grainDegrees: 0, opacity: finishes[finish].alpha, texture: defaultTexture(finish) });
    return this.material(id)!;
  }
  addImageMaterial(label: string, imageData: string): MaterialDefinition {
    if (!validImageData(imageData)) throw new Error("Texture image must be a PNG or JPEG data URI under 8 MB");
    const material = this.addMaterial(label, "paint", "#ffffff");
    this.updateMaterial(material.id, { texture: "image", imageData });
    return this.material(material.id)!;
  }
  updateMaterial(id: string, changes: Partial<Omit<MaterialDefinition, "id">>): void {
    if (!this.store.has(nn(id), nn(NS.rdf + "type"), nn(NS.cad + "Material"), null))
      throw new Error(`Unknown material: ${id}`);
    if (changes.label !== undefined && (!changes.label.trim() || changes.label.length > 80))
      throw new Error("Material needs a name of at most 80 characters");
    if (changes.finish !== undefined && !Object.hasOwn(finishes, changes.finish))
      throw new Error(`Unknown finish: ${changes.finish}`);
    if (changes.color !== undefined && !/^#[0-9a-fA-F]{6}$/.test(changes.color))
      throw new Error("Material color must be a six digit hex value");
    if (changes.opacity !== undefined && (!Number.isFinite(changes.opacity) || changes.opacity < 0 || changes.opacity > 1))
      throw new Error("Material opacity must be between 0 and 1");
    if (changes.texture !== undefined && !texturePresets.includes(changes.texture))
      throw new Error(`Unknown texture: ${changes.texture}`);
    if (changes.imageData !== undefined && !validImageData(changes.imageData))
      throw new Error("Texture image must be a PNG or JPEG data URI under 8 MB");
    if (changes.texture === "image" && !changes.imageData && !this.value(id, NS.cad + "imageData"))
      throw new Error("Import an image before selecting the image texture");
    for (const field of ["tileWidth", "tileHeight", "grainDegrees"] as const) {
      const value = changes[field];
      if (value !== undefined && (!Number.isFinite(value) || (field !== "grainDegrees" && value <= 0)))
        throw new Error(`${field} must be ${field === "grainDegrees" ? "finite" : "positive"}`);
    }
    if (changes.label !== undefined) {
      this.setLabel(id, changes.label.trim());
    }
    if (changes.finish !== undefined) {
      this.replace(id, NS.cad + "finish", f.literal(changes.finish));
    }
    if (changes.color !== undefined) {
      this.replace(id, NS.cad + "color", f.literal(changes.color.toLowerCase()));
    }
    if (changes.opacity !== undefined || changes.finish !== undefined) {
      const opacity = changes.opacity ?? finishes[changes.finish!].alpha;
      this.replace(id, NS.cad + "opacity", f.literal(String(opacity), nn(NS.xsd + "decimal")));
    }
    if (changes.texture !== undefined || changes.finish !== undefined) {
      const texture = changes.texture ?? defaultTexture(changes.finish!);
      this.replace(id, NS.cad + "texture", f.literal(texture));
    }
    if (changes.imageData !== undefined)
      this.replace(id, NS.cad + "imageData", f.literal(changes.imageData));
    for (const field of ["tileWidth", "tileHeight", "grainDegrees"] as const) {
      const value = changes[field];
      if (value === undefined) continue;
      this.replace(id, NS.cad + field, f.literal(String(value), nn(NS.xsd + "decimal")));
    }
  }
  private ensurePresetMaterial(finish: Finish): MaterialDefinition {
    return this.materials.find(item => item.finish === finish && item.label === finishes[finish].label)
      ?? this.addMaterial(finishes[finish].label, finish);
  }
  assignMaterial(elementId: string, materialId?: string): void {
    const element = this.elements.find(item => item.id === elementId);
    if (!element) throw new Error(`Unknown element: ${elementId}`);
    if (materialId && !this.material(materialId)) throw new Error(`Unknown material: ${materialId}`);
    this.store.removeQuads(this.store.getQuads(nn(elementId), nn(NS.cad + "material"), null, null));
    if (materialId) this.store.addQuad(nn(elementId), nn(NS.cad + "material"), nn(materialId));
    if (materialId && element.kind === "Mesh")
      for (const binding of this.store.getQuads(nn(elementId), nn(NS.cad + "hasObjMaterial"), null, null)) {
        if (binding.object.termType !== "NamedNode") continue;
        this.store.removeQuads(this.store.getQuads(binding.object, nn(NS.cad + "material"), null, null));
        this.store.addQuad(binding.object, nn(NS.cad + "material"), nn(materialId));
      }
    this.store.removeQuads(this.store.getQuads(nn(elementId), nn(NS.cad + "finish"), null, null));
    if (materialId) this.store.removeQuads(this.store.getQuads(nn(elementId), nn(NS.cad + "color"), null, null));
  }
  faceId(elementId: string, face: BoxFaceKey): string { return `${elementId}:face:${face}`; }
  faceMaterial(elementId: string, face: BoxFaceKey): MaterialDefinition | undefined {
    const materialId = this.value(this.faceId(elementId, face), NS.cad + "material");
    return materialId ? this.material(materialId) : undefined;
  }
  setFaceMaterial(elementId: string, face: BoxFaceKey, materialId: string): void {
    const element = this.elements.find(item => item.id === elementId);
    if (!element || element.imported || element.kind === "Face" ||
      (element.kind === "Wall" && this.hostedOpenings(elementId).length))
      throw new Error("Face paint needs a CAD solid without openings");
    if (!boxFaces.includes(face) || !this.material(materialId)) throw new Error("Unknown face or material");
    const faceId = this.faceId(elementId, face);
    this.store.addQuad(nn(elementId), nn(NS.cad + "hasFace"), nn(faceId));
    this.store.addQuad(nn(faceId), nn(NS.rdf + "type"), nn(NS.cad + "BoxFace"));
    this.replace(faceId, NS.cad + "faceKey", f.literal(face));
    this.store.removeQuads(this.store.getQuads(nn(faceId), nn(NS.cad + "material"), null, null));
    this.store.addQuad(nn(faceId), nn(NS.cad + "material"), nn(materialId));
  }
  get scenes(): SceneBookmark[] {
    return this.store.getQuads(null, nn(NS.rdf + "type"), nn(NS.cad + "Scene"), null)
      .filter(quad => quad.subject.termType === "NamedNode").map(quad => {
        const id = quad.subject.value;
        const number = (field: string, fallback: number) => {
          const value = Number(this.value(id, NS.cad + field));
          return Number.isFinite(value) ? value : fallback;
        };
        return { id, label: this.value(id, NS.rdfs + "label") ?? "Scene", alpha: number("alpha", 0),
          beta: number("beta", 1), radius: number("radius", 10), targetX: number("targetX", 0),
          targetY: number("targetY", 0), targetZ: number("targetZ", 0),
          orthographic: this.value(id, NS.cad + "orthographic") === "true",
          sectionEnabled: this.value(id, NS.cad + "sectionEnabled") === "true", sectionHeight: number("sectionHeight", 1.5) };
      });
  }
  addScene(label: string, view: Omit<SceneBookmark, "id" | "label">): SceneBookmark {
    if (!label.trim()) throw new Error("Scene needs a name");
    for (const [key, value] of Object.entries(view)) if (typeof value === "number" && !Number.isFinite(value))
      throw new Error(`Invalid scene ${key}`);
    if (view.radius <= 0) throw new Error("Scene radius must be positive");
    const id = `urn:lbd-cad:scene:${crypto.randomUUID()}`;
    this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.cad + "Scene"));
    this.setLabel(id, label.trim());
    for (const [key, value] of Object.entries(view)) this.replace(id, NS.cad + key,
      f.literal(String(value), nn(NS.xsd + (typeof value === "boolean" ? "boolean" : "decimal"))));
    return this.scenes.find(scene => scene.id === id)!;
  }
  removeScene(id: string): void {
    if (!this.scenes.some(scene => scene.id === id)) throw new Error(`Unknown scene: ${id}`);
    this.store.removeQuads(this.store.getQuads(nn(id), null, null, null));
  }
  get elements(): Element[] {
    const seen = new Set<string>();
    const result: Element[] = [];
    for (const q of this.store.getQuads(null, nn(NS.rdf + "type"), null, null)) {
      if (q.subject.termType !== "NamedNode" || q.object.termType !== "NamedNode") continue;
      const kind = q.object.value.startsWith(NS.cad) ? q.object.value.slice(NS.cad.length) as Kind : undefined;
      if (!kind || !kinds.includes(kind) || seen.has(q.subject.value)) continue;
      seen.add(q.subject.value);
      const id = q.subject.value;
      const number = (field: Field, fallback: number) => {
        const literal = this.store.getQuads(nn(id), nn(NS.cad + field), null, null)[0]?.object;
        if (!literal || literal.termType !== "Literal") return fallback;
        const value = Number(literal.value);
        return Number.isFinite(value) ? value : fallback;
      };
      const [width, height, depth] = defaults[kind];
      const meshBounds = kind === "Mesh" ? this.meshBounds(id) : undefined;
      result.push({ id, kind, label: this.value(id, NS.rdfs + "label") ?? id.split(/[\/#]/).pop() ?? id,
        x: meshBounds?.x ?? number("x", 0), y: meshBounds?.y ?? number("y", 0), z: meshBounds?.z ?? number("z", 0),
        width: meshBounds?.width ?? number("width", width), height: meshBounds?.height ?? number("height", height),
        depth: meshBounds?.depth ?? number("depth", depth), rotation: number("rotation", 0),
        color: this.value(id, NS.cad + "color"), finish: this.finish(id), materialId: this.value(id, NS.cad + "material"),
        layer: this.value(id, NS.cad + "layer"), group: this.value(id, NS.cad + "group"), hidden: this.value(id, NS.cad + "hidden") === "true",
        imported: kind === "Mesh" });
    }
    // IFCtoLBD identifies concrete IFC classes through owl:sameAs. The linked
    // bounding box locates each embedded OBJ mesh in CAD coordinates.
    const sameAs = nn("http://www.w3.org/2002/07/owl#sameAs");
    for (const q of this.store.getQuads(null, sameAs, null, null)) {
      if (q.subject.termType !== "NamedNode" || seen.has(q.subject.value)) continue;
      const ifcClass = q.object.value.split(/[\/#]/).pop()?.replace(/_\d+$/, "");
      const kind: Kind | undefined = ifcClass === "IfcSpace" ? "Space" : ifcClass === "IfcSlab" ? "Slab" :
        ifcClass === "IfcWall" || ifcClass === "IfcWallStandardCase" ? "Wall" :
        ifcClass === "IfcDoor" ? "Door" : ifcClass === "IfcWindow" ? "Window" :
        ifcClass === "IfcOpeningElement" ? "Opening" : undefined;
      if (!kind) continue;
      const geometry = this.store.getQuads(q.subject, OMG_GEOMETRY, null, null)[0]?.object;
      if (!geometry || geometry.termType !== "NamedNode") continue;
      const encoded = this.store.getQuads(geometry, FOG_OBJ, null, null)[0]?.object;
      if (encoded?.termType !== "Literal") continue;
      const bounds = this.store.getQuads(geometry, nn(LBD + "hasBoundingBox"), null, null)[0]?.object;
      if (!bounds || bounds.termType !== "NamedNode") continue;
      const coordinate = (axis: string, end: string) => {
        const term = this.store.getQuads(bounds, nn(LBD + `${axis}-${end}`), null, null)[0]?.object;
        return term?.termType === "Literal" ? Number(term.value) : NaN;
      };
      const [xMin, xMax, yMin, yMax, zMin, zMax] = [coordinate("x", "min"), coordinate("x", "max"), coordinate("y", "min"), coordinate("y", "max"), coordinate("z", "min"), coordinate("z", "max")];
      if (![xMin, xMax, yMin, yMax, zMin, zMax].every(Number.isFinite) || xMax < xMin || yMax < yMin || zMax < zMin) continue;
      const id = q.subject.value;
      seen.add(id);
      const name = id.split(/[\/#]/).pop() ?? id;
      let readableName = name;
      try { readableName = decodeURIComponent(name); } catch { /* A valid IRI may contain a literal percent sign. */ }
      result.push({ id, kind, label: this.value(id, NS.rdfs + "label") ?? readableName,
        x: (xMin + xMax) / 2, y: (zMin + zMax) / 2, z: -(yMin + yMax) / 2,
        width: Math.max(.001, xMax - xMin), height: Math.max(.001, zMax - zMin), depth: Math.max(.001, yMax - yMin),
        rotation: Number(this.value(id, NS.cad + "rotation") ?? 0), color: this.value(id, NS.cad + "color"),
        finish: this.finish(id), materialId: this.value(id, NS.cad + "material"), layer: this.value(id, NS.cad + "layer"), group: this.value(id, NS.cad + "group"),
        hidden: this.value(id, NS.cad + "hidden") === "true", imported: true });
    }
    return result;
  }
  value(id: string, predicate: string): string | undefined { return this.store.getQuads(nn(id), nn(predicate), null, null)[0]?.object.value; }
  private meshBounds(id: string): Pick<Element, "x" | "y" | "z" | "width" | "height" | "depth"> | undefined {
    const source = this.importedSource(id);
    if (!source) return;
    const coordinate = (axis: string, end: string) => Number(this.store.getQuads(source.bounds,
      nn(LBD + `${axis}-${end}`), null, null)[0]?.object.value);
    const [x0, x1, y0, y1, z0, z1] = [coordinate("x", "min"), coordinate("x", "max"),
      coordinate("y", "min"), coordinate("y", "max"), coordinate("z", "min"), coordinate("z", "max")];
    if (![x0, x1, y0, y1, z0, z1].every(Number.isFinite)) return;
    return { x: (x0 + x1) / 2, y: (z0 + z1) / 2, z: -(y0 + y1) / 2,
      width: Math.max(.001, x1 - x0), height: Math.max(.001, z1 - z0), depth: Math.max(.001, y1 - y0) };
  }
  private finish(id: string): Finish | undefined {
    const materialId = this.value(id, NS.cad + "material");
    const material = materialId ? this.material(materialId) : undefined;
    if (material) return material.finish;
    const value = this.value(id, NS.cad + "finish");
    return value && Object.hasOwn(finishes, value) ? value as Finish : undefined;
  }
  hostWall(id: string): string | undefined {
    const parent = this.store.getQuads(null, nn(NS.bot + "hasSubElement"), nn(id), null)[0]?.subject;
    if (parent?.termType === "NamedNode") return parent.value;
    return id.endsWith("%3A1") ? this.hostWall(id.slice(0, -4)) : undefined;
  }
  relatedOpening(id: string): string | undefined {
    const linked = this.value(id, NS.cad + "opening");
    if (linked) return linked;
    const candidate = nn(id + "%3A1");
    const sameAs = this.store.getQuads(candidate, nn("http://www.w3.org/2002/07/owl#sameAs"), null, null)[0]?.object.value;
    return sameAs && /IfcOpeningElement_\d+$/.test(sameAs) ? candidate.value : undefined;
  }
  hostedOpenings(wallId: string, elements = this.elements): Element[] {
    const byId = new Map(elements.map(element => [element.id, element]));
    return this.store.getQuads(nn(wallId), nn(NS.bot + "hasSubElement"), null, null)
      .map(q => byId.get(q.object.value)).filter((element): element is Element => element?.kind === "Opening");
  }
  originalOpening(id: string): Pick<Element, Field> | undefined {
    const entries = fields.concat("rotation").map(field =>
      [field, this.value(id, NS.cad + "original" + field[0].toUpperCase() + field.slice(1))] as const);
    if (entries.some(([, value]) => value === undefined || !Number.isFinite(Number(value)))) return;
    return Object.fromEntries(entries.map(([field, value]) => [field, Number(value)])) as Pick<Element, Field>;
  }
  private setOriginalOpening(id: string, geometry: Pick<Element, Field>): void {
    for (const field of fields.concat("rotation"))
      this.replace(id, NS.cad + "original" + field[0].toUpperCase() + field.slice(1),
        f.literal(String(geometry[field]), nn(NS.xsd + "decimal")));
  }
  objMaterials(id: string): Map<string, MaterialDefinition> {
    const result = new Map<string, MaterialDefinition>();
    for (const link of this.store.getQuads(nn(id), nn(NS.cad + "hasObjMaterial"), null, null)) {
      const name = this.value(link.object.value, NS.cad + "objName");
      const materialId = this.value(link.object.value, NS.cad + "material");
      const material = materialId ? this.material(materialId) : undefined;
      if (name && material) result.set(name, material);
    }
    return result;
  }
  importedMesh(id: string, element = this.elements.find(item => item.id === id)): ObjSurface | undefined {
    if (!element?.imported) return;
    const source = this.importedSource(id);
    if (!source) return;
    const base = this.store.getQuads(source.geometry, CAD_BASE_OBJ, null, null)[0]?.object;
    const text = decodeObj(base?.termType === "Literal" ? base.value : source.encoded.value);
    if (element.kind === "Mesh") {
      const parsed = readObjSurface(text);
      const positions: number[] = [];
      for (let i = 0; i < parsed.positions.length; i += 3)
        positions.push(parsed.positions[i] - element.x, parsed.positions[i + 2] - element.y,
          -parsed.positions[i + 1] - element.z);
      return { ...parsed, positions };
    }
    const parsed = parseObj(text);
    const positions = parsed.vertices.flatMap(([x, y, z]) => [x - element.x, z - element.y, -y - element.z]);
    return { positions, indices: parsed.indices, uvs: [], runs: [] };
  }
  setGeometry(id: string, changes: Partial<Pick<Element, Field>>, fromWall = false): void {
    const current = this.elements.find(item => item.id === id);
    if (!current) throw new Error(`Unknown element: ${id}`);
    const target = { ...current, ...changes };
    for (const field of fields.concat("rotation")) {
      if (!Number.isFinite(target[field])) throw new Error(`${field} must be a number`);
      if (["width", "height", "depth"].includes(field) && target[field] <= 0) throw new Error(`${field} must be a positive number`);
    }
    const openingId = current.kind === "Door" || current.kind === "Window" ? this.relatedOpening(id) : undefined;
    const opening = openingId ? this.elements.find(item => item.id === openingId) : undefined;
    const hosted = current.kind === "Wall" ? this.store.getQuads(nn(id), nn(NS.bot + "hasSubElement"), null, null)
      .map(q => this.elements.find(item => item.id === q.object.value))
      .filter((item): item is Element => item?.kind === "Door" || item?.kind === "Window") : [];
    const previousOrigins = current.kind === "Wall" ? hosted.flatMap(child => {
      const openingId = this.relatedOpening(child.id);
      const origin = openingId ? this.originalOpening(openingId) : undefined;
      return openingId && origin ? [{ openingId, origin }] : [];
    }) : [];
    const linked = [...(opening ? [opening] : []), ...hosted]
      .map(child => ({ child, changes: linkedGeometryChange(current, target, child) }));
    if (current.kind === "Opening" && current.imported && !fromWall && this.hostWall(id) && !this.originalOpening(id)) {
      this.setOriginalOpening(id, current);
      const wall = this.hostWall(id)!;
      const source = this.importedSource(wall);
      if (source && !this.store.getQuads(source.geometry, CAD_BASE_OBJ, null, null).length)
        this.store.addQuad(source.geometry, CAD_BASE_OBJ, source.encoded);
    }
    const finishLinked = () => {
      for (const { child, changes: childChanges } of linked) this.setGeometry(child.id, childChanges, fromWall || current.kind === "Wall");
      for (const { openingId, origin } of previousOrigins)
        this.setOriginalOpening(openingId, linkedGeometryChange(current, target, origin));
    };
    if (!current.imported) {
      for (const field of fields.concat("rotation")) if (target[field] !== current[field])
        this.replace(id, NS.cad + field, f.literal(String(target[field]), nn(NS.xsd + "decimal")));
      finishLinked();
      return;
    }
    const source = this.importedSource(id);
    if (!source) throw new Error("Imported element has no embedded OBJ and bounding box");
    const angle = (target.rotation - current.rotation) * Math.PI / 180;
    const cosine = Math.cos(angle), sine = Math.sin(angle);
    const transform = ([x, y, z]: Point): Point => {
      const localX = (x - current.x) * target.width / current.width;
      const localY = (z - current.y) * target.height / current.height;
      const localZ = (-y - current.z) * target.depth / current.depth;
      const nextX = target.x + localX * cosine + localZ * sine;
      const nextZ = target.z - localX * sine + localZ * cosine;
      return [nextX, -nextZ, target.y + localY];
    };
    const rewritten = rewriteObj(decodeObj(source.encoded.value), transform);
    parseObj(rewritten.text);
    const base = this.store.getQuads(source.geometry, CAD_BASE_OBJ, null, null)[0]?.object;
    const rewrittenBase = base?.termType === "Literal" ? rewriteObj(decodeObj(base.value), transform) : undefined;
    const wireframe = this.store.getQuads(source.geometry, nn(LBD + "hasWireframe"), null, null)[0]?.object;
    const newWireframe = wireframe?.termType === "Literal" ? rewriteWireframe(wireframe.value, transform) : undefined;
    const limits: Point[] = [[Infinity, Infinity, Infinity], [-Infinity, -Infinity, -Infinity]];
    for (const point of rewritten.vertices) for (let axis = 0; axis < 3; axis++) {
      limits[0][axis] = Math.min(limits[0][axis], point[axis]);
      limits[1][axis] = Math.max(limits[1][axis], point[axis]);
    }
    this.replaceTerm(source.geometry, FOG_OBJ, f.literal(encodeObj(rewritten.text), source.encoded.datatype));
    if (base?.termType === "Literal" && rewrittenBase)
      this.replaceTerm(source.geometry, CAD_BASE_OBJ, f.literal(encodeObj(rewrittenBase.text), base.datatype));
    if (wireframe?.termType === "Literal" && newWireframe)
      this.replaceTerm(source.geometry, nn(LBD + "hasWireframe"), f.literal(newWireframe, wireframe.datatype));
    for (const [index, axis] of ["x", "y", "z"].entries()) {
      this.replaceTerm(source.bounds, nn(LBD + `${axis}-min`), f.literal(String(limits[0][index]), nn(NS.xsd + "double")));
      this.replaceTerm(source.bounds, nn(LBD + `${axis}-max`), f.literal(String(limits[1][index]), nn(NS.xsd + "double")));
    }
    this.replace(id, NS.cad + "rotation", f.literal(String(target.rotation), nn(NS.xsd + "decimal")));
    finishLinked();
  }
  moveGroup(id: string, target: { x: number; y: number; z: number }): void {
    const current = this.elements.find(element => element.id === id);
    if (!current) throw new Error(`Unknown element: ${id}`);
    const offset = { x: target.x - current.x, y: target.y - current.y, z: target.z - current.z };
    if (!current.group) { this.setGeometry(id, target); return; }
    const members = this.elements.filter(element => element.group === current.group);
    const ids = new Set(members.map(element => element.id));
    for (const member of members) {
      const host = this.hostWall(member.id);
      if (host && ids.has(host)) continue;
      this.setGeometry(member.id, {
        x: member.x + offset.x, y: member.y + offset.y, z: member.z + offset.z
      });
    }
  }
  private importedSource(id: string) {
    const geometry = this.store.getQuads(nn(id), OMG_GEOMETRY, null, null)[0]?.object;
    if (!geometry || geometry.termType !== "NamedNode") return;
    const encoded = this.store.getQuads(geometry, FOG_OBJ, null, null)[0]?.object;
    const bounds = this.store.getQuads(geometry, nn(LBD + "hasBoundingBox"), null, null)[0]?.object;
    if (encoded?.termType !== "Literal" || bounds?.termType !== "NamedNode") return;
    return { geometry, encoded, bounds };
  }
  importObj(label: string, sourceText: string, unit: ObjUnit, up: ObjUpAxis,
    mtlText = "", images: Record<string, string> = {}): Element {
    if (!label.trim() || label.length > 80) throw new Error("OBJ needs a name of at most 80 characters");
    if (sourceText.length > 50_000_000) throw new Error("OBJ is too large for an embedded Turtle project");
    const obj = prepareObj(sourceText, unit, up);
    const geometry = parseObj(obj);
    const surface = readObjSurface(obj);
    const library = new Map(readMtl(mtlText).map(item => [item.name, item]));
    const usedNames = [...new Set(surface.runs.map(run => run.name?.trim() || "Default OBJ"))];
    const nameOf = (path: string) => path.replaceAll("\\", "/").split("/").at(-1)!.toLowerCase();
    const assets = new Map(Object.entries(images).map(([name, data]) => [nameOf(name), data]));
    const definitions = usedNames.map(name => {
      const mtl = library.get(name);
      const imageData = mtl?.imageName ? assets.get(nameOf(mtl.imageName)) : undefined;
      if (imageData && !validImageData(imageData)) throw new Error(`Invalid texture for ${name}`);
      return { name, color: mtl?.color ?? "#ffffff", opacity: mtl?.opacity ?? 1, imageData };
    });
    const minimum: Point = [Infinity, Infinity, Infinity], maximum: Point = [-Infinity, -Infinity, -Infinity];
    for (const vertex of geometry.vertices) for (let axis = 0; axis < 3; axis++) {
      minimum[axis] = Math.min(minimum[axis], vertex[axis]);
      maximum[axis] = Math.max(maximum[axis], vertex[axis]);
    }
    const id = `urn:lbd-cad:element:${crypto.randomUUID()}`;
    const geometryId = `${id}:geometry`, boundsId = `${id}:bounds`;
    this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.cad + "Mesh"));
    this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.bot + "Element"));
    this.setLabel(id, label.trim());
    this.store.addQuad(nn(id), OMG_GEOMETRY, nn(geometryId));
    this.store.addQuad(nn(geometryId), FOG_OBJ, f.literal(encodeObj(obj), nn(NS.xsd + "base64Binary")));
    this.store.addQuad(nn(geometryId), nn(LBD + "hasBoundingBox"), nn(boundsId));
    for (const [axis, index] of [["x", 0], ["y", 1], ["z", 2]] as const) {
      this.store.addQuad(nn(boundsId), nn(LBD + `${axis}-min`), f.literal(String(minimum[index]), nn(NS.xsd + "double")));
      this.store.addQuad(nn(boundsId), nn(LBD + `${axis}-max`), f.literal(String(maximum[index]), nn(NS.xsd + "double")));
    }
    this.replace(id, NS.cad + "rotation", f.literal("0", nn(NS.xsd + "decimal")));
    this.store.addQuad(nn(id), nn(NS.cad + "sourceUnit"), f.literal(unit));
    this.store.addQuad(nn(id), nn(NS.cad + "sourceUpAxis"), f.literal(up));
    for (const definition of definitions) {
      const material = this.addMaterial(definition.name.slice(0, 80), "paint", definition.color);
      this.updateMaterial(material.id, { opacity: definition.opacity,
        texture: definition.imageData ? "image" : "none", imageData: definition.imageData,
        tileWidth: 1, tileHeight: 1 });
      const binding = `${id}:material:${crypto.randomUUID()}`;
      this.store.addQuad(nn(id), nn(NS.cad + "hasObjMaterial"), nn(binding));
      this.store.addQuad(nn(binding), nn(NS.rdf + "type"), nn(NS.cad + "ObjMaterialBinding"));
      this.store.addQuad(nn(binding), nn(NS.cad + "objName"), f.literal(definition.name));
      this.store.addQuad(nn(binding), nn(NS.cad + "material"), nn(material.id));
      if (definition.name === definitions[0].name) this.assignMaterial(id, material.id);
    }
    return this.elements.find(element => element.id === id)!;
  }
  add(kind: Kind, position = { x: 0, y: 0, z: 0 }): Element {
    if (kind === "Mesh") throw new Error("Import an OBJ to create a mesh");
    const id = `urn:lbd-cad:element:${crypto.randomUUID()}`;
    this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.cad + kind));
    if (kind !== "Face") this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.bot + (kind === "Space" ? "Space" : "Element")));
    this.setLabel(id, `${kind} ${this.elements.length}`);
    const [width, height, depth] = defaults[kind];
    for (const [field, value] of Object.entries({ ...position, width, height, depth })) this.setNumber(id, field as Field, value);
    this.setNumber(id, "rotation", 0);
    this.assignMaterial(id, this.ensurePresetMaterial(defaultFinish(kind)).id);
    return this.elements.find(e => e.id === id)!;
  }
  setKind(id: string, kind: Kind): void {
    if (kind === "Mesh") throw new Error("Import an OBJ to create a mesh");
    const current = this.elements.find(element => element.id === id);
    if (!current || current.imported) throw new Error("Only CAD primitives can change kind");
    this.store.removeQuads(this.store.getQuads(nn(id), nn(NS.rdf + "type"), null, null)
      .filter(q => q.object.value.startsWith(NS.cad) || q.object.value === NS.bot + "Element" || q.object.value === NS.bot + "Space"));
    this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.cad + kind));
    if (kind !== "Face") this.store.addQuad(nn(id), nn(NS.rdf + "type"), nn(NS.bot + (kind === "Space" ? "Space" : "Element")));
  }
  addHosted(kind: "Door" | "Window", wallId: string, point?: { x: number; y: number; z: number }): Element {
    const wall = this.elements.find(element => element.id === wallId && element.kind === "Wall");
    if (!wall) throw new Error("Select a wall before adding a door or window");
    if (wall.imported) {
      const source = this.importedSource(wallId);
      if (source && !this.store.getQuads(source.geometry, CAD_BASE_OBJ, null, null).length)
        this.store.addQuad(source.geometry, CAD_BASE_OBJ, source.encoded);
    }
    const [defaultWidth, defaultHeight, defaultDepth] = defaults[kind];
    const alongZ = wall.imported && wall.depth > wall.width;
    const run = alongZ ? wall.depth : wall.width;
    const thickness = alongZ ? wall.width : wall.depth;
    const width = Math.min(defaultWidth, run * .7);
    const height = Math.min(defaultHeight, wall.height * .8);
    const angle = wall.rotation * Math.PI / 180;
    const dx = (point?.x ?? wall.x) - wall.x, dz = (point?.z ?? wall.z) - wall.z;
    const localX = Math.max(-(wall.width - width) / 2, Math.min((wall.width - width) / 2,
      dx * Math.cos(angle) - dz * Math.sin(angle)));
    const clamp = (value: number, center: number) => center + Math.max(-(run - width) / 2, Math.min((run - width) / 2, value - center));
    const center = { x: wall.imported ? alongZ ? wall.x : clamp(point?.x ?? wall.x, wall.x) : wall.x + localX * Math.cos(angle),
      y: kind === "Door" ? wall.y - wall.height / 2 + height / 2 :
        Math.max(wall.y - (wall.height - height) / 2, Math.min(wall.y + (wall.height - height) / 2, point?.y ?? wall.y)),
      z: wall.imported ? alongZ ? clamp(point?.z ?? wall.z, wall.z) : wall.z : wall.z - localX * Math.sin(angle) };
    const opening = this.add("Opening", center);
    this.setNumber(opening.id, "width", width + .04);
    this.setNumber(opening.id, "height", height + .04);
    this.setNumber(opening.id, "depth", thickness + .1);
    this.setNumber(opening.id, "rotation", wall.imported && alongZ ? 90 : wall.rotation);
    const element = this.add(kind, center);
    if (kind === "Door") {
      let doorPaint = this.materials.find(material => material.label === "Door paint" &&
        material.finish === "paint" && material.color === "#f1ece4");
      if (!doorPaint) {
        doorPaint = this.addMaterial("Door paint", "paint", "#f1ece4");
        this.updateMaterial(doorPaint.id, { texture: "none" });
      }
      this.assignMaterial(element.id, doorPaint.id);
    }
    this.setNumber(element.id, "width", width);
    this.setNumber(element.id, "height", height);
    this.setNumber(element.id, "depth", Math.min(defaultDepth, thickness));
    this.setNumber(element.id, "rotation", wall.imported && alongZ ? 90 : wall.rotation);
    this.store.addQuad(nn(wallId), nn(NS.bot + "hasSubElement"), nn(element.id));
    this.store.addQuad(nn(wallId), nn(NS.bot + "hasSubElement"), nn(opening.id));
    this.store.addQuad(nn(element.id), nn(NS.cad + "opening"), nn(opening.id));
    return this.elements.find(item => item.id === element.id)!;
  }
  hasDerivedWall(id: string): boolean {
    const source = this.importedSource(id);
    return !!source && this.store.getQuads(source.geometry, CAD_BASE_OBJ, null, null).length > 0;
  }
  setDerivedWallObj(id: string, obj: string): void {
    const source = this.importedSource(id);
    if (!source || !this.hasDerivedWall(id)) throw new Error("Wall has no editable base OBJ");
    const parsed = parseObj(obj);
    const minima: Point = [Infinity, Infinity, Infinity], maxima: Point = [-Infinity, -Infinity, -Infinity];
    for (const point of parsed.vertices) for (let axis = 0; axis < 3; axis++) {
      minima[axis] = Math.min(minima[axis], point[axis]);
      maxima[axis] = Math.max(maxima[axis], point[axis]);
    }
    const currentWireframe = this.store.getQuads(source.geometry, nn(LBD + "hasWireframe"), null, null)[0]?.object;
    const prefix = currentWireframe?.termType === "Literal" ? currentWireframe.value.split("MULTILINESTRING")[0] : "";
    const edges = new Set<string>();
    for (let index = 0; index < parsed.indices.length; index += 3) {
      const triangle = parsed.indices.slice(index, index + 3);
      for (const [a, b] of [[triangle[0], triangle[1]], [triangle[1], triangle[2]], [triangle[2], triangle[0]]])
        edges.add([Math.min(a, b), Math.max(a, b)].join(":"));
    }
    const lines = [...edges].map(edge => {
      const [a, b] = edge.split(":").map(Number);
      return `(${parsed.vertices[a].join(" ")}, ${parsed.vertices[b].join(" ")})`;
    });
    const wkt = `${prefix}MULTILINESTRING Z(${lines.join(", ")})`;
    this.replaceTerm(source.geometry, FOG_OBJ, f.literal(encodeObj(obj), source.encoded.datatype));
    this.replaceTerm(source.geometry, nn(LBD + "hasWireframe"), f.literal(wkt,
      currentWireframe?.termType === "Literal" ? currentWireframe.datatype : nn("http://www.opengis.net/ont/geosparql#wktLiteral")));
    for (const [index, axis] of ["x", "y", "z"].entries()) {
      this.replaceTerm(source.bounds, nn(LBD + `${axis}-min`), f.literal(String(minima[index]), nn(NS.xsd + "double")));
      this.replaceTerm(source.bounds, nn(LBD + `${axis}-max`), f.literal(String(maxima[index]), nn(NS.xsd + "double")));
    }
  }
  setLabel(id: string, label: string) { this.replace(id, NS.rdfs + "label", f.literal(label)); }
  setLayer(id: string, layer: string): void {
    if (!this.elements.some(element => element.id === id)) throw new Error(`Unknown element: ${id}`);
    const value = layer.trim();
    if (!value || value.length > 80) throw new Error("Layer needs a name of at most 80 characters");
    this.replace(id, NS.cad + "layer", f.literal(value));
  }
  setGroup(id: string, group?: string): void {
    if (!this.elements.some(element => element.id === id)) throw new Error(`Unknown element: ${id}`);
    const value = group?.trim();
    if (value && value.length > 80) throw new Error("Group name is too long");
    if (value) this.replace(id, NS.cad + "group", f.literal(value));
    else this.store.removeQuads(this.store.getQuads(nn(id), nn(NS.cad + "group"), null, null));
  }
  setHidden(id: string, hidden: boolean): void {
    if (!this.elements.some(element => element.id === id)) throw new Error(`Unknown element: ${id}`);
    this.replace(id, NS.cad + "hidden", f.literal(String(hidden), nn(NS.xsd + "boolean")));
  }
  setColor(id: string, color: string): void {
    if (!this.elements.some(element => element.id === id)) throw new Error(`Unknown element: ${id}`);
    if (!/^#[0-9a-fA-F]{6}$/.test(color)) throw new Error("Color must be a six digit hex value");
    this.replace(id, NS.cad + "color", f.literal(color.toLowerCase()));
  }
  setFinish(id: string, finish?: Finish): void {
    if (!this.elements.some(element => element.id === id)) throw new Error(`Unknown element: ${id}`);
    if (finish && !Object.hasOwn(finishes, finish)) throw new Error(`Unknown finish: ${finish}`);
    this.assignMaterial(id, finish ? this.ensurePresetMaterial(finish).id : undefined);
    this.store.removeQuads(this.store.getQuads(nn(id), nn(NS.cad + "color"), null, null));
  }
  setNumber(id: string, field: Field, value: number) {
    if (!Number.isFinite(value) || (["width", "height", "depth"].includes(field) && value <= 0)) throw new Error(`${field} must be a ${field === "width" || field === "height" || field === "depth" ? "positive " : ""}number`);
    if (this.store.getQuads(nn(id), OMG_GEOMETRY, null, null).length || this.relatedOpening(id) ||
        this.store.getQuads(nn(id), nn(NS.bot + "hasSubElement"), null, null).length) {
      this.setGeometry(id, { [field]: value }); return;
    }
    this.replace(id, NS.cad + field, f.literal(String(value), nn(NS.xsd + "decimal")));
  }
  private replace(id: string, predicate: string, object: ReturnType<typeof f.literal>) {
    this.replaceTerm(nn(id), nn(predicate), object);
  }
  private replaceTerm(subject: Quad["subject"], predicate: Quad["predicate"], object: Quad["object"]) {
    this.store.removeQuads(this.store.getQuads(subject, predicate, null, null));
    this.store.addQuad(subject, predicate, object);
  }
  remove(id: string) {
    const mesh = this.elements.find(element => element.id === id && element.kind === "Mesh");
    if (mesh) {
      const source = this.importedSource(id);
      if (source) {
        this.store.removeQuads(this.store.getQuads(source.geometry, null, null, null));
        this.store.removeQuads(this.store.getQuads(source.bounds, null, null, null));
      }
      for (const link of this.store.getQuads(nn(id), nn(NS.cad + "hasObjMaterial"), null, null))
        this.store.removeQuads(this.store.getQuads(link.object, null, null, null));
    }
    for (const face of boxFaces) {
      const faceId = this.faceId(id, face);
      this.store.removeQuads(this.store.getQuads(nn(faceId), null, null, null));
    }
    const children = this.store.getQuads(nn(id), nn(NS.bot + "hasSubElement"), null, null)
      .map(q => q.object.value);
    const opening = this.relatedOpening(id);
    for (const child of [...children, ...(opening ? [opening] : [])])
      if (child !== id && this.store.getQuads(nn(child), nn(NS.rdf + "type"), null, null)
        .some(q => q.object.value.startsWith(NS.cad))) this.remove(child);
    this.store.removeQuads(this.store.getQuads(nn(id), null, null, null));
    this.store.removeQuads(this.store.getQuads(null, null, nn(id), null));
  }
  triples(id: string): Quad[] { return this.store.getQuads(nn(id), null, null, null); }
  serialize(): Promise<string> {
    return new Promise((resolve, reject) => {
      const writer = new Writer({ prefixes: NS });
      writer.addQuads(this.store.getQuads(null, null, null, null));
      writer.end((error, result) => error ? reject(error) : resolve(result ?? ""));
    });
  }
  snapshot(): Quad[] { return this.store.getQuads(null, null, null, null); }
}

export function starterDocument(): CadDocument {
  const doc = new CadDocument();
  const slab = doc.add("Slab", { x: 0, y: -.125, z: 0 }); doc.setLabel(slab.id, "Ground slab");
  const wall = doc.add("Wall", { x: 0, y: 1.5, z: -1.9 }); doc.setLabel(wall.id, "North wall");
  const space = doc.add("Space", { x: 0, y: 1.5, z: 0 }); doc.setLabel(space.id, "Room 101");
  return doc;
}
