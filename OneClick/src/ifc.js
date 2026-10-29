const PRODUCT_TYPES = [
  "IFCWALL", "IFCWALLSTANDARDCASE", "IFCDOOR", "IFCWINDOW", "IFCSLAB",
  "IFCROOF", "IFCCOLUMN", "IFCBEAM", "IFCSTAIR", "IFCFURNISHINGELEMENT",
  "IFCFLOWTERMINAL", "IFCBUILDINGELEMENTPROXY"
];

export const PRODUCT_CATEGORIES = {
  building: "Building elements",
  hvac: "HVAC elements",
  electrical: "Electrical elements",
  plumbing: "Plumbing & piping",
  furnishing: "Furnishings & equipment",
  services: "Other building services",
  other: "Other products"
};

export function productCategory(product) {
  return Object.hasOwn(PRODUCT_CATEGORIES, product.category) ? product.category : "other";
}

export function availableProductCategories(products) {
  return new Set((products || []).map(productCategory));
}

export function exportFilename(fileName, scope) {
  const stem = String(fileName || "").replace(/\.ifc(zip)?$/i, "") || "building";
  const label = scope.startsWith("products:")
    ? PRODUCT_CATEGORIES[scope.slice(9)]
    : { floorplan: "Floorplan", spaces: "Spaces & interfaces" }[scope];
  if (!label) throw new Error(`Unknown export selection: ${scope}`);
  const option = label.replaceAll("&", "").trim().replace(/\s+/g, "_");
  return `${stem}_${option}.ttl`;
}

export function parseIfc(text, fileName = "model.ifc") {
  const source = String(text || "");
  const count = (type) => (source.match(new RegExp(`=\\s*${type}\\(`, "gi")) || []).length;
  const schema = source.match(/FILE_SCHEMA\s*\(\s*\(\s*'([^']+)'/i)?.[1] || "IFC";
  const projectFields = quotedFields(extractRecords(source, "IFCPROJECT")[0] || "");
  const projectGuid = projectFields[0] || null;
  const projectName = projectFields[2] || projectFields[1] || fileName.replace(/\.ifc$/i, "");
  const siteGuid = quotedFields(extractRecords(source, "IFCSITE")[0] || "")[0] || null;
  const spaces = extractNames(source, "IFCSPACE");
  const storeys = extractNames(source, "IFCBUILDINGSTOREY");
  const products = PRODUCT_TYPES.flatMap((type) =>
    extractRecords(source, type).map((record, index) => ({
      type: friendlyType(type),
      name: quotedFields(record)[1] || `${friendlyType(type)} ${index + 1}`
    }))
  );
  const propertyCount = count("IFCPROPERTYSINGLEVALUE") + count("IFCPROPERTYENUMERATEDVALUE") + count("IFCQUANTITYLENGTH") + count("IFCQUANTITYAREA");

  return {
    fileName,
    schema,
    projectGuid,
    projectName,
    siteGuid,
    spaces,
    storeys,
    floorplans: [],
    spaceGeometry: [],
    interfaces: [],
    products,
    counts: {
      spaces: spaces.length,
      products: products.length,
      properties: propertyCount,
      interfaces: count("IFCRELSPACEBOUNDARY") + count("IFCRELSPACEBOUNDARY1STLEVEL") + count("IFCRELSPACEBOUNDARY2NDLEVEL")
    }
  };
}

function extractRecords(source, type) {
  const expression = new RegExp(`=\\s*${type}\\(([^;]*)\\);`, "gi");
  return [...source.matchAll(expression)].map((match) => match[1]);
}

function extractNames(source, type) {
  return extractRecords(source, type)
    .map((record) => quotedFields(record)[1])
    .filter(Boolean);
}

function quotedFields(record) {
  return [...record.matchAll(/'((?:''|[^'])*)'/g)].map((match) => match[1].replaceAll("''", "'"));
}

function friendlyType(type) {
  return type.replace(/^IFC/, "").replace(/STANDARDCASE$/, "").replace(/([A-Z])([A-Z]+)/g, (_, first, rest) => first + rest.toLowerCase());
}

function slug(value) {
  return String(value || "item").normalize("NFKD").replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-|-$/g, "").toLowerCase() || "item";
}

function usableGuid(guid) {
  return typeof guid === "string" && /^[0-9A-Za-z_$]{22}$/.test(guid);
}

function resourcePath(guid, fallback) {
  return usableGuid(guid) ? `ifc-globalid/${encodeURIComponent(guid)}` : fallback;
}

function literal(value) {
  return `"${String(value).replaceAll("\\", "\\\\").replaceAll('"', '\\"')
    .replaceAll("\n", "\\n").replaceAll("\r", "\\r").replaceAll("\t", "\\t")}"`;
}

function jsonTerm(value) {
  const json = JSON.stringify(value, (_, item) => typeof item === "string"
    ? item.replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/g, "\uFFFD")
    : item, 2);
  return `"""${json.replaceAll("\\", "\\\\").replaceAll("\n", "\n    ")}"""^^rdf:JSON`;
}

const OMITTED_ATTRIBUTE_KEYS = new Set(["stepId", "OwnerHistory", "ObjectPlacement", "Representation"]);

function withoutNulls(value) {
  if (Array.isArray(value)) return value.filter((item) => item != null).map(withoutNulls);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value)
      .filter(([key, item]) => !OMITTED_ATTRIBUTE_KEYS.has(key) && item != null)
      .map(([key, item]) => [key, withoutNulls(item)]));
  }
  return value;
}

function emit(lines, subject, facts) {
  lines.push("", subject);
  facts.forEach((fact, index) => lines.push(`  ${fact}${index === facts.length - 1 ? " ." : " ;"}`));
}

export function buildTurtle(model, scopes, options = {}) {
  const selected = new Set(scopes);
  const projectId = usableGuid(model.projectGuid) ? encodeURIComponent(model.projectGuid) : slug(model.projectName);
  const base = `https://example.org/building/${projectId}/`;
  const lines = [
    "@prefix bot: <https://w3id.org/bot#> .",
    "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .",
    "@prefix ifctolbd: <https://w3id.org/ifctolbd/mapping#> .",
    `@prefix oc: <${base}vocab/> .`,
    "@prefix geo: <http://www.opengis.net/ont/geosparql#> .",
    "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .",
    `@base <${base}> .`
  ];
  const siteFacts = ["a bot:Site", `rdfs:label ${literal(model.projectName)}`];
  if (model.siteGuid) siteFacts.push(`ifctolbd:ifcGlobalId ${literal(model.siteGuid)}`);
  emit(lines, `<${resourcePath(model.siteGuid, "")}>`, siteFacts);

  if (selected.has("floorplan")) {
    const storeys = model.floorplans.length
      ? model.floorplans
      : model.storeys.map((name) => ({ name, wkt: null }));
    storeys.forEach((storey, index) => {
      const path = resourcePath(storey.guid, `storey/${index + 1}`);
      const geometryPath = `${path}_geometry`;
      const features = storey.features || [];
      const facts = ["a bot:Storey", `rdfs:label ${literal(storey.name)}`];
      if (storey.guid) facts.push(`ifctolbd:ifcGlobalId ${literal(storey.guid)}`);
      if (storey.wkt) facts.push(`geo:hasGeometry <${geometryPath}>`);
      features.forEach((feature, featureIndex) => facts.push(`bot:hasElement <${resourcePath(feature.guid, `${path}/feature/${featureIndex + 1}`)}>`));
      emit(lines, `<${path}>`, facts);
      if (storey.wkt) {
        emit(lines, `<${geometryPath}>`, ["a geo:Geometry",
          `geo:asWKT ${literal(`<${base}crs/local> ${storey.wkt}`)}^^geo:wktLiteral`]);
      }
      features.forEach((feature, featureIndex) => {
        const featurePath = resourcePath(feature.guid, `${path}/feature/${featureIndex + 1}`);
        const featureFacts = ["a bot:Element", `rdfs:label ${literal(feature.name || feature.type)}`,
          `ifctolbd:sourceIfcType ${literal(feature.type === "door" ? "IfcDoor" : "IfcWindow")}`];
        if (feature.guid) featureFacts.push(`ifctolbd:ifcGlobalId ${literal(feature.guid)}`);
        if (feature.wkt) featureFacts.push(`geo:hasGeometry <${featurePath}_geometry>`);
        emit(lines, `<${featurePath}>`, featureFacts);
        if (feature.wkt) emit(lines, `<${featurePath}_geometry>`, ["a geo:Geometry",
          `geo:asWKT ${literal(`<${base}crs/local> ${feature.wkt}`)}^^geo:wktLiteral`]);
      });
    });
  }

  if (selected.has("spaces")) {
    const spaces = model.spaceGeometry?.length
      ? model.spaceGeometry
      : model.spaces.map((name) => ({ name }));
    const paths = new Map();
    spaces.forEach((space, index) => {
      const path = resourcePath(space.guid, `space/${index + 1}`);
      if (space.guid) paths.set(space.guid, path);
      const facts = ["a bot:Space", `rdfs:label ${literal(space.name)}`];
      if (space.guid) facts.push(`ifctolbd:ifcGlobalId ${literal(space.guid)}`);
      if (space.wkt) facts.push(`geo:hasGeometry <${path}_geometry>`);
      emit(lines, `<${path}>`, facts);
      if (space.wkt) emit(lines, `<${path}_geometry>`, ["a geo:Geometry",
        `geo:asWKT ${literal(`<${base}crs/local> ${space.wkt}`)}^^geo:wktLiteral`]);
    });
    const elements = new Map();
    (model.interfaces || []).forEach((boundary, index) => {
      const spacePath = paths.get(boundary.spaceGuid);
      if (!spacePath) return;
      const path = resourcePath(boundary.guid, `interface/${index + 1}`);
      const endpoints = [`<${spacePath}>`];
      const adjacentSpace = paths.get(boundary.correspondingSpaceGuid);
      if (adjacentSpace && adjacentSpace !== spacePath) endpoints.push(`<${adjacentSpace}>`);
      if (boundary.elementGuid) {
        let elementPath = elements.get(boundary.elementGuid);
        if (!elementPath) {
          elementPath = resourcePath(boundary.elementGuid, `boundary-element/${elements.size + 1}`);
          elements.set(boundary.elementGuid, elementPath);
          emit(lines, `<${elementPath}>`, ["a bot:Element",
            `rdfs:label ${literal(boundary.elementName || "Building element")}`,
            `ifctolbd:ifcGlobalId ${literal(boundary.elementGuid)}`]);
        }
        endpoints.push(`<${elementPath}>`);
      }
      const facts = ["a bot:Interface", `bot:interfaceOf ${endpoints.join(", ")}`];
      if (boundary.guid) facts.push(`ifctolbd:ifcGlobalId ${literal(boundary.guid)}`);
      if (boundary.wkt) facts.push(`geo:hasGeometry <${path}_geometry>`);
      emit(lines, `<${path}>`, facts);
      if (boundary.wkt) emit(lines, `<${path}_geometry>`, ["a geo:Geometry",
        `geo:asWKT ${literal(`<${base}crs/local> ${boundary.wkt}`)}^^geo:wktLiteral`]);
    });
  }

  const category = [...selected].find((scope) => scope.startsWith("products:"))?.slice(9);
  if (selected.has("products") || Object.hasOwn(PRODUCT_CATEGORIES, category)) {
    const storeys = new Map();
    for (const product of model.products) {
      if (product.storeyName) {
        const key = product.storeyGuid || product.storeyName;
        if (!storeys.has(key)) storeys.set(key, resourcePath(product.storeyGuid, `storey/${storeys.size + 1}`));
      }
    }
    const selectedProducts = model.products
      .map((product, index) => ({ product, index }))
      .filter(({ product, index }) => (options.productIndex == null || index === options.productIndex) &&
        (options.productIndex != null || !category || productCategory(product) === category));
    const storeyElements = new Map();
    for (const { product, index } of selectedProducts) {
      if (product.storeyName) {
        const key = product.storeyGuid || product.storeyName;
        if (!storeyElements.has(key)) storeyElements.set(key, { product, paths: [] });
        storeyElements.get(key).paths.push(resourcePath(product.guid, `element/${index + 1}`));
      }
    }
    for (const [key, { product, paths }] of storeyElements) {
      const facts = ["a bot:Storey", `rdfs:label ${literal(product.storeyName)}`];
      if (product.storeyGuid) facts.push(`ifctolbd:ifcGlobalId ${literal(product.storeyGuid)}`);
      paths.forEach((path) => facts.push(`bot:hasElement <${path}>`));
      emit(lines, `<${storeys.get(key)}>`, facts);
    }
    for (const { product, index } of selectedProducts) {
      const path = resourcePath(product.guid, `element/${index + 1}`);
      const facts = ["a bot:Element", `rdfs:label ${literal(product.name)}`,
        `ifctolbd:sourceIfcType ${literal(product.ifcType || product.type || "IfcProduct")}`];
      if (product.guid) facts.push(`ifctolbd:ifcGlobalId ${literal(product.guid)}`);
      facts.push(`oc:hasAttributes ${jsonTerm(withoutNulls(product.attributes || {}))}`);
      if (product.typeName) facts.push(`oc:typeName ${literal(product.typeName)}`);
      for (const name of product.materials || []) {
        facts.push(`oc:hasMaterial ${jsonTerm({ name })}`);
      }
      (product.propertySets || []).forEach((set) =>
        facts.push(`oc:hasPropertySet ${jsonTerm(set)}`));
      emit(lines, `<${path}>`, facts);
    }
  }

  lines.push("", `# Generated by OneClick from ${String(model.fileName).replace(/[\r\n]/g, " ")}`,
    `# Source schema: ${String(model.schema).replace(/[\r\n]/g, " ")}`);
  return `${lines.join("\n")}\n`;
}
