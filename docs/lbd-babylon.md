# Walk through LBD geometry in LBDBabylon

[Documentation home](README.md) · [Using the triples](using-triples.md) ·
[Geometry export options](optional-integration.md#optional-geometry-artifact-formats)

`LBDBabylon` is the source-development browser viewer for geometry referenced by
IFCtoLBD Turtle. It keeps the RDF and geometry association intact, processes
local files in the browser, and provides orbit, walk, fly, and assisted-walk
navigation.

It is not part of the published 2.49.0 desktop package. Run it from this source
repository with Node.js 20 or newer.

## Prepare an export with geometry

A BOT topology graph alone does not contain a surface to render or walk on.
Enable geometry when converting the IFC model. The default embedded geometry
form uses `fog:asObj_v3.0-obj`. Development builds can instead publish linked
OBJ, GLB, or self-contained glTF artifacts with the `geometry-external` profile.

For a first walkthrough, use the desktop's **Geometry preview** preset or enable
geometry explicitly in the CLI. For external geometry, follow the
[artifact-format instructions](optional-integration.md#optional-geometry-artifact-formats).

## Start the viewer

From the repository root:

```sh
npm --prefix LBDBabylon ci
npm --prefix LBDBabylon run dev
```

Open the local Vite URL. Select **Upload LBD Turtle file** or drop a `.ttl` file
into the viewport.

- Embedded OBJ is decoded from the selected Turtle file.
- Linked HTTP(S) OBJ, GLB, and self-contained glTF artifacts are fetched
  automatically after Turtle parsing without credentials or a separate button.
  Browser CORS rules still apply, and failures do not prevent other available
  geometry from rendering.

If the Turtle graph has no supported geometry, the viewer reports that result
instead of constructing placeholder surfaces.

## Navigation controls

| Mode or action | Controls |
| --- | --- |
| Orbit | Drag to orbit and use the wheel to zoom. This is the default mode. |
| Walk | Click **Walk** to keep the current view and show the avatar with its close rear camera; use WASD, Up/Down for forward/back, Left/Right to turn, and drag to steer. |
| Fast walk | Hold Shift while walking. |
| Recover | Press `R` to return to the most recent safe floor position. |
| Release pointer | Press Escape. Click **Walk** again to recapture it. |
| Assisted walk | In orbit mode, double-click a recognized floor, slab, stair, or ramp. Pressing WASD cancels the destination. |
| Fly | Use WASD, `E` to rise, and `Q` to descend. |
| Only near objects | Toggle **Only near objects** to keep objects within 2 m fully visible. Farther objects become transparent. |
| Inspect object | Click or tap rendered geometry to highlight its RDF element and open the LBD attribute card. |

Switching among Orbit, Walk, and Fly preserves the viewing direction. When Walk
starts, the figure is placed on the grass-green exterior ground nearest the
current camera projection, outside the model footprint. The close rear camera
then follows it from that starting point. Leaving and entering Walk again
restores the avatar's last position and heading; loading a new model clears that
saved position.

## Object information

Click or tap any rendered object to select it with Babylon's mesh picking. The
viewer highlights every mesh fragment linked to the same RDF element and opens
an information card containing its label, kind, full URI, and direct LBD
attributes from the loaded Turtle graph.

Repeated predicates are grouped into one row. Turtle prefixes shorten predicate
and resource IRIs, literal language and datatype annotations are retained, and
linked resources use their `rdfs:label` when one is present. Hover a shortened
predicate or linked value to see its full IRI. The card uses text nodes rather
than HTML interpretation and therefore does not execute RDF content. Closing
the card also clears the model highlight.

## Third-person avatar

The optional avatar supplies a human scale reference and makes walking direction
easier to read. It is a neutral figure assembled entirely from Babylon sphere,
capsule, and box primitives. No external character model, texture, animation,
or third-party licence is bundled.

The avatar is visual-only and displayed at 90% of its original scale: it is
neither pickable nor collidable and does not
alter RDF selection. The existing walker body remains the single authority for
walls, floors, steps, gravity, and safe-position recovery. Limb movement blends
from the walker's resolved velocity, so the figure stops animating when a wall
actually stops movement rather than when a key is merely held.

In Walk mode, the avatar is always visible and a 1.2 m rear-follow camera starts
farther back and clearly above the figure's head. Dragging changes
the camera and avatar heading together rather than orbiting around to the
figure's front; the wheel changes follow distance within a compact 1.2–4.0 m range. The
walker body still collides with semantic physical elements, but the follow camera
deliberately ignores collision. This prevents nearby walls and windows from changing
the avatar's heading or blocking backward movement, at the cost of temporary camera
clipping in tight spaces.

## Near-object visibility

Rendered RDF elements retain their imported mesh visibility and material alpha.
This keeps floors, furniture, and the lower virtual ground in stable depth order.
All RDF element materials render both front and back faces, including every child
of an imported glTF multi-material. The walk avatar uses the model's normal depth
buffer, so a wall or other opaque element between the camera and avatar is rendered
in front and hides the obstructed part of the figure.
Walls use 90% opacity (10% transparency), including imported glTF multi-materials.
If a source wall material is already more transparent, its original opacity is retained.

Only-near-objects mode reduces visual clutter without cutting objects at the distance
boundary. Distance is measured from the active camera to the nearest point of
an object's world-space bounding boxes. When any mesh fragment is within the
fixed 2 m radius, every fragment linked to the same RDF element uses its imported
visibility. Beyond the radius, the whole object fades smoothly from that baseline.

Transparency does not change RDF selection, pickability, collision, walkability,
or hide/isolate state. Physical elements therefore continue to stop the walker,
while spaces, openings, and voids remain non-blocking according to their semantic
navigation role. Turning the mode off restores the imported mesh visibility.

## What affects movement

Rendering, selection, collision, and walkability are separate properties. A
mesh can be visible and selectable without blocking the user.

| RDF or IFC meaning | Movement role |
| --- | --- |
| Slabs, floors, ramps, stairs, landings, floor coverings | Walkable and physically blocking |
| Walls, columns, beams, roofs, doors, windows, railings | Physically blocking |
| Furniture and furnishing elements | Physically blocking |
| `bot:Space`, IFC spaces and spatial zones | Navigation context only; never blocking |
| `IfcOpeningElement`, openings, voids and recesses | Never blocking |
| Sites, buildings, storeys, zones, annotations and grids | Never blocking |
| Unknown or helper geometry | Visible and selectable, but non-blocking by default |

Collision uses an allowlist. This is important for BIM data because space
volumes, opening geometry, bounding helpers, and other semantic representations
must not become invisible barriers. Hiding or isolating a physical element also
disables its collision, so an invisible mesh cannot continue to block the user.

Closed door geometry initially blocks movement. When the walker touches an
`IfcDoor`, the viewer swings its geometry away from the walker and disables the
door's collision so the opening can be crossed. This is a viewer interaction;
it does not infer or write an operational door state back to the RDF model.

Contact with any other blocking element uses Babylon's native collision resolution
and surface sliding. Thin, detailed IFC meshes can place opposing triangle faces inside
the walker's ellipsoid and produce a correction opposite to the requested movement. A
fresh impact applies a small rebound; a backward correction is restored to the first
clear contact position so overlapping triangles cannot progressively wedge the walker.
Contact positions are not saved as safe recovery positions.

## Walking behaviour and units

The walker uses a separate collision body and eye camera. The body has a 0.24 m
radius (0.48 m diameter) and 1.75 m standing height; the eye is smoothed at 1.65 m. Holding Up or
Down accelerates progressively from rest. Releasing movement input stops immediately
and resets horizontal velocity to zero, so the next press starts from rest. Movement
also adds gravity, ground snapping, native collision sliding, pitch limits, and
last-safe-position recovery.

After geometry loads, the viewer creates a two-sided, 82% opaque grass-green virtual ground at the
lowest recognized walkable level. It covers the model footprint with at least
8 m of padding on every side, allowing exterior walking without a fall. The
plane provides visual and navigational context after walking begins: it has no
RDF entity and is excluded from model selection and camera fitting. It is also
the initial Walk placement, so the avatar begins outside the model on the green
area.

OBJ geometry is normalized to metres from `objUnitToMetres`, falling back to
`unitToMetres` and then metre units when metadata is absent. The conversion is
applied after subtracting the shared double-precision render origin. GLB and
glTF retain their specification-defined metre unit. These rules keep body size,
walking speed, and stair thresholds consistent across IFC authoring units.

## Assisted walking and physics scope

Assisted walking steers directly toward the picked walkable point while using
the same collision, floor, and stair checks as WASD. It is not a baked Recast
navigation mesh and cannot plan a route around an intervening wall. Use WASD or
choose a nearer point when the direct route is blocked.

The viewer does not load Havok. Current IFC walkthroughs use static geometry and
do not require dynamic rigid bodies, moving platforms, or physics-driven doors.
Recast pathfinding and Havok remain possible extensions if those requirements
are introduced; they are not needed for ordinary static model inspection.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Walk reports that it needs a floor | Confirm geometry was exported and that the floor has a slab, floor, ramp, stair, landing, or covering type. Unknown meshes are intentionally not assumed to be floors. |
| A space or opening appears solid | Inspect its RDF types. It should be typed as `bot:Space`, an IFC space, `IfcOpeningElement`, opening, or void rather than only as a generic physical element. |
| Walk starts on the wrong storey | Select a space or element on the intended storey before choosing **Walk**, or double-click the intended floor. |
| The user is too large, too small, or too slow | Check `objUnitToMetres`/`unitToMetres` on OBJ geometry. Missing metadata assumes metres. |
| Stairs cannot be climbed | Individual risers must be no higher than 0.20 m, or the stair should provide a suitable ramp-like navigation surface. Use Fly for geometry that has no traversable surface. |
| Double-click does not route around a wall | Assisted walking is direct steering, not navmesh pathfinding. Pick an intermediate floor point or use WASD. |
| Pointer lock does not engage | Click **Walk** again as a direct browser gesture. Check browser and iframe pointer-lock permissions. |
| The avatar becomes stuck after touching geometry | Open the browser console and copy the `[walk] door opened` and `[walk] collision resolved` JSON lines. They identify whether the door leaf or a wall/jamb was contacted and include the RDF element, attempted and achieved displacement, progress ratio, position, velocity, last-safe position, and assisted-walk cancellation state. |

## Developer checks

The navigation fixture contains a floor, stair/ramp surface, wall, space volume,
and opening/void. Run the viewer checks with:

```sh
npm --prefix LBDBabylon run check
npm --prefix LBDBabylon test
npm --prefix LBDBabylon run build
```

The semantic classification tests ensure spaces and voids cannot regress into
movement blockers. See the component-level [LBDBabylon README](../LBDBabylon/README.md)
for the complete RDF geometry predicates and implementation limits.
