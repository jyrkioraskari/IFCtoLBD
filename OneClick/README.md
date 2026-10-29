# OneClick

A focused web interface for opening an IFC file, choosing one export
scope, previewing the selection, and downloading lightweight Linked Building
Data as Turtle.

The 3D spaces view shows explicit IFC space boundaries, including planar and
linearly extruded surfaces. Some IFC files store boundary coordinates
in building XY coordinates despite declaring them relative to a space. When that
placement would put a surface well outside the spaces, OneClick uses the
coordinates that align with the space geometry.
In the 3D preview, the Spaces and Interfaces buttons show either layer or both.
The Explain this view button describes the layers.

## Run

Requires Node.js 20 or newer, Python 3, IfcOpenShell 0.8.5, and Shapely 2.
Install the Python packages in the Python environment used by the server:

```sh
python3 -m pip install ifcopenshell==0.8.5 'shapely>=2,<3'
```

The server tries `python3`, then `python` on Unix. On Windows it checks active
virtual environments and common installation paths, then tries `py`, `python3`,
and `python`.
Set `ONECLICK_PYTHON` to the full path of the Python executable if it is not
on the server process's `PATH`. Install IfcOpenShell and Shapely with that same
executable. For example:

```sh
ONECLICK_PYTHON=/path/to/python3 npm --prefix OneClick start
```

On Windows, open a **new PowerShell window** after installing Python. First try
`py -3 -c "import sys; print(sys.executable)"` to find the real executable. If
`py` is unavailable, look for `python.exe` under
`$env:LOCALAPPDATA\Programs\Python`, or use the path shown by your Python
installer. Then run, replacing the path with your installation:

```powershell
$env:ONECLICK_PYTHON = 'C:\Users\you\AppData\Local\Programs\Python\Python313\python.exe'
& $env:ONECLICK_PYTHON -m pip install 'ifcopenshell==0.8.5' 'shapely>=2,<3'
npm --prefix OneClick start
```

The Microsoft Store app execution alias can print “Python was not found” even
when another Python is installed. `ONECLICK_PYTHON` bypasses that alias. If
Python really is absent, install a compatible Python 3 release from the
[official Windows downloads](https://www.python.org/downloads/windows/) and
then run the package command above. IfcOpenShell 0.8.5 requires Python 3.10–3.14;
its available Windows wheels are listed on [PyPI](https://pypi.org/project/ifcopenshell/0.8.5/).

If extraction fails, the terminal running the server logs the Python command
and error; the browser console also logs the failed request.
After `npm --prefix OneClick run build`, start the packaged app with
`node OneClick/dist/server.mjs`; geometry extraction requires the OneClick server.

```sh
npm --prefix OneClick start
```

Then open <http://127.0.0.1:4173>. `npm start` serves the source files, so
changes appear without rebuilding. The packaged command above serves `dist/`.
After opening a model, click its file summary to choose another IFC file. A new
selection starts again at Floorplan; cancelling the file picker keeps the current
model and export selection.

```sh
npm --prefix OneClick test
npm --prefix OneClick run check
cd OneClick && python3 -m unittest discover -s test
```

## Command-line export

The source checkout also provides a separate OneClick CLI. It uses the same
IfcOpenShell extractor and Turtle serializer as the web app; the Java
`IFCtoLBDConverter_CLI` and its defaults are unchanged. From the repository root:

```sh
node OneClick/cli.mjs --ifc IFCtoLBD/src/main/resources/Duplex_A.ifc --selection floorplan
node OneClick/cli.mjs --ifc IFCtoLBD/src/main/resources/Duplex_A.ifc --selection products:building --output duplex-building.ttl
```

Without `--output`, the CLI writes a file in the current directory using the
OneClick selection filename, such as `Duplex_A_Floorplan.ttl`. Use `--stdout`
instead to write Turtle to standard output. Available selections are `floorplan`,
`spaces`, `products:building`, `products:hvac`, `products:electrical`,
`products:plumbing`, `products:furnishing`, `products:services`, and
`products:other`. Product categories with no elements in the IFC file fail
instead of writing an empty selection. The CLI requires the same Python,
IfcOpenShell, and Shapely setup as the web server; `ONECLICK_PYTHON` also applies.

The MCP server exposes this workflow separately as `export_oneclick_lbd`.
See [MCP usage](../IFCtoLBD_MCP/README.md#oneclick-export).

## Scope

The browser reads IFC STEP records for the inventory and uploads the selected file
to the OneClick server for IfcOpenShell geometry extraction. The server writes a
temporary file and removes it after extraction. By default, this server binds to
`127.0.0.1`; when OneClick is deployed through a remote host or proxy, the file
travels to that host. The interface makes no device-local privacy guarantee.

For each `IfcBuildingStorey`, the extractor cuts contained walls and columns
horizontally up to 1.2 m above the storey elevation. It emits the resulting
linework as 2D WKT and draws every storey floorplan in a vertical stack on the
right. Door footprints are marked in orange and window footprints in blue;
their XY projections appear even when a window sits above the section plane.
The floorplan display reverses IFC world X, changing the visual left/right order
of doors and windows; Y remains upward. The 2D WKT and Turtle export retain
the original IFC world coordinates. Dragging rotates the 3D space view, and
double-clicking restores its default angle.
Each storey shows its own WKT for copying, with no storey selection required.
The Floorplan Turtle export links each door and window to its storey and gives
each available footprint a separate `geo:asWKT` geometry.
For Spaces & interfaces, the extractor tessellates each `IfcSpace` into a
`POLYHEDRALSURFACE Z` and transforms explicit planar `IfcRelSpaceBoundary`
surfaces into `POLYGON Z`. The right-hand preview shows all spaces and interface
surfaces together in one rotatable, zoomable 3D view. Space faces are green and
interface surfaces are orange. Selecting a space highlights it and reveals its
copyable WKT and the WKT of its interfaces below the view.
The Turtle export links each space and interface to its own `geo:Geometry` and
uses `bot:interfaceOf` to connect the interface to its space, any corresponding
adjacent space, and related building element. Boundaries without supported surface
geometry retain their links in Turtle but are omitted from the geometry list;
spaces without representations likewise have no geometry literal.

For example, this query finds spaces with explicit interface surfaces:

```sparql
PREFIX bot: <https://w3id.org/bot#>
PREFIX geo: <http://www.opengis.net/ont/geosparql#>
SELECT ?space ?interface ?wkt WHERE {
  ?interface a bot:Interface ; bot:interfaceOf ?space ; geo:hasGeometry/geo:asWKT ?wkt .
  ?space a bot:Space .
}
```

For product data, the server reads every non-spatial `IfcProduct`, including
product classes outside the short building-element list. Choose Building elements,
HVAC elements, Electrical elements, Plumbing & piping, Furnishings & equipment,
Other building services, or Other products. Each choice filters both the right-hand
table and the main Turtle export. Categories with no products in the loaded model
are hidden, and the visible choices are numbered consecutively. Classification uses the IFC element class and,
for generic distribution elements in IFC2X3, its assigned IFC type class. Classes
shared by multiple services remain under Other building services. Openings and
uncategorised classes appear under Other products. The right-hand table
can be searched by name, IFC class, type,
storey, material, or IFC GlobalId. Selecting a row shows its description,
predefined type, materials, and property sets and quantities grouped under one
heading per property set, including inherited
type properties, plus the Turtle for that individual element. Its Turtle can be
copied or downloaded as a separate file. The main Export button writes the
selected category to one file. Its filename appends the displayed selection,
with `&` removed and spaces replaced by underscores, for example
`Duplex_A_HVAC_elements.ttl`. The Turtle export
connects products to storeys. Each `oc:hasMaterial` value is an `rdf:JSON` object
with one material name; materials do not have separate resources. Each
`oc:hasPropertySet` value is
directly an [`rdf:JSON`](https://www.w3.org/TR/rdf12-concepts/#section-rdf-JSON)
literal containing one set's name, properties, and quantities. An element with no
sets has no `oc:hasPropertySet` value. The export preserves IFC GlobalIds and source
classes with `ifctolbd:ifcGlobalId` and `ifctolbd:sourceIfcType`.
Each product also has one `oc:hasAttributes` `rdf:JSON` object containing its
non-null direct IFC attributes except `OwnerHistory`, `ObjectPlacement`, and
`Representation`. References to other IFC objects are represented by their IFC
class and GlobalId when available; file-local STEP ids are omitted. The assigned
type name remains available through `oc:typeName`.
Turtle output uses indented predicates and blank lines between resources; the
JSON literals are indented across multiple lines for inspection.

The product export uses an `oc:` vocabulary under the building's own base IRI.
For IFC resources with a valid GlobalId, OneClick uses IFCtoLBD's stable project
identity path, `ifc-globalid/{GlobalId}`, for the site, storeys, spaces,
elements, and IFC space boundaries. The project namespace is
`https://example.org/building/{IfcProject.GlobalId}/` when the IFC project has a
valid GlobalId; otherwise it uses the project-name slug. This keeps the same
storey or element URI across OneClick export selections and project renames.
The `example.org` host is a placeholder: exact URI equality with an IFCtoLBD
conversion requires configuring its stable project identity policy with the
same namespace. Resources without a usable GlobalId use local fallback paths.
Geometry resources use their parent's URI with `_geometry` appended.
Its terms are `oc:hasMaterial`, `oc:hasPropertySet`,
`oc:hasAttributes`, and `oc:typeName`. Property values retain JSON string,
number, Boolean, and null types inside the literal. For example, replace
`your-project` with the project identifier in the exported `@base` to retrieve each
element's named property-set JSON:

```sparql
PREFIX bot: <https://w3id.org/bot#>
PREFIX oc: <https://example.org/building/your-project/vocab/>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
SELECT ?product ?setJson WHERE {
  ?product a bot:Element ; oc:hasPropertySet ?setJson .
  FILTER(DATATYPE(?setJson) = rdf:JSON)
}
```

Replace `oc:hasPropertySet` with `oc:hasMaterial` and `?setJson` with
`?materialJson` in the query to retrieve the material JSON values.

If extraction fails, the interface shows the error and blocks geometry export
for Floorplan or Spaces & interfaces until another scope is selected.
Coordinates are local engineering coordinates in metres;
the Turtle uses a per-building local CRS IRI. A storey with no intersecting geometry
has no `geo:asWKT` value. The prototype still does not replace the full Java
IFCtoLBD conversion pipeline or validation.
