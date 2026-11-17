import { parseObj, type Point } from "./ifc-geometry";

export type ObjUnit = "m" | "cm" | "mm" | "in" | "ft";
export type ObjUpAxis = "y" | "z";
export interface MtlMaterial { name: string; color: string; opacity: number; imageName?: string }
export interface ObjSurface { positions: number[]; indices: number[]; uvs: number[];
  runs: { name?: string; start: number; count: number }[] }
const metres: Record<ObjUnit, number> = { m: 1, cm: .01, mm: .001, in: .0254, ft: .3048 };

function reference(value: string, count: number): number {
  const index = Number(value);
  const resolved = index < 0 ? count + index : index - 1;
  if (!Number.isInteger(index) || index === 0 || resolved < 0 || resolved >= count)
    throw new Error(`Invalid OBJ index: ${value}`);
  return resolved;
}

/** Converts source units/up axis once, retaining UVs and material runs in editable OBJ text. */
export function prepareObj(text: string, unit: ObjUnit, up: ObjUpAxis): string {
  if (!Object.hasOwn(metres, unit) || (up !== "y" && up !== "z")) throw new Error("Invalid OBJ units or up axis");
  const output: string[] = [];
  let vertices = 0, texcoords = 0;
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    const parts = trimmed.split(/\s+/);
    if (parts[0] === "v" && parts.length >= 4) {
      const raw = parts.slice(1, 4).map(Number);
      if (raw.some(value => !Number.isFinite(value))) throw new Error("OBJ contains an invalid vertex");
      const [x, y, z] = raw.map(value => value * metres[unit]);
      const converted: Point = up === "y" ? [x, -z, y] : [x, y, z];
      output.push(`v ${converted.join(" ")}`); vertices++;
    } else if (parts[0] === "vt" && parts.length >= 3) {
      if (!Number.isFinite(Number(parts[1])) || !Number.isFinite(Number(parts[2])))
        throw new Error("OBJ contains an invalid texture coordinate");
      output.push(`vt ${parts[1]} ${parts[2]}`); texcoords++;
    } else if (parts[0] === "f" && parts.length >= 4) {
      const corners = parts.slice(1).map(token => {
        const [v, vt] = token.split("/");
        const vertex = reference(v, vertices) + 1;
        return vt ? `${vertex}/${reference(vt, texcoords) + 1}` : String(vertex);
      });
      for (let i = 1; i < corners.length - 1; i++) output.push(`f ${corners[0]} ${corners[i]} ${corners[i + 1]}`);
    } else if (["usemtl", "g", "o"].includes(parts[0])) output.push(trimmed);
  }
  const result = output.join("\n") + "\n";
  parseObj(result);
  return result;
}

/** Expands only the render mesh so each triangle can keep its own OBJ UV/material. */
export function readObjSurface(text: string): ObjSurface {
  const vertices: Point[] = [], texcoords: [number, number][] = [];
  const positions: number[] = [], indices: number[] = [], uvs: number[] = [], runs: ObjSurface["runs"] = [];
  let name: string | undefined;
  for (const line of text.split(/\r?\n/)) {
    const parts = line.trim().split(/\s+/);
    if (parts[0] === "v" && parts.length >= 4) vertices.push([Number(parts[1]), Number(parts[2]), Number(parts[3])]);
    else if (parts[0] === "vt" && parts.length >= 3) texcoords.push([Number(parts[1]), Number(parts[2])]);
    else if (parts[0] === "usemtl") name = parts.slice(1).join(" ");
    else if (parts[0] === "f" && parts.length >= 4) {
      const corners = parts.slice(1).map(token => {
        const [v, vt] = token.split("/");
        return { vertex: vertices[reference(v, vertices.length)], uv: vt ? texcoords[reference(vt, texcoords.length)] : undefined };
      });
      for (let i = 1; i < corners.length - 1; i++) {
        const run = runs.at(-1);
        if (!run || run.name !== name) runs.push({ name, start: indices.length, count: 0 });
        for (const corner of [corners[0], corners[i], corners[i + 1]]) {
          positions.push(...corner.vertex);
          uvs.push(...(corner.uv ?? [NaN, NaN]));
          indices.push(indices.length);
          runs.at(-1)!.count++;
        }
      }
    }
  }
  if (!indices.length || positions.some(value => !Number.isFinite(value))) throw new Error("OBJ has no usable faces");
  return { positions, indices, uvs, runs };
}

export function readMtl(text: string): MtlMaterial[] {
  const result: MtlMaterial[] = [];
  for (const line of text.split(/\r?\n/)) {
    const clean = line.trim();
    if (!clean || clean.startsWith("#")) continue;
    const [command, ...rest] = clean.split(/\s+/);
    if (command === "newmtl") result.push({ name: rest.join(" "), color: "#ffffff", opacity: 1 });
    const current = result.at(-1);
    if (!current) continue;
    if (command === "Kd" && rest.length >= 3) {
      const rgb = rest.slice(0, 3).map(Number);
      if (rgb.every(Number.isFinite)) current.color = "#" + rgb.map(value =>
        Math.round(Math.max(0, Math.min(1, value)) * 255).toString(16).padStart(2, "0")).join("");
    }
    if (command === "d" && Number.isFinite(Number(rest[0]))) current.opacity = Math.max(0, Math.min(1, Number(rest[0])));
    if (command === "Tr" && Number.isFinite(Number(rest[0]))) current.opacity = Math.max(0, Math.min(1, 1 - Number(rest[0])));
    if (command === "map_Kd") {
      const tokens = rest.slice();
      const optionSize: Record<string, number> = { "-s": 3, "-o": 3, "-t": 3, "-mm": 2,
        "-bm": 1, "-clamp": 1, "-blendu": 1, "-blendv": 1, "-imfchan": 1 };
      while (tokens[0]?.startsWith("-")) {
        const option = tokens.shift()!;
        tokens.splice(0, optionSize[option] ?? 1);
      }
      current.imageName = tokens.join(" ").replace(/^['"]|['"]$/g, "");
    }
  }
  return result.filter(item => item.name);
}
