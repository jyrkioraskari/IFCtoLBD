#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { availableProductCategories, buildTurtle, exportFilename, parseIfc, PRODUCT_CATEGORIES } from "./src/ifc.js";

const root = fileURLToPath(new URL(".", import.meta.url));
const selections = ["floorplan", "spaces", ...Object.keys(PRODUCT_CATEGORIES).map((name) => `products:${name}`)];

function usage() {
  return `Usage: node OneClick/cli.mjs --ifc model.ifc --selection <option> [--output file.ttl | --stdout]\nOptions: ${selections.join(", ")}\n`;
}

function argumentsFrom(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (key === "--help" || key === "-h") return { help: true };
    if (key === "--stdout") { options.stdout = true; continue; }
    if (!["--ifc", "--selection", "--output"].includes(key) || !argv[index + 1]) {
      throw new Error(`Unknown or incomplete argument: ${key}\n${usage()}`);
    }
    options[key.slice(2)] = argv[++index];
  }
  if (!options.ifc || !selections.includes(options.selection)) throw new Error(usage());
  if (options.stdout && options.output) throw new Error("Use --stdout or --output, not both.");
  return options;
}

export function convertOneClick(ifcPath, selection, python = process.env.ONECLICK_PYTHON || "python3") {
  if (!selections.includes(selection)) throw new Error(`Unsupported selection: ${selection}`);
  const source = resolve(ifcPath);
  const model = parseIfc(readFileSync(source, "utf8"), basename(source));
  const extracted = JSON.parse(execFileSync(python, [join(root, "floorplans.py"), source], {
    encoding: "utf8", timeout: 120000, maxBuffer: 100 * 1024 * 1024
  }));
  if (!Array.isArray(extracted.storeys) || !Array.isArray(extracted.spaces) ||
      !Array.isArray(extracted.interfaces) || !Array.isArray(extracted.products)) {
    throw new Error("OneClick geometry extractor returned incomplete data.");
  }
  model.floorplans = extracted.storeys;
  model.spaceGeometry = extracted.spaces;
  model.interfaces = extracted.interfaces;
  model.products = extracted.products;
  if (selection.startsWith("products:") &&
      !availableProductCategories(model.products).has(selection.slice(9))) {
    throw new Error(`No products are available for ${selection}.`);
  }
  return buildTurtle(model, [selection]);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = argumentsFrom(process.argv.slice(2));
    if (args.help) process.stdout.write(usage());
    else {
      const turtle = convertOneClick(args.ifc, args.selection);
      if (args.stdout) process.stdout.write(turtle);
      else {
        const output = resolve(args.output || exportFilename(basename(args.ifc), args.selection));
        writeFileSync(output, turtle, "utf8");
        process.stdout.write(`${output}\n`);
      }
    }
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  }
}
