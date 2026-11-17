import { Mesh, SubMesh, VertexData, type Scene } from "@babylonjs/core";
import { boxFaces, type BoxFaceKey, type Element, type MaterialDefinition } from "./model";

/** Six separate local faces keep their material slots stable through resize and rotation. */
export function materialBox(element: Element, materials: MaterialDefinition[], scene: Scene): Mesh {
  const { width: w, height: h, depth: d } = element;
  const x = w / 2, y = h / 2, z = d / 2;
  const faces: Record<BoxFaceKey, [number, number, number][]> = {
    px: [[x,-y,-z],[x,y,-z],[x,y,z],[x,-y,z]],
    nx: [[-x,-y,z],[-x,y,z],[-x,y,-z],[-x,-y,-z]],
    py: [[-x,y,-z],[-x,y,z],[x,y,z],[x,y,-z]],
    ny: [[-x,-y,z],[-x,-y,-z],[x,-y,-z],[x,-y,z]],
    pz: [[x,-y,z],[x,y,z],[-x,y,z],[-x,-y,z]],
    nz: [[-x,-y,-z],[-x,y,-z],[x,y,-z],[x,-y,-z]]
  };
  const dimensions: Record<BoxFaceKey, [number, number]> = {
    px: [h,d], nx: [h,d], py: [d,w], ny: [d,w], pz: [h,w], nz: [h,w]
  };
  const positions: number[] = [], indices: number[] = [], uvs: number[] = [];
  boxFaces.forEach((face, index) => {
    const [u, v] = dimensions[face];
    const material = materials[index];
    const angle = material.grainDegrees * Math.PI / 180;
    const c = Math.cos(angle), s = Math.sin(angle);
    const map = (a: number, b: number) => [
      (a * c - b * s) / material.tileWidth,
      (a * s + b * c) / material.tileHeight
    ];
    positions.push(...faces[face].flat());
    uvs.push(...map(0, 0), ...map(u, 0), ...map(u, v), ...map(0, v));
    const start = index * 4;
    indices.push(start, start + 1, start + 2, start, start + 2, start + 3);
  });
  const normals: number[] = [];
  VertexData.ComputeNormals(positions, indices, normals);
  const mesh = new Mesh(element.id, scene);
  const data = new VertexData();
  data.positions = positions; data.indices = indices; data.uvs = uvs; data.normals = normals;
  data.applyToMesh(mesh);
  mesh.subMeshes = [];
  boxFaces.forEach((_, index) => new SubMesh(index, index * 4, 4, index * 6, 6, mesh));
  return mesh;
}
