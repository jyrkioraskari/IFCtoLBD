import { CSG, MeshBuilder, Ray, Vector3, VertexBuffer, type Mesh, type Scene } from "@babylonjs/core";
import type { Element } from "./model";
import { cutSurfaceOpenings } from "./surface-cut";

function signedVolume(mesh: Mesh): number {
  const positions = mesh.getVerticesData(VertexBuffer.PositionKind);
  const indices = mesh.getIndices();
  if (!positions || !indices) return 0;
  let sixVolumes = 0;
  for (let i = 0; i < indices.length; i += 3) {
    const a = indices[i] * 3, b = indices[i + 1] * 3, c = indices[i + 2] * 3;
    sixVolumes += positions[a] * (positions[b + 1] * positions[c + 2] - positions[b + 2] * positions[c + 1])
      + positions[a + 1] * (positions[b + 2] * positions[c] - positions[b] * positions[c + 2])
      + positions[a + 2] * (positions[b] * positions[c + 1] - positions[b + 1] * positions[c]);
  }
  return sixVolumes / 6;
}

/** Cut linked opening volumes from a wall mesh in wall-local coordinates. */
export function cutWallOpenings(base: Mesh, wall: Element, openings: readonly Element[], scene: Scene,
  originalVoids: readonly Pick<Element, "x" | "y" | "z" | "width" | "height" | "depth">[] = []): Mesh {
  let solid = CSG.FromMesh(base, true);
  // Babylon boxes have negative signed volume. Some IFC OBJ walls use the
  // opposite winding, which makes CSG subtraction add the cutter instead.
  if (signedVolume(base) > 0) solid = solid.inverse();
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
  const result = solid.toMesh(wall.id, null, scene);
  if (wall.imported && !originalVoids.length && openings.some(opening => {
    const dx = opening.x - wall.x, dz = opening.z - wall.z;
    const centerX = dx * Math.cos(angle) - dz * Math.sin(angle);
    const centerZ = dx * Math.sin(angle) + dz * Math.cos(angle);
    const relative = (opening.rotation - wall.rotation) * Math.PI / 180;
    const direction = new Vector3(Math.sin(relative), 0, Math.cos(relative));
    const center = new Vector3(centerX, opening.y - wall.y, centerZ);
    const ray = new Ray(center.subtract(direction.scale(opening.depth / 2 + .01)), direction, opening.depth + .02);
    return base.intersects(ray).hit && result.intersects(ray).hit;
  })) {
    result.dispose();
    return cutSurfaceOpenings(base, wall, openings, scene);
  }
  return result;
}
