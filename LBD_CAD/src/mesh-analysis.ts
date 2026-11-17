export interface MeshAnalysis {
  surfaceArea: number;
  volume: number | undefined;
  boundaryEdges: number;
  nonManifoldEdges: number;
}

/** Metric area and volume of a triangle mesh; volume requires a closed manifold. */
export function analyzeMesh(positions: ArrayLike<number>, indices: ArrayLike<number>): MeshAnalysis {
  const edges = new Map<string, number>();
  let surfaceArea = 0;
  let signedVolume = 0;
  const point = (index: number) => [positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]] as const;
  const key = (index: number) => point(index).map(value => Math.round(value * 100000)).join(":");
  for (let i = 0; i + 2 < indices.length; i += 3) {
    const [ia, ib, ic] = [indices[i], indices[i + 1], indices[i + 2]];
    const a = point(ia), b = point(ib), c = point(ic);
    const ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
    const vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
    const cross = [uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx];
    const area = Math.hypot(...cross) / 2;
    if (area < 1e-12) continue;
    surfaceArea += area;
    signedVolume += (a[0] * (b[1] * c[2] - b[2] * c[1]) +
      a[1] * (b[2] * c[0] - b[0] * c[2]) + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6;
    for (const [start, end] of [[ia, ib], [ib, ic], [ic, ia]]) {
      const ka = key(start), kb = key(end);
      const edge = ka < kb ? `${ka}|${kb}` : `${kb}|${ka}`;
      edges.set(edge, (edges.get(edge) ?? 0) + 1);
    }
  }
  const boundaryEdges = [...edges.values()].filter(count => count === 1).length;
  const nonManifoldEdges = [...edges.values()].filter(count => count > 2).length;
  return { surfaceArea, volume: boundaryEdges || nonManifoldEdges ? undefined : Math.abs(signedVolume),
    boundaryEdges, nonManifoldEdges };
}
