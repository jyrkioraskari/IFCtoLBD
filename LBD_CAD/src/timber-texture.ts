import { DynamicTexture, type Scene } from "@babylonjs/core";

/** A small deterministic grain for editable CAD primitives with box UVs. */
export function createTimberTexture(scene: Scene): DynamicTexture {
  const texture = new DynamicTexture("timber-grain", { width: 256, height: 256 }, scene, false);
  const context = texture.getContext();
  context.fillStyle = "#f7eee1";
  context.fillRect(0, 0, 256, 256);
  for (let line = 0; line < 58; line++) {
    const y = line * 4.5;
    context.beginPath();
    for (let x = 0; x <= 256; x += 4) {
      const bend = Math.sin(x * .034 + line * .61) * 1.3 + Math.sin(x * .083 + line * .19) * .5;
      if (x === 0) context.moveTo(x, y + bend);
      else context.lineTo(x, y + bend);
    }
    context.strokeStyle = line % 7 === 0 ? "#a88363" : "#d5b99d";
    context.lineWidth = line % 7 === 0 ? 1.2 : .55;
    context.stroke();
  }
  // A few quiet rings suggest natural grain without a repeating high-contrast pattern.
  for (const [x, y, width] of [[67, 72, 20], [194, 191, 15]] as const) {
    for (let ring = 0; ring < 3; ring++) {
      context.save();
      context.translate(x, y);
      context.scale(1, (3 + ring * 3) / (width + ring * 5));
      context.beginPath();
      context.arc(0, 0, width + ring * 5, 0, Math.PI * 2);
      context.strokeStyle = ring === 0 ? "#ad8969" : "#cfb297";
      context.lineWidth = .8;
      context.stroke();
      context.restore();
    }
  }
  texture.update();
  return texture;
}
