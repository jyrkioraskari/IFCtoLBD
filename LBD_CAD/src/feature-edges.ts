export type Point3 = [number, number, number];
export type Segment3 = [Point3, Point3];

interface EdgeRecord { start: Point3; end: Point3; normals: Point3[] }

/** Shape boundaries and creases, with coplanar triangle diagonals removed. */
export function featureEdges(positions: ArrayLike<number>, indices: ArrayLike<number>, creaseDegrees = 25): Segment3[] {
  const edges = new Map<string, EdgeRecord>();
  const key = (point: Point3) => point.map(value => Math.round(value * 100000)).join(":");
  const vertex = (index: number): Point3 => [positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]];
  for (let i = 0; i + 2 < indices.length; i += 3) {
    const a = vertex(indices[i]), b = vertex(indices[i + 1]), c = vertex(indices[i + 2]);
    const u: Point3 = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
    const v: Point3 = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];
    const cross: Point3 = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
    const length = Math.hypot(...cross);
    if (length < 1e-10) continue;
    const normal: Point3 = cross.map(value => value / length) as Point3;
    for (const [start, end] of [[a, b], [b, c], [c, a]] as Segment3[]) {
      const first = key(start), second = key(end);
      if (first === second) continue;
      const edgeKey = first < second ? `${first}|${second}` : `${second}|${first}`;
      const record = edges.get(edgeKey);
      if (record) record.normals.push(normal);
      else edges.set(edgeKey, { start, end, normals: [normal] });
    }
  }
  const threshold = Math.cos(creaseDegrees * Math.PI / 180);
  return [...edges.values()].filter(edge => edge.normals.length === 1 ||
    edge.normals.some((normal, i) => edge.normals.slice(i + 1).some(other =>
      Math.abs(normal[0] * other[0] + normal[1] * other[1] + normal[2] * other[2]) < threshold)))
    .map(edge => [edge.start, edge.end]);
}
