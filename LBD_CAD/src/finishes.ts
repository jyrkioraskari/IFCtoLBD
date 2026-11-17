export const finishes = {
  plaster: { label: "Warm plaster", color: "#e8dfd1", specular: "#24333a", power: 20, alpha: 1 },
  concrete: { label: "Concrete", color: "#aebdc1", specular: "#2b3a41", power: 28, alpha: 1 },
  timber: { label: "Timber", color: "#bd9064", specular: "#342820", power: 18, alpha: 1 },
  glass: { label: "Glass", color: "#70bdca", specular: "#d0f3fa", power: 110, alpha: .57 },
  metal: { label: "Metal", color: "#617984", specular: "#c4d8de", power: 96, alpha: 1 },
  paint: { label: "Paint", color: "#85b8aa", specular: "#526e69", power: 48, alpha: 1 }
} as const;

export type Finish = keyof typeof finishes;

export function defaultFinish(kind: string): Finish {
  if (kind === "Wall") return "plaster";
  if (kind === "Slab" || kind === "Space" || kind === "Opening") return "concrete";
  if (kind === "Door") return "timber";
  if (kind === "Window") return "glass";
  if (kind === "Face") return "paint";
  return "metal";
}

export function contrastEdge(hex: string): string {
  const channels = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4);
  const luminance = channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
  return luminance > .33 ? "#1c3944" : "#e3f3f4";
}
