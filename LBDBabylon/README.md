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
```

`omg:hasGeometry` is `https://w3id.org/omg#hasGeometry`, `fog:asObj_v3.0-obj` is `https://w3id.org/fog#asObj_v3.0-obj`, and the artifact namespace is `https://w3id.org/ifctolbd/geometry#`. Embedded payloads are decoded locally; external artifact URLs are never fetched and are reported as unavailable.

When IFCtoLBD emits `https://lbd.org/#asMTL_kd` and `asMTL_alpha` on the geometry resource, the viewer uses them as the Babylon diffuse colour and opacity. Meshes without these converter-specific material values use the default blue material.

IFCtoLBD currently writes OBJ coordinates in IFC's X/Y horizontal, Z-up frame. The viewer applies `(x, y, z) → (x, z, -y)` once when creating Babylon vertices; it assumes IFCtoLBD's geometry is already in metres. It does not render WKT bounding boxes, `lbd:hasBoundingBox`, arbitrary FOG formats, GLB, or glTF. GLB/glTF files can be selected for future artifact workflows, but no current IFCtoLBD RDF binding associates them with elements, so they are deliberately not imported.

To generate candidate input, run the converter with geometry enabled (`--hasGeometry`) so it produces `fog:asObj_v3.0-obj`; enable the converter's geometry-artifact output when using a controlled companion OBJ directory. BOT topology alone does not create geometry.

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
