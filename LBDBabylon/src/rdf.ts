import { DataFactory, Parser, Store, type Quad, type Term } from "n3";
import type { Entity, EntityKind, GeometrySource, ModelData } from "./types";

export const RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
export const RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
export const OMG_GEOMETRY = "https://w3id.org/omg#hasGeometry";
export const GEO_GEOMETRY = "http://www.opengis.net/ont/geosparql#hasGeometry";
export const FOG_OBJ = "https://w3id.org/fog#asObj_v3.0-obj";
export const ARTIFACT = "https://w3id.org/ifctolbd/geometry#artifact";
export const ARTIFACT_MEDIA_TYPE = "https://w3id.org/ifctolbd/geometry#mediaType";
const GEOMETRY_META = "https://w3id.org/ifctolbd/geometry#";
const MTL_KD = "https://lbd.org/#asMTL_kd";
const MTL_ALPHA = "https://lbd.org/#asMTL_alpha";
export const BOT = "https://w3id.org/bot#";
const termId = (term: Term) => term.value;

export async function parseTurtle(text: string): Promise<ModelData> {
  const parser = new Parser();
  const parsed = parser.parse(text);
  const store = new Store(parsed);
  const prefixes = (parser as unknown as { _prefixes: Record<string, string> })._prefixes;
  const subjects = new Map<string, Term>();
  parsed.forEach(q => subjects.set(termId(q.subject), q.subject));
  const entities = [...subjects.values()].map(subject => entityFor(store, subject)).filter((x): x is Entity => !!x);
  const geometries: GeometrySource[] = [];
  const warnings: string[] = [];
  for (const q of parsed) {
    if (q.predicate.value !== OMG_GEOMETRY && q.predicate.value !== GEO_GEOMETRY || q.object.termType === "Literal") continue;
    const embedded = store.getQuads(q.object, DataFactory.namedNode(FOG_OBJ), null, null)[0];
    const diffuseColor = store.getQuads(q.object, DataFactory.namedNode(MTL_KD), null, null)[0]?.object.value;
    const alphaValue = Number(store.getQuads(q.object, DataFactory.namedNode(MTL_ALPHA), null, null)[0]?.object.value);
    const material = { diffuseColor: /^#[0-9a-f]{6}$/i.test(diffuseColor ?? "") ? diffuseColor : undefined, alpha: Number.isFinite(alphaValue) ? Math.max(0, Math.min(1, alphaValue)) : undefined };
    const textMeta = (name: string) => store.getQuads(q.object, DataFactory.namedNode(GEOMETRY_META + name), null, null)[0]?.object.value;
    const unitToMetres = Number(textMeta("unitToMetres"));
    const metadata = { coordinateSpace: textMeta("objCoordinateSpace") ?? textMeta("coordinateSpace"), coordinateReferenceSystem: textMeta("objCoordinateReferenceSystem") ?? textMeta("coordinateReferenceSystem"), lengthUnit: textMeta("lengthUnit"), unitToMetres: Number.isFinite(unitToMetres) ? unitToMetres : undefined };
    if (embedded?.object.termType === "Literal") geometries.push({ element: termId(q.subject), geometry: termId(q.object), kind: "embedded-obj", payload: embedded.object.value, ...material, ...metadata });
    const artifact = store.getQuads(q.object, DataFactory.namedNode(ARTIFACT), null, null)[0];
    const mediaType = artifact && store.getQuads(artifact.object, DataFactory.namedNode(ARTIFACT_MEDIA_TYPE), null, null)[0]?.object.value;
    if (artifact && mediaType === "model/obj") geometries.push({ element: termId(q.subject), geometry: termId(q.object), kind: "artifact-obj", asset: artifact.object.value, ...material, ...metadata });
    if (!embedded && (!artifact || mediaType !== "model/obj")) warnings.push(`Geometry for ${termId(q.subject)} is not an IFCtoLBD OBJ payload or model/obj artifact.`);
  }
  return { store, prefixes, entities, geometries, warnings: [...new Set(warnings)], quads: parsed };
}

function entityFor(store: Store, subject: Term): Entity | undefined {
  if (subject.termType === "Literal") return undefined;
  const types = store.getQuads(subject, DataFactory.namedNode(RDF), null, null).map(q => q.object.value);
  const typeText = types.join(" ").toLowerCase();
  const kind: EntityKind = typeText.includes("buildingstorey") || types.includes(BOT + "Storey") ? "storey" : typeText.includes("building") || types.includes(BOT + "Building") ? "building" : typeText.includes("space") || types.includes(BOT + "Space") ? "space" : types.length ? "element" : "other";
  const label = store.getQuads(subject, DataFactory.namedNode(RDFS_LABEL), null, null)[0]?.object.value ?? subject.value.split(/[\/#]/).pop() ?? subject.value;
  const containedBy = store.getQuads(subject, DataFactory.namedNode(BOT + "containsElement"), null, null)[0];
  return { id: subject.value, kind, label, types, storey: containedBy?.subject.value };
}

export function displayTerm(term: Term): string { return term.termType === "Literal" ? `“${term.value}”${term.language ? "@" + term.language : term.datatype.value.endsWith("#string") ? "" : "^^" + term.datatype.value}` : term.value; }
export function findGlobalId(store: Store, id: string): string | undefined { return store.getQuads(DataFactory.namedNode(id), null, null, null).find(q => /globalid/i.test(q.predicate.value))?.object.value; }
