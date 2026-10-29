import { createReadStream, createWriteStream, existsSync, readdirSync } from "node:fs";
import { mkdtemp, rm } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { spawn } from "node:child_process";
import { pipeline } from "node:stream/promises";
import { fileURLToPath } from "node:url";

const projectRoot = fileURLToPath(new URL(".", import.meta.url));
const root = projectRoot;
const port = Number.parseInt(process.env.PORT || "4173", 10);
const types = new Map([
  [".html", "text/html; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".svg", "image/svg+xml"],
  [".json", "application/json; charset=utf-8"]
]);

const server = createServer(async (request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
  if (pathname === "/api/floorplans" || pathname === "/api/geometry") {
    if (request.method !== "POST") {
      sendJson(response, 405, { error: "Use POST with an IFC file." });
      return;
    }
    const directory = await mkdtemp(join(tmpdir(), "oneclick-"));
    try {
      const path = join(directory, "model.ifc");
      let bytes = 0;
      request.on("data", (chunk) => {
        bytes += chunk.length;
        if (bytes > 100 * 1024 * 1024) request.destroy(new Error("IFC file exceeds 100 MB."));
      });
      await pipeline(request, createWriteStream(path));
      const result = await runGeometry(path);
      sendJson(response, 200, result);
    } catch (error) {
      console.error("OneClick geometry extraction failed:", error);
      if (!response.destroyed) sendJson(response, error.code === "PYTHON_UNAVAILABLE" ? 503 : 422, { error: error.message });
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
    return;
  }
  const relative = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  const candidate = normalize(join(root, relative));

  if (!candidate.startsWith(root) || !existsSync(candidate)) {
    response.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
    response.end("Not found");
    return;
  }

  response.writeHead(200, {
    "content-type": types.get(extname(candidate)) || "application/octet-stream",
    "cache-control": "no-store"
  });
  createReadStream(candidate).pipe(response);
});

function sendJson(response, status, value) {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(value));
}

async function runGeometry(path) {
  const configured = process.env.ONECLICK_PYTHON;
  const candidates = configured ? [configured] : process.platform === "win32"
    ? [...new Set([...windowsPythonPaths(), "py", "python3", "python"])] : ["python3", "python"];
  let missingDependency;
  for (const executable of candidates) {
    try {
      console.info(`OneClick geometry: trying Python executable ${executable}`);
      return await runWithPython(executable, path);
    } catch (error) {
      if (error.code === "ENOENT" || isWindowsPythonAlias(error)) {
        console.warn(`OneClick geometry: ${executable} is unavailable: ${error.message}`);
        continue;
      }
      const missingModule = error.message.match(/No module named ['"]([^'"]+)['"]/);
      if (missingModule) {
        missingDependency = new Error(`Python ${executable} is missing ${missingModule[1]}. Install IfcOpenShell 0.8.5 and Shapely 2 into that Python environment; see OneClick/README.md.`);
        if (configured) throw missingDependency;
        console.warn(`OneClick geometry: ${missingDependency.message}`);
        continue;
      }
      throw error;
    }
  }
  if (missingDependency) throw missingDependency;
  const error = new Error(`A usable Python 3 executable was not found. Tried ${candidates.join(", ")}. Set ONECLICK_PYTHON to the full path of your installed python.exe. If Python is not installed, install Python 3 and restart the server.`);
  error.code = "PYTHON_UNAVAILABLE";
  throw error;
}

function isWindowsPythonAlias(error) {
  return process.platform === "win32" && /Python was not found; run without arguments to install from the Microsoft Store/i.test(error.message);
}

function windowsPythonPaths() {
  const paths = [];
  for (const environment of ["VIRTUAL_ENV", "CONDA_PREFIX"]) {
    if (!process.env[environment]) continue;
    const executable = join(process.env[environment], "Scripts", "python.exe");
    const direct = join(process.env[environment], "python.exe");
    if (existsSync(executable)) paths.push(executable);
    else if (existsSync(direct)) paths.push(direct);
  }
  if (process.env.LOCALAPPDATA) {
    for (const name of ["py.exe", "python.exe"]) {
      const executable = join(process.env.LOCALAPPDATA, "Python", "bin", name);
      if (existsSync(executable)) paths.push(executable);
    }
  }
  const roots = [
    process.env.LOCALAPPDATA && join(process.env.LOCALAPPDATA, "Programs", "Python"),
    process.env.PROGRAMFILES,
    process.env["PROGRAMFILES(X86)"]
  ].filter(Boolean);
  for (const root of roots) {
    if (!existsSync(root)) continue;
    let entries;
    try { entries = readdirSync(root, { withFileTypes: true }); }
    catch { continue; }
    for (const entry of entries) {
      if (!entry.isDirectory() || !/^Python\s*3\d+(?:\.\d+)?$/i.test(entry.name)) continue;
      const executable = join(root, entry.name, "python.exe");
      if (existsSync(executable)) paths.push(executable);
    }
  }
  for (const name of ["miniforge3", "miniconda3", "anaconda3"]) {
    if (!process.env.USERPROFILE) continue;
    const executable = join(process.env.USERPROFILE, name, "python.exe");
    if (existsSync(executable)) paths.push(executable);
  }
  return paths;
}

function runWithPython(executable, path) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, [join(projectRoot, "floorplans.py"), path]);
    let output = "";
    let errors = "";
    const timer = setTimeout(() => child.kill(), 120000);
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (part) => {
      output += part;
      if (output.length > 20_000_000) child.kill();
    });
    child.stderr.on("data", (part) => { errors += part.slice(0, 2000); });
    child.on("error", reject);
    child.on("close", (code) => {
      clearTimeout(timer);
      try {
        if (code !== 0) throw new Error(errors.trim() || output.trim() || "IfcOpenShell could not extract geometry.");
        resolve(JSON.parse(output));
      } catch (error) { reject(error); }
    });
  });
}

server.listen(port, "127.0.0.1", () => {
  console.log(`OneClick is ready at http://127.0.0.1:${port}`);
});
