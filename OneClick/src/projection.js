export function projectWorldPoint([x, y, z], center, yaw, pitch) {
  const dx = x - center[0], dy = y - center[1], dz = z - center[2];
  const cosine = Math.cos(yaw), sine = Math.sin(yaw);
  const tilt = Math.cos(pitch), rise = Math.sin(pitch);
  const horizontal = dx * cosine - dy * sine;
  const depth = dx * sine + dy * cosine;
  return [horizontal, -depth * rise - dz * tilt, depth * tilt - dz * rise];
}

export function projectPlanPoint([x, y], maxX, maxY, scale, left, top) {
  return [left + (maxX - x) * scale, top + (maxY - y) * scale];
}
