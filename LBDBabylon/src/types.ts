import type { Quad, Store } from "n3";

export type EntityKind = "building" | "storey" | "space" | "element" | "other";
export interface Entity { id: string; kind: EntityKind; label: string; types: string[]; storey?: string }
export interface GeometrySource { element: string; geometry: string; kind: "embedded-obj" | "artifact-obj"; payload?: string; asset?: string; diffuseColor?: string; alpha?: number; coordinateSpace?: string; coordinateReferenceSystem?: string; lengthUnit?: string; unitToMetres?: number; }
export interface ModelData { store: Store; prefixes: Record<string, string>; entities: Entity[]; geometries: GeometrySource[]; warnings: string[]; quads: Quad[]; }
export interface ImportSummary { entities: number; renderedElements: number; meshes: number; unsupported: number; }
