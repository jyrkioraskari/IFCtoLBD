import { CSG, MeshBuilder, type Mesh, type Scene } from "@babylonjs/core";
import type { Element } from "./model";

/** Cut linked opening volumes from a wall mesh in wall-local coordinates. */
export function cutWallOpenings(base: Mesh, wall: Element, openings: readonly Element[], scene: Scene,
  originalVoids: readonly Pick<Element, "x" | "y" | "z" | "width" | "height" | "depth">[] = []): Mesh {
  let solid = CSG.FromMesh(base, true);
  const angle = wall.rotation * Math.PI / 180;
  // IFC OBJ geometry already contains its original window and door holes.
  // Restore an edited opening's old volume before cutting its current one.
  for (const original of originalVoids) {
    const axes = (["x", "y", "z"] as const).map(axis => {
      const wallSize = axis === "x" ? wall.width : axis === "y" ? wall.height : wall.depth;
      const openingSize = axis === "x" ? original.width : axis === "y" ? original.height : original.depth;
      const wallMin = wall[axis] - wallSize / 2, wallMax = wall[axis] + wallSize / 2;
      const min = Math.max(wallMin, original[axis] - openingSize / 2);
      const max = Math.min(wallMax, original[axis] + openingSize / 2);
      return { center: (min + max) / 2, size: max - min };
    });
    if (axes.some(axis => axis.size <= .001)) continue;
    const fill = MeshBuilder.CreateBox("original-opening-fill", {
      width: axes[0].size, height: axes[1].size, depth: axes[2].size }, scene);
    const dx = axes[0].center - wall.x, dz = axes[2].center - wall.z;
    fill.position.set(dx * Math.cos(angle) - dz * Math.sin(angle), axes[1].center - wall.y,
      dx * Math.sin(angle) + dz * Math.cos(angle));
    try { solid = solid.union(CSG.FromMesh(fill, true)); }
    finally { fill.dispose(); }
  }
  for (const opening of openings) {
    const dx = opening.x - wall.x, dz = opening.z - wall.z;
    const cutter = MeshBuilder.CreateBox("opening-cutter", {
      width: opening.width, height: opening.height, depth: opening.depth }, scene);
    cutter.position.set(dx * Math.cos(angle) - dz * Math.sin(angle), opening.y - wall.y,
      dx * Math.sin(angle) + dz * Math.cos(angle));
    cutter.rotation.y = (opening.rotation - wall.rotation) * Math.PI / 180;
    try { solid = solid.subtract(CSG.FromMesh(cutter, true)); }
    finally { cutter.dispose(); }
  }
  return solid.toMesh(wall.id, null, scene);
}
