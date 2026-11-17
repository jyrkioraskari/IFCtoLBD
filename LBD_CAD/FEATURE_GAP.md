# LBD CAD feature gap against IngeTrazo

Compared with the [IngeTrazo English feature page](https://ingetrazo.com/en/)
on 17 November 2026. This is a product comparison, not a plan to copy its GPL
source. LBD CAD is an early browser editor with an RDF Turtle project file;
IngeTrazo is a much broader desktop modeler. Turtle remains LBD CAD's editable
source of truth; future IFC output would be a derived export, not a second
project format.

| Area | Implemented in LBD CAD | Still missing or limited |
| --- | --- | --- |
| Sketching | Ground rectangle, exact width and depth, grid and point snapping, X/Z move constraint | Lines, circles, arcs, arbitrary drawing planes, guides, extension/parallel/perpendicular inference |
| Solid modeling | Face-aware box Push/Pull, move, rotate, uniform scale, hosted wall openings | Independent editable vertices, edges and faces; Offset, Follow Me, curved solids and robust Boolean operations; validation that every edited solid is watertight |
| Organization | Named groups that move together, layers and visibility toggles, saved camera views | Nested groups, reusable component definitions and instances, isolation, component library |
| Materials | Named Turtle material resources, metre-sized wood, concrete, metal and plaster textures, local box-face painting, element overrides | Imported mesh face identities, wall-opening face painting, external material catalog assets, and painted-area quantities |
| Views and drawings | Iso/top/front/right, display styles, live horizontal clipping plane, plan DXF edge export | Section geometry with cut fill, anchored and chained dimensions, level marks, annotations, scaled sheets, title blocks and vector PDF |
| BIM | RDF building elements and hosted openings, mesh-based area/volume CSV | Tagging arbitrary solids as BIM classes, richer quantities, IFC export; edited imported elements do not recompute RDF adjacency or containment |
| Site | Local metric grid | Georeferenced terrain and imagery, survey points, project north, route profiles |
| Exchange | Turtle open/save; OBJ and PNG/JPG texture import; OBJ, STL and plan DXF export | Direct DWG/DXF, SKP, glTF, COLLADA and STL import; glTF and COLLADA export; MTL/image files with OBJ export; IFC export |
| Automation | Local undo/redo | Action API, recipe execution, AI assistant, live MCP connection, extension system |

The site's advertised [scaled sheets, IFC export, terrain and automation](https://ingetrazo.com/en/)
are distinct product areas, not small UI adjustments. LBD CAD currently has no
equivalent end-to-end workflows for them. A production release would also need
browser interaction tests and packaging checks; the current automated checks
cover TypeScript, pure-model behavior, and a Vite build.

## Recommended sequence

1. Build an editable solid representation with validation. Keep Turtle identity
   and undo semantics stable while adding edges, loops and faces.
2. Add line, arc and circle tools on that representation, then inference and
   measured edits. Extend groups into reusable components.
3. Add BIM tagging and richer quantities in Turtle, then an optional IFC export
   that reads the saved RDF model. Define how IDs, units, geometry and hosted
   openings map to IFC before adding that exporter.
4. Build drawing views with anchored dimensions, sections and scaled sheets.
5. Add terrain, georeferencing and broader exchange formats. Expose a stable
   editor action API before connecting automation or MCP.

No IngeTrazo source has been copied into LBD CAD.
