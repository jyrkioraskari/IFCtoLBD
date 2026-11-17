import { Mesh, MeshBuilder, VertexData, type Scene } from "@babylonjs/core";
import { applyProjectedUvs } from "./projected-uvs";
import { cutWallOpenings } from "./wall-cut";
import type { CadDocument, Element } from "./model";

/** Disposable wall cut that follows a hosted insert without changing Turtle. */
export class HostedCutPreview {
  private readonly base: Mesh;
  private cut?: Mesh;
  private lastUpdate = 0;

  constructor(private readonly doc: CadDocument, private readonly wall: Element,
    private readonly wallMesh: Mesh, private readonly insertId: string, private readonly scene: Scene) {
    const imported = doc.importedMesh(wall.id, wall);
    if (imported) {
      this.base = new Mesh(`cut-base-${wall.id}`, scene);
      const data = new VertexData();
      data.positions = imported.positions; data.indices = imported.indices;
      const normals: number[] = [];
      VertexData.ComputeNormals(imported.positions, imported.indices, normals);
      data.normals = normals; data.applyToMesh(this.base);
    } else this.base = MeshBuilder.CreateBox(`cut-base-${wall.id}`,
      { width: wall.width, height: wall.height, depth: wall.depth }, scene);
    this.base.isVisible = false;
    this.base.isPickable = false;
  }

  update(insert: Element, target: { x: number; y: number; z: number }, force = false): void {
    const now = performance.now();
    if (!force && this.cut && this.wall.imported && now - this.lastUpdate < 80) return;
    const openingId = this.doc.relatedOpening(this.insertId);
    if (!openingId) return;
    const dx = target.x - insert.x, dy = target.y - insert.y, dz = target.z - insert.z;
    const hosted = this.doc.hostedOpenings(this.wall.id);
    const openings = hosted.map(opening => opening.id === openingId ?
      { ...opening, x: opening.x + dx, y: opening.y + dy, z: opening.z + dz } : opening);
    const originalVoids = openings.filter(opening => opening.imported).flatMap(opening => {
      const original = this.doc.originalOpening(opening.id) ??
        (opening.id === openingId ? hosted.find(item => item.id === opening.id) : undefined);
      return original ? [original] : [];
    });
    const active = openings.filter(opening => !opening.imported || opening.id === openingId ||
      this.doc.originalOpening(opening.id));
    const next = cutWallOpenings(this.base, this.wall, active, this.scene, originalVoids);
    const material = this.doc.material(this.wall.materialId ?? "");
    if (material && material.texture !== "none") applyProjectedUvs(next, material);
    next.position.copyFrom(this.wallMesh.position);
    next.rotation.copyFrom(this.wallMesh.rotation);
    next.material = this.wallMesh.material;
    next.isPickable = false;
    this.cut?.dispose();
    this.cut = next;
    this.wallMesh.isVisible = false;
    for (const child of this.wallMesh.getChildMeshes()) child.isVisible = false;
    this.lastUpdate = now;
  }

  dispose(): void {
    this.cut?.dispose();
    this.base.dispose();
  }
}
