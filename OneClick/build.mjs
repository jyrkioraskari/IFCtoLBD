import { cp, mkdir, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const root = fileURLToPath(new URL(".", import.meta.url));
const output = join(root, "dist");

await rm(output, { recursive: true, force: true });
await mkdir(join(output, "src"), { recursive: true });
await Promise.all([
  cp(join(root, "index.html"), join(output, "index.html")),
  cp(join(root, "styles.css"), join(output, "styles.css")),
  cp(join(root, "server.mjs"), join(output, "server.mjs")),
  cp(join(root, "floorplans.py"), join(output, "floorplans.py")),
  cp(join(root, "cli.mjs"), join(output, "cli.mjs")),
  cp(join(root, "src", "app.js"), join(output, "src", "app.js")),
  cp(join(root, "src", "ifc.js"), join(output, "src", "ifc.js")),
  cp(join(root, "src", "projection.js"), join(output, "src", "projection.js"))
]);

console.log("Built OneClick site and geometry server in dist/");
