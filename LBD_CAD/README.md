# LBD CAD

An early browser based building model editor whose project file is RDF Turtle. The RDF store is the document: a wall, slab, space, or box has a stable URI, a BOT type, a CAD primitive type, a label, and position and size literals in metres. Meshes are rebuilt from those statements. Unknown triples survive open, edit, and save. Turtle is the editable source of truth; other formats are exports derived from it.

## Run

From the repository root:

```sh
npm --prefix LBD_CAD install
npm --prefix LBD_CAD run dev
```

Open the Vite URL. Use the Create panel, select a shape in the viewport or model list, edit its properties, then Save Turtle. Click **Door** or **Window** to add one: if a wall is selected or the model has only one wall, it is added immediately; otherwise click a wall in the viewport or model list. Clicking the wall surface places it near the clicked point. Each action adds a door or window, a linked opening, and a `bot:hasSubElement` host link; the wall displays a cutout that follows edits to the new element. New CAD doors have an editable light painted leaf, a contrasting frame, and a visible handle; windows render a pane and frame inside their cutout.

## OBJ and image import

Click **Import OBJ**, select the `.obj`, its `.mtl`, and any referenced PNG/JPG images together, then choose the source units and up axis. The importer triangulates polygon faces, preserves `usemtl` material runs and UV coordinates, reads MTL color and opacity, and matches `map_Kd` images by filename. The whole import is one undo step. Save Turtle to keep the geometry, material definitions, and images in the project; no original file path is needed when reopening it. OBJ export currently writes geometry only, without MTL or image files. An OBJ may be up to 30 MB and each image up to 8 MB. Missing MTL or image files leave the relevant mesh surfaces with a solid color. To import a standalone texture, select an element and click **Import PNG/JPG as material** in Properties. Imported images can then be reused on other elements through the named material selector.

The compact CAD workspace has a vertical creation and drawing tool rail on the left, a large 3D canvas in the centre, and entity properties, model items, views, and layers in the right dock. Hover over a tool icon for its name. The canvas toolbar shows the active tool. The hint at the bottom of the canvas changes with the tool or door/window placement mode. Camera & display provides Iso, Top, Front, and Right views plus Shaded, Hidden line, Wireframe, and X-ray styles. The red X, blue Y, and green Z axes mark model directions. In Move, press X or Z to lock motion to that axis; press the same key again to unlock. Shaded and Hidden line show clean surfaces and silhouettes without mesh triangulation; imported and planar building surfaces use even color to avoid false triangular shading from OBJ or wall-cut normals. Wireframe deliberately shows the triangle mesh.

**Rectangle** draws a ground face by clicking two corners; after the first click, enter `4,3` in Measurements for an exact 4 × 3 metre face. **Push/Pull** drags the picked face of a CAD box, wall, or slab outward or inward along its normal while keeping the opposite face fixed. It also extrudes a drawn ground face upward. Hold Shift to snap the drag distance to 0.25 metre increments. Select a face or solid and enter `2.5 m` in Measurements for an exact height from its current bottom. **Snap** starts off so Move follows the pointer continuously; toggle it on when you want 0.25 metre grid and nearby point snapping. **Plan** toggles an orthographic overhead view. The Move tool (M, selected by default) lets you drag a shape across the horizontal plane; hold Shift while dragging vertically to change its height. Hosted doors and windows move in their wall plane and stay within its face, including in Front and Right views. Their wall cut follows the drag in a faded preview, and the document records the final position on release. With an element selected, enter `-2,3` in Measurements to set its absolute X and Z coordinates. The Rotate tool (R) lets you drag horizontally to turn a shape around the vertical Y axis; hold Shift to snap to 15° increments, or enter `45°` in Measurements. Scale (S) resizes all three dimensions together. Select (Space) lets you choose a shape without editing it. Drag empty space to orbit, scroll to zoom, and use Fit view to frame the model. Escape cancels a drag and returns to Select. Each completed drag is one undo step.

Open Turtle loads a saved model. The Properties panel has named materials shared by elements. Create a material, assign it to an element, and edit its color, opacity, texture width and height in metres, and grain angle in degrees. Changes to a named material update every element and face using it. Choose **Paint with**, then use Paint (P) to click one side of a CAD solid. The six local sides keep their identity when the solid moves, rotates, or resizes. Built-in wood grain, concrete, brushed metal, and plaster textures repeat according to the material's metre dimensions; Solid color disables texture. These swatches are generated locally and require no external asset service. Imported OBJ meshes retain whole-element materials because their triangles have no stable face identities. Imported IFC elements keep their original solid-color appearance until a material texture is explicitly assigned. The editor then projects metre-based texture coordinates onto imported and wall-cut triangles. Walls with openings also use whole-element materials. The Finish selector is a shortcut to shared preset materials; Element color override applies only to the selected element. The viewer draws shape boundaries and creases as contrasting lines while omitting coplanar triangle diagonals. IFCtoLBD walls, slabs, spaces, doors, windows, and openings with embedded OBJ geometry can be moved, rotated, and scaled; Save Turtle updates their mesh vertices, linked bounding boxes, and wireframes. Door and window properties show their host wall and matching opening where present. `npm --prefix LBD_CAD run check`, `test`, and `build` validate the application.

## RDF contract

Editable subjects require `rdf:type cad:Wall`, `cad:Slab`, `cad:Space`, `cad:Door`, `cad:Window`, `cad:Opening`, `cad:Face`, `cad:Box`, or `cad:Mesh`, where `cad:` is `https://w3id.org/ifctolbd/cad#`. Each subject can have `rdfs:label`, `cad:material` pointing to a named `cad:Material`, a hex `cad:color` element override, and decimal `cad:x`, `cad:y`, `cad:z`, `cad:width`, `cad:height`, `cad:depth`, and `cad:rotation`. Legacy `cad:finish` literals (`plaster`, `concrete`, `timber`, `glass`, `metal`, or `paint`) remain readable. A material has `rdfs:label`, `cad:finish`, `cad:color`, `cad:texture` (`none`, `timber`, `concrete`, `metal`, `plaster`, or `image`), decimal `cad:tileWidth` and `cad:tileHeight` in metres, decimal `cad:grainDegrees`, and decimal `cad:opacity` between 0 and 1. An image material stores a PNG/JPG data URI in `cad:imageData`. Imported OBJ meshes store encoded geometry in `fog:asObj_v3.0-obj` and their original unit/up-axis choices in `cad:sourceUnit` and `cad:sourceUpAxis`; `cad:hasObjMaterial` links an OBJ material binding with `cad:objName` and `cad:material`. All element surfaces render from both sides; opacity controls transparency independently. A painted side is a `cad:BoxFace` resource linked from its element by `cad:hasFace`; it has a `cad:faceKey` (`px`, `nx`, `py`, `ny`, `pz`, or `nz`) and a `cad:material` link. These keys refer to local box axes, so rotation and resize do not change the face resource URI. An edited imported IFC opening stores its original box as decimal `cad:originalX`, `cad:originalY`, `cad:originalZ`, `cad:originalWidth`, `cad:originalHeight`, `cad:originalDepth`, and `cad:originalRotation` literals; its wall retains `cad:baseObj` for rebuilding the void after further edits. Coordinates refer to the centre of a box; Y is up. Rotation is in degrees about Y and defaults to zero for older files. Lengths are metres. Created physical elements are also `bot:Element`; spaces are `bot:Space`. A face becomes a box when extruded and has no BOT element type until then. A created wall links to its door or window and opening using `bot:hasSubElement`; the door or window links to its opening using `cad:opening`. When an imported wall receives a CAD opening, its original encoded OBJ is retained as `cad:baseObj` on the geometry resource, and the cut mesh is written to the existing `fog:asObj_v3.0-obj` statement when saved.

Example query:

```sparql
PREFIX cad: <https://w3id.org/ifctolbd/cad#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?wall ?name ?width WHERE {
  ?wall a cad:Wall ; rdfs:label ?name ; cad:width ?width .
}
```

To find hosted openings:

```sparql
PREFIX cad: <https://w3id.org/ifctolbd/cad#>
PREFIX bot: <https://w3id.org/bot#>
SELECT ?wall ?element ?opening WHERE {
  ?wall a cad:Wall ; bot:hasSubElement ?element, ?opening .
  ?element cad:opening ?opening .
}
```

To find painted faces and their material dimensions:

```sparql
PREFIX cad: <https://w3id.org/ifctolbd/cad#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?element ?faceKey ?name ?texture ?tileWidth ?tileHeight WHERE {
  ?element cad:hasFace ?face .
  ?face cad:faceKey ?faceKey ; cad:material ?material .
  ?material rdfs:label ?name ; cad:texture ?texture ; cad:tileWidth ?tileWidth ; cad:tileHeight ?tileHeight .
}
```

To find materials bound to an imported OBJ:

```sparql
PREFIX cad: <https://w3id.org/ifctolbd/cad#>
SELECT ?mesh ?objName ?material ?texture WHERE {
  ?mesh a cad:Mesh ; cad:hasObjMaterial ?binding .
  ?binding cad:objName ?objName ; cad:material ?material .
  ?material cad:texture ?texture .
}
```

IFCtoLBD walls, slabs, spaces, doors, windows, and openings are inferred from `owl:sameAs` IFC classes, `lbd:hasBoundingBox` coordinates, and embedded `fog:asObj_v3.0-obj` geometry. Openings act as invisible cutters so they do not tint windows. Moving a door or window moves its linked opening. For an edited imported IFC opening, Turtle stores its original geometry coordinates and the wall base mesh; the renderer fills the old void and cuts the opening at its new position. Geometry edits rewrite the embedded OBJ vertices, the bounding box, and any `lbd:hasWireframe` literal. A door or window edit also transforms a matching opening resource when the converter's `%3A1` URI suffix identifies one. `bot:hasSubElement` links identify host walls. New CAD openings are cut from the wall mesh in the editor and retained as linked RDF primitives when saved. For imported IFCtoLBD walls, Save also writes the cut into the embedded OBJ and updates its bounding box and wireframe, so other OBJ readers can see the cut. The editor rebuilds its wall display from `cad:baseObj` and the linked openings to keep those openings editable. Existing topology and property assertions are retained; editing an imported opening does not recompute adjacency and containment. It does not yet import IFC, edit individual mesh vertices or profiles, or export IFC. Existing RDF statements that it does not interpret remain in the document.

Editing a linked opening moves its door or window with it. On open, the editor repairs imported inserts whose opening was moved separately by an older version; this repair can be undone before saving.

## Research and design

See [Feature gap against IngeTrazo](FEATURE_GAP.md) for a prioritized comparison of modeling, BIM, drawing, terrain, exchange, and automation capabilities.

- [BenchUp (formerly KetchUp)](https://github.com/BenchUp3D/benchup) demonstrates the browser and desktop modeling interaction set: primitives, selection, measured editing, orbit, and portable project files. LBD CAD implements a small subset focused on building elements and Turtle persistence.
- [That Open Components](https://github.com/ThatOpen/engine_components) is a modular browser BIM toolkit with viewing and measurement capabilities; its data layer is Fragments rather than RDF.
- [IfcOpenShell and Bonsai](https://github.com/IfcOpenShell/IfcOpenShell) provide native IFC authoring and geometry infrastructure. Their IFC first model is complementary to this RDF first experiment.
- This repository's `LBDBabylon` establishes Babylon.js and N3 as local browser visualization and RDF dependencies. LBD CAD uses those libraries through their public APIs. No upstream application source was copied.
- [IngeTrazo](https://ingetrazo.com/en/) informed the interaction backlog and the compact workspace layout: snapping, exact dimensions, rectangle drawing, extrusion, and plan view. LBD CAD implements these controls and styles independently; no GPL source or assets were copied.

The application is a separate Node/Vite project outside the Maven reactor. It follows the repository's Apache 2.0 license. Third party dependency licenses are retained in installed packages; distribute their required notices with any packaged build.
