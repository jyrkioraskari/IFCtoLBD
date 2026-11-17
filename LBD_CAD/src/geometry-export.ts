import { Vector3, VertexBuffer, type Mesh } from "@babylonjs/core";

/** Serialize the displayed mesh in the IFC coordinate frame (X, Y, Z-up). */
export function meshToIfcObj(mesh: Mesh): string {
  const positions = mesh.getVerticesData(VertexBuffer.PositionKind);
  const indices = mesh.getIndices();
  if (!positions?.length || !indices?.length || indices.length % 3 !== 0)
    throw new Error("Wall cut has no triangle geometry");
  const world = mesh.computeWorldMatrix(true);
  const lines: string[] = [];
  for (let index = 0; index < positions.length; index += 3) {
    const point = Vector3.TransformCoordinates(new Vector3(positions[index], positions[index + 1], positions[index + 2]), world);
    lines.push(`v ${point.x} ${-point.z} ${point.y}`);
  }
  for (let index = 0; index < indices.length; index += 3)
    lines.push(`f ${indices[index] + 1} ${indices[index + 1] + 1} ${indices[index + 2] + 1}`);
  return lines.join("\n") + "\n";
}
