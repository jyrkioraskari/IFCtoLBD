import { featureEdges } from "./feature-edges";

export interface ExportPart { name: string; kind?: string; layer: string; positions: number[]; indices: number[] }

export function exportObj(parts: readonly ExportPart[]): string {
  const lines = ["# LBD CAD OBJ export · metres · Y up"];
  let offset = 0;
  for (const part of parts) {
    lines.push(`o ${part.name.replace(/[\r\n]/g, " ")}`);
    for (let i = 0; i < part.positions.length; i += 3)
      lines.push(`v ${part.positions[i]} ${part.positions[i + 1]} ${part.positions[i + 2]}`);
    for (let i = 0; i < part.indices.length; i += 3)
      lines.push(`f ${part.indices[i] + offset + 1} ${part.indices[i + 1] + offset + 1} ${part.indices[i + 2] + offset + 1}`);
    offset += part.positions.length / 3;
  }
  return lines.join("\n") + "\n";
}

export function exportStl(parts: readonly ExportPart[]): string {
  const lines = ["solid lbd_cad"];
  for (const part of parts) for (let i = 0; i < part.indices.length; i += 3) {
    const vertices = [0, 1, 2].map(j => {
      const index = part.indices[i + j] * 3;
      return part.positions.slice(index, index + 3);
    });
    const [a, b, c] = vertices;
    const u = b.map((value, axis) => value - a[axis]);
    const v = c.map((value, axis) => value - a[axis]);
    const normal = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
    const length = Math.hypot(...normal);
    if (length < 1e-12) continue;
    lines.push(`facet normal ${normal.map(value => value / length).join(" ")}`, "outer loop",
      ...vertices.map(vertex => `vertex ${vertex.join(" ")}`), "endloop", "endfacet");
  }
  lines.push("endsolid lbd_cad");
  return lines.join("\n") + "\n";
}

/** Top projection of feature edges. This is a model outline, not a scaled drawing sheet. */
export function exportDxfPlan(parts: readonly ExportPart[]): string {
  const pairs = ["0", "SECTION", "2", "HEADER", "9", "$INSUNITS", "70", "6", "0", "ENDSEC",
    "0", "SECTION", "2", "ENTITIES"];
  for (const part of parts) for (const [a, b] of featureEdges(part.positions, part.indices)) {
    if (Math.hypot(a[0] - b[0], a[2] - b[2]) < 1e-7) continue;
    pairs.push("0", "LINE", "8", part.layer.replace(/[\r\n]/g, " ").slice(0, 255) || "0",
      "10", String(a[0]), "20", String(-a[2]), "30", "0",
      "11", String(b[0]), "21", String(-b[2]), "31", "0");
  }
  pairs.push("0", "ENDSEC", "0", "EOF");
  return pairs.join("\n") + "\n";
}

export interface QuantityRow { name: string; kind: string; layer: string; area: number; volume?: number; closed: boolean }

export function exportQuantitiesCsv(rows: readonly QuantityRow[]): string {
  const quote = (value: string) => `"${value.replaceAll('"', '""')}"`;
  return ["name,kind,layer,surface_m2,volume_m3,closed",
    ...rows.map(row => [quote(row.name), quote(row.kind), quote(row.layer), row.area.toFixed(4),
      row.volume === undefined ? "" : row.volume.toFixed(4), String(row.closed)].join(","))].join("\n") + "\n";
}
