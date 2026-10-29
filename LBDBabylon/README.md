# LBD Babylon Viewer

Browser-local LBD viewer for Turtle produced by this repository. It keeps RDF terms in an N3 RDF/JS store, renders the geometry IFCtoLBD actually emits, and links every rendered mesh back to the originating element URI.

## Run

Requires Node.js 20 or newer.

```sh
npm ci
npm run dev
npm run build
npm test
```

Open the local Vite URL, choose **Upload LBD Turtle file**, or drop a `.ttl` file on the viewport.
HTTP(S) OBJ, GLB, and self-contained glTF artifacts linked by the Turtle are loaded automatically.

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

`omg:hasGeometry` is `https://w3id.org/omg#hasGeometry`, `fog:asObj_v3.0-obj` is `https://w3id.org/fog#asObj_v3.0-obj`, and the artifact namespace is `https://w3id.org/ifctolbd/geometry#`. Embedded payloads are decoded locally. HTTP(S) artifacts linked by the loaded Turtle are fetched automatically without credentials; HTTP, CORS, and unsupported-content failures are reported while other available geometry still renders. No other URI scheme is fetched.

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

The viewer does not perform map conversion, datum shifts, or reprojection. OBJ
coordinates are normalized to metres from `objUnitToMetres` (falling back to
`unitToMetres`, then metre units when metadata is absent) after subtracting the
shared double-precision render origin. glTF/GLB retains its specification-defined metre
unit. This makes navigation body sizes, speeds, and step heights independent of
the IFC project's authored unit.
It does not render WKT bounding boxes, `lbd:hasBoundingBox`, or arbitrary FOG formats. External `.gltf`
must be self-contained; sibling `.bin` buffers and texture files are not resolved from a selected package.

To generate candidate input, run the converter with geometry enabled (`--hasGeometry`) so it produces `fog:asObj_v3.0-obj`; use the `geometry-external` profile and an artifact format when using a controlled companion OBJ/GLB/glTF directory. BOT topology alone does not create geometry.

## Controls

- **Orbit** (default): mouse orbit, wheel zoom.
- **Walk**: on first use, places the avatar on the grass-green exterior ground nearest the current camera projection and shows it with an elevated rear-follow camera. Later activations resume its last position and heading. Use WASD; Up/Down move forward/back and Left/Right turn the heading. Hold Shift for a fast walk and press `R` to return to the most recent safe position. Dragging turns the camera and avatar heading together, and the wheel adjusts the follow distance.
- **Fly**: mouse-look, WASD, `E` up, `Q` down.
- **Only near objects**: keeps complete RDF objects within 2 m at their normal visibility and makes farther objects more transparent.
- **Object information**: click or tap rendered geometry to highlight its RDF element and open a card containing its LBD label, type, URI, attributes, and linked-resource labels.
- **Assisted walk**: double-click a floor, slab, stair, or ramp. WASD immediately cancels the destination and returns full control.
- **Fit**, selection highlighting, hide, isolate, and show-all are in the viewport toolbar.

Rendered RDF elements retain their imported mesh visibility and material alpha,
so opaque floors correctly hide the lower virtual ground and furniture participates
normally in depth ordering. Element materials render both front and back faces,
including imported glTF multi-materials. In Walk mode the avatar shares the model's
normal depth buffer. A wall or other opaque element between the camera and avatar is
therefore rendered in front of the avatar and hides the obstructed part of the figure.
Wall materials use 90% opacity (10% transparency); materials that were already more
transparent retain their original opacity. `IfcSpace`, BOT `Space`, and
`IfcOpeningElement` geometry is excluded from the 3D scene because these resources
describe semantic volumes and voids; the RDF entities remain available in search and
object data. The exclusion checks both RDF types and the IFC class retained in geometry
material metadata, covering converter output where an opening's RDF identity is absent.

Invisible meshes have collision disabled as well, so filters cannot create invisible barriers. Collision is semantically allowlisted: physical walls, slabs, columns, beams, roofs, doors, windows, stairs, ramps, railings, and furniture can block movement; spaces, openings, voids, zones, storeys, sites, buildings, annotations, virtual elements, and unknown helper geometry never do. Floors, slabs, ramps, stairs, landings, and floor coverings are walkable surfaces. Touching an `IfcDoor` swings its geometry open and disables its collision for the rest of the loaded scene.
Concrete IFC classes are resolved from both direct RDF types and IFCtoLBD's
`owl:sameAs` instance links, so generic `bot:Element` output retains the correct
collision, walkability, door, space, and opening behavior.

On contact with any other blocking object, Babylon resolves the body at the contact
position and slides along the surface when possible. When opposing faces in thin,
detailed BIM geometry trap Babylon's ellipsoid at contact, reversing briefly excludes
only the last collider and adds 6 cm of separation; every other collider remains active.
Collision-contact positions are not recorded as safe recovery points.

Walk uses a separate 0.24 m radius (0.48 m diameter), 1.75 m high body with a smoothed 1.65 m eye camera. The narrower body approximates shoulder width and leaves practical clearance in common 0.80-0.90 m door openings. Its initial 2.3 m camera radius keeps the full avatar in view from behind. Switching among Orbit, Walk, and Fly preserves the viewing direction. Held Up/Down input accelerates progressively from rest; releasing movement input stops immediately and resets velocity to zero. Movement also uses gravity, ground snapping, native collision sliding, pitch limits, safe-position recovery, and a clamped frame delta. One continuous, opaque ground mesh with subtle grass-green color variation extends at least 8 m beyond the building. Its entry level is checked against the exterior door bottoms and the nearest walkable floor top; a mismatch is reported in the console. Without exterior doors it uses the lowest recognized walkable level with a 5 mm rendering offset. The plane is visual only; walking support is limited to the exterior and ground-level doorways so the plane cannot stop descent on interior stairs. It is not an RDF entity and is excluded from model selection and camera fitting.
When no rendered element has recognizable floor semantics, the ground remains available at the predominant mesh base elevation instead of dropping to an isolated footing or foundation underside.

Walk mode renders a neutral avatar assembled from local Babylon primitives. It
adds no downloaded model, texture, licence, or attribution requirement. The
figure is displayed at 90% of its original visual scale and is non-pickable and
non-colliding; the established walker body remains
authoritative. Its limbs blend between idle and walking from resolved velocity.
The third-person camera deliberately ignores building collision so a nearby wall
or window cannot alter the avatar's heading or prevent backward movement; it may
temporarily pass through geometry in tight spaces.

Walk diagnostics are written as copyable JSON lines in the browser console with a
`[walk]` prefix. Door openings identify the contacted mesh and RDF element. Throttled
collision resolutions include the body position, attempted and achieved displacement,
progress ratio, velocity, last-safe position, mesh name, navigation role, RDF element
identifier, and whether assisted walking was cancelled, so model-specific doorway and
collision traps can be reproduced.

Only-near-objects mode measures from the active camera to the nearest point of each
object's world-space bounds. If any fragment is inside the fixed 2 m radius, all
mesh fragments linked to that RDF element remain fully visible. Objects fade to
14% visibility over a short band beyond the radius to avoid visual popping.
This is a display effect only: distant objects remain selectable and retain
their semantic collision and walkability roles, so walking toward a transparent
wall restores its baseline 90% opacity before the walker reaches it. Disabling the mode restores
each imported mesh's original Babylon visibility value.

Assisted walking currently steers directly to a picked walkable point while retaining the same collision and ground handling; it is deliberately not a baked Recast navmesh. IFC models are static and already carry useful floor semantics, while runtime navmesh generation over arbitrary high-detail BIM triangle soups adds a significant WASM/build cost. Havok is likewise not loaded because the viewer has no dynamic platforms or physics-driven objects. Recast-assisted paths and Havok remain extension points if those requirements are added.

## RDF interaction and limits

Clicking or tapping a mesh selects and highlights its RDF element and opens the
object-information card. The card groups repeated predicates, abbreviates IRIs
with prefixes declared by the Turtle document, preserves literal language and
datatype information, and shows the labels of linked RDF resources when they
are available. Predicate and resource IRIs remain available as hover titles.
The close button clears the highlight. All content is inserted as text; Turtle
data is never executed as HTML.

The viewer builds mesh/entity indexes once at import time and does no RDF lookup each frame. It is intended for modest local models: large embedded base64 OBJ payloads duplicate data in memory while Turtle and OBJ are parsed. Replace a model by uploading another Turtle; its scene and GPU resources are disposed first.

Fixtures cover embedded OBJ discovery/identity, semantic-only files, malformed Turtle, unsupported/no geometry, unit normalization, and semantic navigation roles. `fixtures/navigation-semantics.ttl` includes a floor, stair/ramp surface, wall, space volume, and opening/void so movement classification cannot regress.
