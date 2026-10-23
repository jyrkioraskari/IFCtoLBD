# LBD Babylon Viewer

Browser-local LBD viewer for Turtle produced by this repository. It keeps RDF terms in an N3 RDF/JS store, renders the geometry IFCtoLBD actually emits, and links every rendered mesh back to the originating element URI.

## Run

```sh
npm install
npm run dev
npm run build
npm test
```

Open the local Vite URL, choose **Upload LBD Turtle file**, or drop a `.ttl` file on the viewport.
For RDF that links external OBJ, GLB, or self-contained glTF artifacts, choose **Select local geometry package** and select the package
directory. Relative paths and file names are resolved locally; `..` traversal and Windows-style separators
are rejected. Selecting files is an explicit user action and preserves the RDF element-to-mesh association.

## Supported IFCtoLBD geometry

The supported, verified graph paths are:

```turtle
element omg:hasGeometry geometry .
geometry fog:asObj_v3.0-obj "...base64 OBJ..."^^xsd:base64Binary .
geometry <https://lbd.org/#asMTL_kd> "#rrggbb" ;
         <https://lbd.org/#asMTL_alpha> 0.0 .

# geometry-artifact mode
geometry ifctolbd-geometry:artifact <asset-uri> .
<asset-uri> ifctolbd-geometry:mediaType "model/obj" .
# Also supported: "model/gltf-binary" and "model/gltf+json".
```

`omg:hasGeometry` is `https://w3id.org/omg#hasGeometry`, `fog:asObj_v3.0-obj` is `https://w3id.org/fog#asObj_v3.0-obj`, and the artifact namespace is `https://w3id.org/ifctolbd/geometry#`. Embedded payloads are decoded locally. External artifacts are loaded from explicitly selected local OBJ, GLB, or self-contained glTF files or packages. Automatic remote fetching is disabled. **Load linked remote geometry** performs an explicit HTTP(S) request without credentials and reports HTTP, CORS, and unsupported-content failures; no other URI scheme is fetched.

When IFCtoLBD emits `https://lbd.org/#asMTL_kd` and `asMTL_alpha` on the geometry resource, the viewer uses them as the Babylon diffuse colour and opacity. Meshes without these converter-specific material values use the default blue material.

IFCtoLBD writes OBJ coordinates in IFC's X/Y horizontal, Z-up local-engineering
frame and now publishes their source unit metadata. Before creating any GPU
buffer, the viewer parses all loaded OBJ payloads, selects one shared model
origin from their combined bounds, and subtracts it from every vertex. It then
applies `(x, y, z) → (x, z, -y)` exactly once. This render rebasing preserves
small detail at large map-like coordinates without changing distances or the
relative placement of separate elements. It never centers meshes individually.
The shared IFC origin and source coordinate-space/unit metadata are retained on
each Babylon mesh under `metadata.ifcToLbd`; `Viewer.renderOriginIfc` exposes the
same origin for navigation and downstream coordinate display.

The viewer does not perform map conversion, datum shifts, reprojection, or a
second unit conversion. It renders the numeric geometry contract it receives.
It does not render WKT bounding boxes, `lbd:hasBoundingBox`, or arbitrary FOG formats. External `.gltf`
must be self-contained; sibling `.bin` buffers and texture files are not resolved from a selected package.

To generate candidate input, run the converter with geometry enabled (`--hasGeometry`) so it produces `fog:asObj_v3.0-obj`; use the `geometry-external` profile and an artifact format when using a controlled companion OBJ/GLB/glTF directory. BOT topology alone does not create geometry.

## Controls

- **Orbit** (default): mouse orbit, wheel zoom.
- **Walk**: click it (a user gesture activates pointer lock), then mouse-look and WASD. Escape releases pointer lock.
- **Fly**: mouse-look, WASD, `E` up, `Q` down.
- **Fit**, selection highlighting, hide, isolate, and show-all are in the viewport toolbar.

Invisible meshes have collision disabled as well, so filters cannot create invisible barriers. Walk uses Babylon mesh collision with a 0.32 m radius, 1.7 m eye height and gravity. It selects the lowest collision mesh as a start floor and recovers after a large fall. If no collision mesh exists it remains in orbit/fly and explains why. This is basic collision walking: no robust navmesh, floor classification, or verified stair step controller is implemented, so complex stairs should currently use Fly.

## RDF interaction and limits

Clicking a mesh selects and highlights its RDF element URI, shown in the status area. The compact viewer layout intentionally omits the entity/property browser; Turtle data is never executed as HTML.

The viewer builds mesh/entity indexes once at import time and does no RDF lookup each frame. It is intended for modest local models: large embedded base64 OBJ payloads duplicate data in memory while Turtle and OBJ are parsed. Replace a model by uploading another Turtle; its scene and GPU resources are disposed first.

Fixtures cover embedded OBJ discovery/identity, semantic-only files, malformed Turtle, and unsupported/no geometry. `fixtures/supported.ttl` is a small working example.
