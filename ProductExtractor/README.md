# ProductExtractor

ProductExtractor reads [IFCtoLBD](../README.md) Linked Building Data (LBD)
Turtle output and writes a **JSON array with one summary per building element**.
Each summary collects:

- **IFC attributes** — GlobalId, Tag (`batid`), ObjectType, and the other IFC
  root/object/occurrence attributes the converter exports;
- **property sets and values** — the element's IFC property sets grouped by
  name, in every property representation the converter can emit (level 1
  flattened predicates, OPM levels 2/3, and bSDD-typed property sets);
- **materials and material layers** — the material assignments produced by the
  opt-in [`materials-bmo`](../docs/materials-bmo.md) profile, including direct
  materials, layer sets with ordered layers, thicknesses and units, and
  occurrence-specific layer-set usage.

The tool is a small standalone Python project (like `IFCtoLBD_Python`). It is
not part of the Maven reactor.

## Install

```sh
pip install -e .
```

This installs the `product-extractor` command and its only runtime dependency,
[rdflib](https://rdflib.readthedocs.io/). To run the tests:

```sh
pip install -e .[test]
pytest
```

### On Windows

These steps use PowerShell (the default shell on Windows 10/11). Where a step
differs in the classic Command Prompt (`cmd.exe`), it is noted.

1. **Install Python 3.10 or newer** from <https://www.python.org/downloads/windows/>.
   In the installer, tick **Add python.exe to PATH** (bottom of the first
   screen). Verify in a *new* PowerShell window:

   ```powershell
   python --version
   ```

   If `python` is not recognised, re-run the installer with the PATH option
   ticked, or use the `py` launcher instead (`py --version`; see
   [Windows tips](#windows-tips) below).

2. **Go to the ProductExtractor folder** and create a virtual environment
   (recommended — it keeps the install isolated):

   ```powershell
   cd "$HOME\Documents\IFCtoLBD\ProductExtractor"   # wherever you cloned the repo
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   ```

   If activation fails with *"execution of scripts is disabled"*, allow scripts
   for your user account once, then activate again:

   ```powershell
   Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
   .\.venv\Scripts\Activate.ps1
   ```

   In `cmd.exe`, activate with `.venv\Scripts\activate.bat` instead. A
   `(.venv)` prefix in the prompt means the virtual environment is active.

3. **Install ProductExtractor** (with the virtual environment active):

   ```powershell
   python -m pip install -e .
   ```

   Using `python -m pip` avoids the "pip is not recognised" problem. To also
   install the test runner:

   ```powershell
   python -m pip install -e ".[test]"
   ```

   (Quote `".[test]"` in PowerShell — the brackets are wildcard characters.)

4. **Run it.** The installer adds a `product-extractor.exe` command to the
   virtual environment's `Scripts` folder, which is on PATH while the virtual
   environment is active:

   ```powershell
   product-extractor --input "C:\models\duplex.ttl" --output "C:\models\elements.json"
   product-extractor --input "C:\models\duplex.ttl" --types Wall,Slab --output "C:\models\walls.json"
   product-extractor --input "C:\models\duplex.ttl" --list-types
   ```

   If Windows reports that `product-extractor` is not recognised, call the
   module directly — this always works and needs no PATH changes:

   ```powershell
   python -m product_extractor --input "C:\models\duplex.ttl" --output "C:\models\elements.json"
   ```

   Quote paths that contain spaces (as above). Forward slashes work too, so
   `C:/models/duplex.ttl` is fine.

5. **Web interface:**

   ```powershell
   product-extractor --web --input "C:\models\duplex.ttl" --port 8000
   ```

   Then open <http://127.0.0.1:8000/> in Edge, Chrome, or Firefox. If Windows
   Defender Firewall asks whether to allow Python, choose **Allow** for
   *private* networks — the server only listens on your own machine by
   default. Stop it with Ctrl+C in the PowerShell window.

6. **Run the tests:**

   ```powershell
   python -m pytest
   ```

> ProductExtractor itself needs only Python. To produce the input `.ttl` from
> an IFC file you also need the IFCtoLBD converter, which needs Java 21 — see
> [Producing the input](#producing-the-input) and
> [docs/quick-start.md](../docs/quick-start.md). The `java -cp ...` command
> works the same on Windows; just use Windows paths.

### Windows tips

- **Several Python versions installed:** use the `py` launcher to pick one,
  e.g. `py -3.12 -m venv .venv` or `py -3.12 -m pip install -e .`.
- **`python` opens the Microsoft Store:** disable the *App execution aliases*
  for `python` and `python3` in Windows Settings → Apps → App execution
  aliases, or use the `py` launcher as shown above.
- **No virtual environment:** install into your user account with
  `python -m pip install --user -e .`. The `product-extractor.exe` command then
  lands in `%LOCALAPPDATA%\Python\Python3x\Scripts` (the `3x` depends on your
  Python version) — add that folder to PATH, or keep using
  `python -m product_extractor`.

## Usage

```sh
# Summarise every building element in the file
product-extractor --input model.ttl

# Write to a chosen file
product-extractor -i model.ttl -o elements.json

# Only walls and slabs (types match rdf:type local names, case-insensitive)
product-extractor -i model.ttl -o walls.json --types Wall,Slab
product-extractor -i model.ttl -o doors.json -t beo:Door

# Discover which types a file contains
product-extractor -i model.ttl --list-types

# Print compact JSON to standard output
product-extractor -i model.ttl -o - --compact
```

The same commands work with `python -m product_extractor ...`. The input may be
a merged Turtle file (`.ttl`) or a TriG/N-Quads file (`.trig`, `.nq`); named
graphs are merged before extraction. Without `--output`, the result is written
to `<input name>-elements.json` next to the input file.

`--types` accepts plain local names (`Wall`), prefixed names (`beo:Wall`), and
full IRIs; matching is case-insensitive. Elements are resources typed
`bot:Element`; spatial resources (sites, buildings, storeys, spaces) are never
included. `Slab-FLOOR` and other predefined types can be filtered directly.

### Producing the input

Convert an IFC model with IFCtoLBD as usual, for example:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --url https://example.com/building/ \
  --target_file model.ttl \
  --profile materials-bmo \
  model.ifc
```

The `materials-bmo` profile adds the material assignments and layer sets that
the summary's `materials` section reports; see
[docs/materials-bmo.md](../docs/materials-bmo.md). Turtle is the merged data
view; pass `--hasTriG` and a `.trig` target to keep the named graphs, which
ProductExtractor also accepts.

## Web interface

For interactive use, start the local web interface:

```sh
product-extractor --web --input model.ttl --port 8000
# or without a file, and load it in the browser:
product-extractor --web
```

Then open <http://127.0.0.1:8000/>. The page lets you:

- load an IFCtoLBD Turtle/TriG file from disk (or use the file given with
  `--input`),
- tick the element types to include (with occurrence counts; no selection
  means all building elements),
- run the extraction and preview the summaries in a table — click a row to
  inspect the full JSON of one element,
- download the summaries as a JSON file (the same array the CLI writes).

The graph is parsed once and kept in memory, so changing the type selection
and re-extracting is fast. The server uses only the Python standard library,
binds to `127.0.0.1` by default (use `--host 0.0.0.0` to expose it on the
network — it is a local tool, not a hardened public service), and stops with
Ctrl+C.

The HTTP API behind the page, if you want to script against it:

| Endpoint | Description |
| --- | --- |
| `GET /api/info` | element count, per-type counts, material coverage |
| `GET /api/extract?types=Wall,Slab` | element summaries as a JSON array |
| `POST /api/extract` | same, body `{"types": ["Wall", "Slab"]}` |
| `POST /api/load` | replace the loaded graph with uploaded Turtle/TriG text (`X-Source-Name` header carries the file name) |

## Output format

The output file is a JSON array, one object per element, sorted by element IRI:

```json
[
  {
    "iri": "https://example.com/building/wall-1",
    "label": "Basic Wall:Exterior:1",
    "types": [
      "https://pi.pauwel.be/voc/buildingelement#Wall",
      "https://w3id.org/bot#Element"
    ],
    "ifcType": "IfcWallStandardCase",
    "attributes": {
      "globalIdIfcRoot": "GUID-WALL-1",
      "batid": "1",
      "objectTypeIfcObject": "Basic Wall:Exterior"
    },
    "propertySets": [
      {
        "name": "Pset_WallCommon",
        "properties": { "isExternal": true, "loadBearing": false }
      }
    ],
    "materials": [
      {
        "iri": "https://example.com/building/assignment-1",
        "inherited": false,
        "sourceIfcType": null,
        "sourceIfcTypeResource": null,
        "material": null,
        "layerSet": {
          "iri": "https://example.com/building/layer-set-1",
          "name": "Ordered wall set",
          "description": "Two ordered layers",
          "layers": [
            {
              "position": 1,
              "iri": "https://example.com/building/layer-1",
              "material": {
                "iri": "https://example.com/building/material-brick",
                "name": "Brick",
                "description": "Fired clay",
                "category": "Masonry"
              },
              "thickness": 0.1,
              "thicknessUnit": "http://qudt.org/vocab/unit/M",
              "thicknessSourceValue": 100.0
            }
          ]
        },
        "usage": {
          "iri": "https://example.com/building/usage-1",
          "layerSetDirection": "AXIS2",
          "directionSense": "POSITIVE",
          "offsetFromReferenceLine": 12.5
        }
      }
    ]
  }
]
```

Notes on the mapping:

- `attributes` keys are the IFC attribute names exactly as the converter
  normalises them (`globalIdIfcRoot`, `batid`, `objectTypeIfcObject`,
  `overallHeightIfcDoor`, ...). The element name appears as `label`
  (`rdfs:label` in the graph). Values keep their literal types: booleans,
  numbers, and strings.
- `ifcType` is read from the element's `owl:sameAs` IFC OWL link, either from
  the linked resource's `ifc:` type or from its local name
  (`IfcWall_13698` → `IfcWall`). It is `null` when the output carries no IFC
  OWL link.
- `propertySets` groups properties by IFC property-set name. At property
  level 1 the converter shares one predicate per property name across all
  property sets that recognise it, so a value can appear under each candidate
  property set; OPM levels 2/3 and bSDD property-set resources carry exact
  `Pset:Property` labels and are grouped precisely.
- `materials` lists the element's `ifctolbd:materialAssignment` resources.
  `material` is set for a direct `IfcMaterial` assignment (also reached through
  a `bmo:MaterialDefinition`), `layerSet` for an `IfcMaterialLayerSet`
  assignment, and `usage` for occurrence-specific `IfcMaterialLayerSetUsage`
  metadata. Layer positions are one-based, matching IFC list order; thicknesses
  are the metre-normalised `bmo:hasThickness` values, with the original IFC
  value kept in `thicknessSourceValue` when the converter recorded it.
- Elements without materials (or in files converted without the
  `materials-bmo` profile) get an empty `materials` array.

## Library use

```python
from product_extractor import summarize_file

summaries = summarize_file("model.ttl", type_filter="Wall,Slab")
```

`summarize_file` returns plain Python data (dicts/lists) that serialises
directly with `json.dumps`. `load_graph`, `summarize_graph`,
`element_type_counts`, and `parse_type_filter` are also exported for finer
control.

## Tests

```sh
pytest
```

The suite covers element discovery, attribute and property extraction in all
converter representations, material/layer summaries, type filtering, TriG
merging, CLI behaviour, the web API (info, extraction, filtering, upload), and
a run against the bundled `IFCtoLBD_MCP/demo/output/duplex-demo.ttl` from this
repository.
