/** Small, local SVG icons with a common 24px stroke style. No runtime asset requests. */
const paths: Record<string, string> = {
  upload: '<path d="M12 16V3m0 0 4 4m-4-4L8 7M4 15v4h16v-4"/>',
  orbit: '<circle cx="12" cy="12" r="3"/><path d="M4 12c0-3.5 3.6-6 8-6s8 2.5 8 6-3.6 6-8 6-8-2.5-8-6Z"/><path d="M12 4c3.5 0 6 3.6 6 8s-2.5 8-6 8-6-3.6-6-8 2.5-8 6-8Z"/>',
  walk: '<path d="M13 5a2 2 0 1 0-4 0 2 2 0 0 0 4 0Z"/><path d="m10 8-2 4 3 2 1 6m-2-12 4 3 3-1m-6 4-4 3-2 4m8-1 4 3"/>',
  fly: '<path d="m3 12 18-8-5 16-4-6-6-2Z"/><path d="m12 14 2-3"/>',
  fit: '<path d="M8 3H3v5m13-5h5v5M8 21H3v-5m18 0v5h-5"/>',
  hide: '<path d="M3 3l18 18M10.6 10.7a2 2 0 0 0 2.7 2.7M9.9 5.1A10.8 10.8 0 0 1 12 5c5 0 8.5 4.3 9.5 7- .4 1.1-1.2 2.5-2.4 3.7M6.6 6.6C4.6 8 3.3 10.3 2.5 12c1 2.7 4.5 7 9.5 7 1.2 0 2.3-.2 3.3-.6"/>',
  isolate: '<path d="M4 4h16v16H4z"/><path d="M8 8h8v8H8z"/>',
  show: '<path d="M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7Z"/><circle cx="12" cy="12" r="2.5"/>',
};
export function icon(name: keyof typeof paths): string { return `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${paths[name]}</svg>`; }
