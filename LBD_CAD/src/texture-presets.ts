import type { Finish } from "./finishes";

export const texturePresets = ["none", "timber", "concrete", "metal", "plaster", "image"] as const;
export type TexturePreset = typeof texturePresets[number];
export const textureLabels: Record<TexturePreset, string> = {
  none: "Solid color", timber: "Wood grain", concrete: "Concrete", metal: "Brushed metal", plaster: "Fine plaster",
  image: "Imported image"
};
export function defaultTexture(finish: Finish): TexturePreset {
  return finish === "timber" || finish === "concrete" || finish === "metal" || finish === "plaster" ? finish : "none";
}
export function initialElementTexture(finish: Finish, imported: boolean): TexturePreset {
  return imported ? "none" : defaultTexture(finish);
}
export function defaultTile(finish: Finish): [number, number] {
  if (finish === "timber") return [1.2, .2];
  if (finish === "concrete") return [1, 1];
  if (finish === "metal") return [.5, .5];
  return [.5, .5];
}
