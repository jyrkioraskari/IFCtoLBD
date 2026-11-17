export type Point = [number, number, number];
export interface ObjGeometry { vertices: Point[]; indices: number[] }

export function decodeObj(value: string): string {
  const bytes = Uint8Array.from(atob(value.replace(/\s/g, "")), char => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

export function encodeObj(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 32768)
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 32768));
  return btoa(binary);
}

export function parseObj(value: string): ObjGeometry {
  const vertices: Point[] = [], indices: number[] = [];
  for (const line of value.split(/\r?\n/)) {
    const parts = line.trim().split(/\s+/);
    if (parts[0] === "v" && parts.length >= 4) vertices.push([Number(parts[1]), Number(parts[2]), Number(parts[3])]);
    if (parts[0] === "f" && parts.length >= 4) {
      const face = parts.slice(1).map(part => Number(part.split("/")[0]) - 1);
      for (let i = 1; i < face.length - 1; i++) indices.push(face[0], face[i], face[i + 1]);
    }
  }
  if (!vertices.length || !indices.length || vertices.some(point => point.some(value => !Number.isFinite(value))) ||
      indices.some(index => !Number.isInteger(index) || index < 0 || index >= vertices.length))
    throw new Error("OBJ has no usable vertices and faces");
  return { vertices, indices };
}

export function rewriteObj(value: string, transform: (point: Point) => Point): { text: string; vertices: Point[] } {
  const vertices: Point[] = [];
  const lines = value.split(/\r?\n/).map(line => {
    const match = line.match(/^(\s*v\s+)(\S+)\s+(\S+)\s+(\S+)(.*)$/);
    if (!match) return line;
    const point: Point = [Number(match[2]), Number(match[3]), Number(match[4])];
    if (point.some(number => !Number.isFinite(number))) throw new Error("OBJ contains an invalid vertex");
    const next = transform(point);
    vertices.push(next);
    return `${match[1]}${next[0]} ${next[1]} ${next[2]}${match[5]}`;
  });
  if (!vertices.length) throw new Error("OBJ contains no vertices");
  return { text: lines.join("\n"), vertices };
}

export function rewriteWireframe(value: string, transform: (point: Point) => Point): string {
  const coordinate = /(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s+(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s+(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g;
  const start = value.indexOf("((");
  if (start < 0) throw new Error("Unsupported wireframe WKT");
  return value.slice(0, start + 2) + value.slice(start + 2).replace(coordinate, (_match, x: string, y: string, z: string) => transform([Number(x), Number(y), Number(z)]).join(" "));
}
