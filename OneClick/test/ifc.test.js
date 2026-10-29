import test from "node:test";
import assert from "node:assert/strict";
import { availableProductCategories, buildTurtle, exportFilename, parseIfc } from "../src/ifc.js";

const sample = `ISO-10303-21;
HEADER; FILE_SCHEMA(('IFC4')); ENDSEC;
DATA;
#1=IFCPROJECT('id',$,'Garden House',$,$,$,$,$,$);
#2=IFCBUILDINGSTOREY('id',$,'Ground floor',$,$,$,$,$,$,$);
#3=IFCSPACE('id',$,'Living room',$,$,$,$,$,$,$,$);
#4=IFCWALL('id',$,'North wall',$,$,$,$,$);
#5=IFCDOOR('id',$,'Front door',$,$,$,$,$,$);
#6=IFCPROPERTYSINGLEVALUE('FireRating',$,IFCLABEL('EI30'),$);
ENDSEC; END-ISO-10303-21;`;

function subjectBlock(turtle, path) {
  const block = turtle.split("\n\n").find((part) => part.startsWith(`<${path}>\n`));
  assert.ok(block, `Turtle contains <${path}>`);
  return block;
}

test("parses useful IFC inventory data", () => {
  const model = parseIfc(sample, "garden.ifc");
  assert.equal(model.schema, "IFC4");
  assert.equal(model.projectName, "Garden House");
  assert.deepEqual(model.spaces, ["Living room"]);
  assert.equal(model.products.length, 2);
  assert.equal(model.counts.properties, 1);
});

test("reads project GlobalId and records with whitespace after STEP assignment", () => {
  const source = sample.replaceAll("=IFC", "= IFC")
    .replace("IFCPROJECT('id'", "IFCPROJECT('1xS3BCk291UvhgP2a6eflL'");
  const model = parseIfc(source, "garden.ifc");
  assert.equal(model.projectGuid, "1xS3BCk291UvhgP2a6eflL");
  assert.equal(model.projectName, "Garden House");
  assert.equal(model.products.length, 2);
  assert.equal(model.counts.properties, 1);
});

test("IFC GlobalIds use the converter's stable project URI pattern across exports", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.projectGuid = "1xS3BCk291UvhgP2a6eflL";
  model.projectName = "Garden House";
  model.siteGuid = "3O2Fr$t4X7Zf8NOew3FNr2";
  const storeyGuid = "0O2Fr$t4X7Zf8NOew3FNr2";
  const elementGuid = "2O2Fr$t4X7Zf8NOew3FNr2";
  model.floorplans = [{ guid: storeyGuid, name: "Ground", wkt: "LINESTRING (0 0, 1 0)",
    features: [{ guid: elementGuid, name: "Door", type: "door" }] }];
  model.products = [{ guid: elementGuid, name: "Door", ifcType: "IfcDoor",
    storeyGuid, storeyName: "Ground", propertySets: [] }];
  const floorplan = buildTurtle(model, ["floorplan"]);
  const products = buildTurtle(model, ["products"]);
  const base = "https://example.org/building/1xS3BCk291UvhgP2a6eflL/";
  const storeyPath = "ifc-globalid/0O2Fr%24t4X7Zf8NOew3FNr2";
  const elementPath = "ifc-globalid/2O2Fr%24t4X7Zf8NOew3FNr2";
  assert.match(floorplan, new RegExp(`@base <${base}>`));
  assert.match(subjectBlock(floorplan, "ifc-globalid/3O2Fr%24t4X7Zf8NOew3FNr2"), /a bot:Site/);
  assert.match(subjectBlock(floorplan, storeyPath), /bot:hasElement <ifc-globalid\/2O2Fr%24t4X7Zf8NOew3FNr2>/);
  assert.match(subjectBlock(products, storeyPath), /bot:hasElement <ifc-globalid\/2O2Fr%24t4X7Zf8NOew3FNr2>/);
  assert.match(subjectBlock(floorplan, elementPath), /ifctolbd:ifcGlobalId "2O2Fr\$t4X7Zf8NOew3FNr2"/);
  assert.match(subjectBlock(products, elementPath), /ifctolbd:ifcGlobalId "2O2Fr\$t4X7Zf8NOew3FNr2"/);
  model.projectName = "Renamed house";
  assert.match(buildTurtle(model, ["products"]), new RegExp(`@base <${base}>`));
});

test("exports only selected LBD scopes", () => {
  const model = parseIfc(sample, "garden.ifc");
  const turtle = buildTurtle(model, ["spaces"]);
  assert.match(turtle, /bot:Space/);
  assert.doesNotMatch(turtle, /bot:Storey/);
  assert.doesNotMatch(turtle, /bot:Element/);
});

test("exports a separate local-coordinate WKT geometry for each storey", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.floorplans = [
    { name: "Ground floor", wkt: "MULTILINESTRING ((0 0, 2 0))" },
    { name: "Roof", wkt: null }
  ];
  const turtle = buildTurtle(model, ["floorplan"]);
  assert.match(subjectBlock(turtle, "storey/1"), /\n  a bot:Storey ;\n  rdfs:label "Ground floor" ;\n  geo:hasGeometry <storey\/1_geometry> \./);
  assert.match(turtle, /geo:asWKT "<https:\/\/example.org\/building\/garden-house\/crs\/local> MULTILINESTRING/);
  assert.match(subjectBlock(turtle, "storey/2"), /rdfs:label "Roof" \./);
  assert.doesNotMatch(turtle, /<storey\/2_geometry>/);
});

test("exports visible door and window footprints with the floorplan", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.floorplans = [{ name: "Ground floor", wkt: null, features: [
    { name: "Front door", type: "door", guid: "door-1", wkt: "POLYGON ((0 0, 1 0, 1 1, 0 0))" },
    { name: "High window", type: "window", guid: "window-1", wkt: "LINESTRING (2 0, 3 0)" }
  ] }];
  const turtle = buildTurtle(model, ["floorplan"]);
  assert.match(subjectBlock(turtle, "storey/1"), /bot:hasElement <storey\/1\/feature\/1>/);
  assert.match(subjectBlock(turtle, "storey/1/feature/1"), /ifctolbd:sourceIfcType "IfcDoor"[\s\S]*geo:hasGeometry/);
  assert.match(subjectBlock(turtle, "storey/1/feature/2"), /ifctolbd:sourceIfcType "IfcWindow"[\s\S]*geo:hasGeometry/);
  assert.match(turtle, /geo:asWKT .*POLYGON \(\(/);
  assert.match(turtle, /geo:asWKT .*LINESTRING \(/);
});

function productSetsFrom(turtle, elementPath = "element/1") {
  const productBlock = subjectBlock(turtle, elementPath);
  return [...productBlock.matchAll(/oc:hasPropertySet """([\s\S]*?)"""\^\^rdf:JSON/g)]
    .map((match) => JSON.parse(match[1].replaceAll("\\\\", "\\")));
}

function attributesFrom(turtle, elementPath = "element/1") {
  const match = subjectBlock(turtle, elementPath)
    .match(/oc:hasAttributes """([\s\S]*?)"""\^\^rdf:JSON/);
  assert.ok(match, "element has an rdf:JSON attributes literal");
  return JSON.parse(match[1].replaceAll("\\\\", "\\"));
}

function materialsFrom(turtle, elementPath = "element/1") {
  return [...subjectBlock(turtle, elementPath)
    .matchAll(/oc:hasMaterial """([\s\S]*?)"""\^\^rdf:JSON/g)]
    .map((match) => JSON.parse(match[1].replaceAll("\\\\", "\\")));
}

test("exports each property set directly as a separate RDF JSON value", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.products = [{
    guid: "element-1", ifcType: "IfcCovering", name: "Ceiling panel",
    description: "Acoustic\npanel", predefinedType: "CEILING", typeName: "Acoustic Type A",
    attributes: { GlobalId: "element-1", Name: "Ceiling panel", Description: "Acoustic\npanel",
      PredefinedType: "CEILING", OwnerHistory: { ifcType: "IfcOwnerHistory" },
      ObjectPlacement: { ifcType: "IfcLocalPlacement", stepId: 42 },
      Representation: { ifcType: "IfcProductDefinitionShape" } },
    storeyGuid: "storey-1", storeyName: "Ground floor", materials: ["Mineral wool"],
    propertySets: [{ name: "Pset_CoveringCommon", properties: [
      { name: "IsExternal", value: false }, { name: "ThermalResistance", value: 2.5 }
    ] }]
  }];
  const turtle = buildTurtle(model, ["products"]);
  assert.match(subjectBlock(turtle, "element/1"), /a bot:Element ;[\s\S]*ifctolbd:sourceIfcType "IfcCovering"/);
  assert.match(turtle, /\n\n<element\/1>\n  a bot:Element ;\n/);
  assert.match(subjectBlock(turtle, "element/1"), /oc:hasPropertySet """\{\n/);
  assert.match(turtle, /ifctolbd:ifcGlobalId "element-1"/);
  assert.deepEqual(attributesFrom(turtle), {
    GlobalId: "element-1", Name: "Ceiling panel", Description: "Acoustic\npanel",
    PredefinedType: "CEILING"
  });
  assert.match(turtle, /oc:typeName "Acoustic Type A"/);
  assert.doesNotMatch(turtle, /oc:description|oc:predefinedType/);
  assert.match(subjectBlock(turtle, "storey/1"), /bot:hasElement <element\/1>/);
  assert.deepEqual(materialsFrom(turtle), [{ name: "Mineral wool" }]);
  assert.doesNotMatch(turtle, /<material\/|a oc:Material/);
  assert.deepEqual(productSetsFrom(turtle), model.products[0].propertySets);
  assert.doesNotMatch(turtle, /oc:propertySets|oc:PropertySet|oc:properties|property-set\//);
});

test("hasAttributes keeps every valued field and removes nulls", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.products = [{ name: "Wall", ifcType: "IfcWall", attributes: {
    GlobalId: "wall-1", Description: null, Tag: "W-1",
    OwnerHistory: { ifcType: "IfcOwnerHistory" },
    ObjectPlacement: { ifcType: "IfcLocalPlacement", stepId: 42, optional: null },
    Representation: { ifcType: "IfcProductDefinitionShape" },
    Codes: ["A", null, "B"]
  }, propertySets: [] }];
  const turtle = buildTurtle(model, ["products"]);
  assert.deepEqual(attributesFrom(turtle), {
    GlobalId: "wall-1", Tag: "W-1",
    Codes: ["A", "B"]
  });
});

test("an element's Turtle uses its URI from the full product export", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.products = [
    { guid: "first", ifcType: "IfcWall", name: "Wall", materials: ["Steel"], propertySets: [] },
    { guid: "second", ifcType: "IfcDoor", name: "Door", materials: ["Wood"],
      propertySets: [{ name: "Pset_DoorCommon", properties: [{ name: "FireRating", value: "EI30" }] }] }
  ];
  const all = buildTurtle(model, ["products"]);
  const door = buildTurtle(model, ["products"], { productIndex: 1 });
  assert.match(subjectBlock(all, "element/2"), /a bot:Element/);
  assert.match(subjectBlock(door, "element/2"), /a bot:Element/);
  assert.deepEqual(materialsFrom(door, "element/2"), [{ name: "Wood" }]);
  assert.deepEqual(productSetsFrom(door, "element/2"), model.products[1].propertySets);
  assert.deepEqual(productSetsFrom(buildTurtle(model, ["products"], { productIndex: 0 })), []);
  assert.doesNotMatch(door, /<element\/1>\n/);
  assert.doesNotMatch(door, /<material\//);
});

test("each material is a separate JSON value on its element", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.products = [{ name: "Wall", ifcType: "IfcWall", materials: ["Brick", "Mortar"] }];
  const turtle = buildTurtle(model, ["products"]);
  assert.deepEqual(materialsFrom(turtle), [{ name: "Brick" }, { name: "Mortar" }]);
  assert.equal([...subjectBlock(turtle, "element/1").matchAll(/oc:hasMaterial """/g)].length, 2);
});

test("separate property sets retain their own names and values", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.products = [{ name: "Door", ifcType: "IfcDoor", propertySets: [
    { name: "Pset_DoorCommon", properties: [{ name: "FireRating", value: "EI30" }] },
    { name: "Qto_DoorBaseQuantities", properties: [{ name: "Width", value: 0.9 }] }
  ] }];
  const turtle = buildTurtle(model, ["products"]);
  assert.equal([...subjectBlock(turtle, "element/1").matchAll(/oc:hasPropertySet """/g)].length, 2);
  assert.deepEqual(productSetsFrom(turtle), model.products[0].propertySets);
});

test("product categories export only their elements while preserving product URIs", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.products = [
    { name: "North wall", ifcType: "IfcWall", category: "building", propertySets: [] },
    { name: "Supply duct", ifcType: "IfcDuctSegment", category: "hvac", propertySets: [] },
    { name: "Cable", ifcType: "IfcCableSegment", category: "electrical", propertySets: [] }
  ];
  const hvac = buildTurtle(model, ["products:hvac"]);
  assert.match(subjectBlock(hvac, "element/2"), /rdfs:label "Supply duct"/);
  assert.doesNotMatch(hvac, /<element\/1>\n|<element\/3>\n/);
  assert.doesNotMatch(hvac, /North wall|Cable"/);
  assert.match(buildTurtle(model, ["products:building"]), /rdfs:label "North wall"/);
  assert.match(buildTurtle(model, ["products:electrical"]), /rdfs:label "Cable"/);
});

test("only populated product categories are available for selection", () => {
  assert.deepEqual(availableProductCategories([
    { category: "building" }, { category: "hvac" }, { category: "building" }
  ]), new Set(["building", "hvac"]));
  assert.deepEqual(availableProductCategories([]), new Set());
  assert.deepEqual(availableProductCategories([{ category: "unknown" }]), new Set(["other"]));
});

test("main export filename uses every selection word with underscores", () => {
  assert.equal(exportFilename("Duplex_A.ifc", "floorplan"), "Duplex_A_Floorplan.ttl");
  assert.equal(exportFilename("Duplex_A.ifc", "spaces"), "Duplex_A_Spaces_interfaces.ttl");
  assert.equal(exportFilename("Duplex_A.ifc", "products:hvac"), "Duplex_A_HVAC_elements.ttl");
  assert.equal(exportFilename("Duplex_A.ifc", "products:building"), "Duplex_A_Building_elements.ttl");
  assert.equal(exportFilename("Duplex_A.ifc", "products:plumbing"), "Duplex_A_Plumbing_piping.ttl");
  assert.equal(exportFilename("Duplex_A.ifc", "products:furnishing"), "Duplex_A_Furnishings_equipment.ttl");
  assert.equal(exportFilename("Duplex_A.ifc", "products:services"), "Duplex_A_Other_building_services.ttl");
  assert.equal(exportFilename("model.IFCZIP", "products:other"), "model_Other_products.ttl");
});

test("JSON property sets replace lone surrogate code points", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.products = [{ name: "Door", ifcType: "IfcDoor", propertySets: [
    { name: "Pset_DoorCommon", properties: [{ name: "Comment", value: "bad\uD800text" }] }
  ] }];
  const value = productSetsFrom(buildTurtle(model, ["products"]));
  assert.equal(value[0].properties[0].value, "bad\uFFFDtext");
});

test("connects spaces and explicit interfaces to their 3D WKT geometry", () => {
  const model = parseIfc(sample, "garden.ifc");
  model.spaceGeometry = [
    { guid: "space-1", name: "Living room", wkt: "POLYHEDRALSURFACE Z (((0 0 0, 1 0 0, 0 1 0, 0 0 0)))" },
    { guid: "space-2", name: "Hall" }
  ];
  model.interfaces = [
    { spaceGuid: "space-1", correspondingSpaceGuid: "space-2", elementGuid: "wall-1", elementName: "North wall", wkt: "POLYGON Z ((0 0 0, 1 0 0, 1 0 2, 0 0 0))" },
    { spaceGuid: "space-2", elementGuid: null, wkt: null }
  ];
  const turtle = buildTurtle(model, ["spaces"]);
  assert.match(subjectBlock(turtle, "space/1"), /a bot:Space ;[\s\S]*geo:hasGeometry <space\/1_geometry>/);
  assert.match(subjectBlock(turtle, "space/1_geometry"), /geo:asWKT "<https:\/\/example.org\/building\/garden-house\/crs\/local> POLYHEDRALSURFACE Z/);
  assert.match(subjectBlock(turtle, "interface/1"), /a bot:Interface ;\n  bot:interfaceOf <space\/1>, <space\/2>, <boundary-element\/1> ;\n  geo:hasGeometry <interface\/1_geometry>/);
  assert.match(subjectBlock(turtle, "interface/1_geometry"), /geo:asWKT .*POLYGON Z/);
  assert.match(subjectBlock(turtle, "interface/2"), /bot:interfaceOf <space\/2> \./);
  assert.doesNotMatch(turtle, /<space\/2_geometry>/);
});
