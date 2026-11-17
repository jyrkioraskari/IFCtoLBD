import { Mesh, SubMesh, VertexBuffer, VertexData, type Scene } from "@babylonjs/core";
import type { Element, MaterialDefinition } from "./model";
import { materialBox } from "./material-box";

/** A pane or leaf, visible jambs, and door hardware inside the wall cut. */
export function hostedInsert(element: Element, leaf: MaterialDefinition, frame: MaterialDefinition, scene: Scene): Mesh {
  const positions: number[] = [], indices: number[] = [], uvs: number[] = [];
  const ranges: { material: number; vertexStart: number; indexStart: number; vertexCount: number; indexCount: number }[] = [];
  const append = (w: number, h: number, d: number, x: number, y: number, z: number, material: number) => {
    const part = materialBox({ ...element, width: w, height: h, depth: d },
      Array(6).fill(material === 0 ? leaf : frame), scene);
    const vertices = part.getVerticesData(VertexBuffer.PositionKind)!;
    const texcoords = part.getVerticesData(VertexBuffer.UVKind)!;
    const faces = part.getIndices()!;
    const vertexStart = positions.length / 3, indexStart = indices.length;
    for (let i = 0; i < vertices.length; i += 3) positions.push(vertices[i] + x, vertices[i + 1] + y, vertices[i + 2] + z);
    uvs.push(...texcoords);
    indices.push(...Array.from(faces, index => index + vertexStart));
    ranges.push({ material, vertexStart, indexStart, vertexCount: vertices.length / 3, indexCount: faces.length });
    part.dispose();
  };
  const rail = Math.min(.07, element.width * .12, element.height * .12);
  const depth = Math.max(.025, element.depth);
  const clearWidth = Math.max(.01, element.width - rail * 2);
  const clearHeight = Math.max(.01, element.height - (element.kind === "Door" ? rail : rail * 2));
  const leafDepth = Math.min(.04, depth);
  append(clearWidth, clearHeight, leafDepth, 0,
    element.kind === "Door" ? -rail / 2 : 0, 0, 0);
  append(rail, element.height, depth, -(element.width - rail) / 2, 0, 0, 1);
  append(rail, element.height, depth, (element.width - rail) / 2, 0, 0, 1);
  append(clearWidth, rail, depth, 0, (element.height - rail) / 2, 0, 1);
  if (element.kind === "Window") append(clearWidth, rail, depth, 0, -(element.height - rail) / 2, 0, 1);
  if (element.kind === "Door") {
    const handleX = element.width / 2 - rail - Math.min(.12, clearWidth * .18);
    const handleY = -.08 * element.height;
    for (const side of [-1, 1]) {
      const faceZ = side * (leafDepth / 2 + .008);
      append(.035, .14, .016, handleX, handleY, faceZ, 2);
      append(.12, .023, .026, handleX - .04, handleY, side * (leafDepth / 2 + .026), 2);
    }
  }
  const normals: number[] = [];
  VertexData.ComputeNormals(positions, indices, normals);
  const mesh = new Mesh(element.id, scene);
  const data = new VertexData(); data.positions = positions; data.indices = indices; data.uvs = uvs; data.normals = normals;
  data.applyToMesh(mesh);
  mesh.subMeshes = [];
  for (const range of ranges)
    new SubMesh(range.material, range.vertexStart, range.vertexCount, range.indexStart, range.indexCount, mesh);
  return mesh;
}
