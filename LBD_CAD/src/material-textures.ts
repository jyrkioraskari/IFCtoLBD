import { DynamicTexture, Texture, type Scene } from "@babylonjs/core";
import { createTimberTexture } from "./timber-texture";
import type { TexturePreset } from "./texture-presets";

/** Small bundled procedural swatches; no external image URLs or licensing dependencies. */
export function createBuiltInTextures(scene: Scene): Record<Exclude<TexturePreset, "none" | "image">, DynamicTexture> {
  const make = (name: string, draw: (context: CanvasRenderingContext2D) => void) => {
    const texture = new DynamicTexture(name, { width: 256, height: 256 }, scene, false);
    draw(texture.getContext() as CanvasRenderingContext2D);
    texture.wrapU = Texture.WRAP_ADDRESSMODE;
    texture.wrapV = Texture.WRAP_ADDRESSMODE;
    texture.update();
    return texture;
  };
  const noise = (x: number, y: number, seed: number) => {
    const value = Math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453;
    return value - Math.floor(value);
  };
  const timber = createTimberTexture(scene);
  timber.wrapU = Texture.WRAP_ADDRESSMODE;
  timber.wrapV = Texture.WRAP_ADDRESSMODE;
  return {
    timber,
    concrete: make("concrete-swatch", context => {
      context.fillStyle = "#e9e9e7"; context.fillRect(0, 0, 256, 256);
      for (let y = 0; y < 256; y += 3) for (let x = 0; x < 256; x += 3) {
        const value = noise(x, y, 1);
        context.fillStyle = value > .73 ? "#b9bbba" : value < .24 ? "#ffffff" : "#d9dbda";
        context.fillRect(x, y, value > .92 ? 3 : 1, value > .92 ? 2 : 1);
      }
    }),
    metal: make("metal-swatch", context => {
      context.fillStyle = "#ededed"; context.fillRect(0, 0, 256, 256);
      for (let y = 0; y < 256; y++) {
        const shade = Math.round(214 + noise(y, 0, 2) * 38);
        context.fillStyle = `rgb(${shade},${shade},${shade})`;
        context.fillRect(0, y, 256, 1);
      }
    }),
    plaster: make("plaster-swatch", context => {
      context.fillStyle = "#f4f2ed"; context.fillRect(0, 0, 256, 256);
      for (let y = 0; y < 256; y += 2) for (let x = 0; x < 256; x += 2) {
        const value = noise(x, y, 3);
        if (value < .72) continue;
        context.fillStyle = value > .94 ? "#d4d1cb" : "#e5e3dd";
        context.fillRect(x, y, 1, 1);
      }
    })
  };
}
