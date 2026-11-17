export interface GroundPoint { x: number; z: number }
export interface Rectangle { x: number; z: number; width: number; depth: number }

export function rectangleFromCorners(first: GroundPoint, second: GroundPoint): Rectangle {
  const width = Math.abs(second.x - first.x), depth = Math.abs(second.z - first.z);
  if (width < .001 || depth < .001) throw new Error("Rectangle needs two different corners");
  return { x: (first.x + second.x) / 2, z: (first.z + second.z) / 2, width, depth };
}

/** Accept measured width/depth such as "4,3", "4;3" or "4 x 3" metres. */
export function parseRectangleDimensions(text: string): [number, number] | undefined {
  const match = text.trim().match(/^([+]?(?:\d+(?:\.\d*)?|\.\d+))\s*(?:,|;|x|×)\s*([+]?(?:\d+(?:\.\d*)?|\.\d+))\s*(?:m)?$/i);
  if (!match) return;
  const width = Number(match[1]), depth = Number(match[2]);
  return width >= .001 && depth >= .001 ? [width, depth] : undefined;
}

export function parseLength(text: string): number | undefined {
  const match = text.trim().match(/^([+]?(?:\d+(?:\.\d*)?|\.\d+))\s*(?:m)?$/i);
  if (!match) return;
  const length = Number(match[1]);
  return length >= .001 ? length : undefined;
}

/** Absolute X,Z coordinates in metres, including negative coordinates. */
export function parseCoordinates(text: string): [number, number] | undefined {
  const signed = "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))";
  const match = text.trim().match(new RegExp(`^${signed}\\s*(?:,|;|x|×)\\s*${signed}\\s*(?:m)?$`, "i"));
  return match ? [Number(match[1]), Number(match[2])] : undefined;
}

export function parseDegrees(text: string): number | undefined {
  const match = text.trim().match(/^([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*(?:°|deg|degrees)?$/i);
  return match ? Number(match[1]) : undefined;
}
