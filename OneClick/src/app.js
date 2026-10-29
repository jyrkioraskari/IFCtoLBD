import { availableProductCategories, buildTurtle, exportFilename, parseIfc, PRODUCT_CATEGORIES, productCategory } from "./ifc.js";
import { projectPlanPoint, projectWorldPoint } from "./projection.js";

const elements = {
  action: document.querySelector("#primaryAction"),
  actionLabel: document.querySelector(".action-label"),
  fileInput: document.querySelector("#fileInput"),
  fileSummary: document.querySelector("#fileSummary"),
  fileName: document.querySelector("#fileName"),
  fileMeta: document.querySelector("#fileMeta"),
  selections: document.querySelector("#selections"),
  selectionCount: document.querySelector("#selectionCount"),
  cards: [...document.querySelectorAll(".selection-card")],
  emptyState: document.querySelector("#emptyState"),
  canvas: document.querySelector("#canvasWrap"),
  building: document.querySelector("#buildingView"),
  planView: document.querySelector("#planView"),
  spaceView: document.querySelector("#spaceView"),
  productView: document.querySelector("#productView"),
  viewTitle: document.querySelector("#viewTitle"),
  support: document.querySelector("#supportCopy"),
  toast: document.querySelector("#toast")
};

let model = null;
let isReading = false;
let spaceScene = null;

elements.canvas.addEventListener("click", async (event) => {
  const button = event.target.closest(".copy-wkt");
  if (!button) return;
  const wkt = button.closest(".wkt-panel").querySelector("textarea");
  try {
    await navigator.clipboard.writeText(wkt.value);
    showToast("3D or floorplan WKT copied.");
  } catch {
    wkt.select();
    showToast("Select and copy the WKT shown below this floorplan.");
  }
});

elements.action.addEventListener("click", () => {
  if (!model) {
    chooseFile();
    return;
  }
  exportSelection();
});

elements.fileSummary.addEventListener("click", chooseFile);

function chooseFile() {
  if (isReading) return;
  elements.fileInput.value = "";
  elements.fileInput.click();
}

elements.fileInput.addEventListener("change", async (event) => {
  const file = event.target.files?.[0];
  if (!file || isReading) return;
  resetWorkflow();
  await readFile(file);
});

elements.cards.forEach((card) => {
  const radio = card.querySelector("input");
  radio.addEventListener("change", () => {
    elements.cards.forEach((item) => item.classList.toggle("is-active", item.querySelector("input").checked));
    updateSelection();
    setPreview(card.dataset.view);
  });
});

function resetWorkflow() {
  model = null;
  spaceScene?.destroy();
  spaceScene = null;
  elements.fileSummary.hidden = true;
  elements.selections.hidden = true;
  elements.cards.forEach((card, index) => {
    card.hidden = false;
    card.querySelector("input").disabled = false;
    card.querySelector(".selection-number").textContent = String(index + 1).padStart(2, "0");
    card.querySelector("input").checked = index === 0;
    card.classList.toggle("is-active", index === 0);
  });
  elements.selectionCount.textContent = "Floorplan selected";
  elements.planView.replaceChildren();
  elements.spaceView.replaceChildren();
  elements.productView.replaceChildren();
  elements.planView.hidden = true;
  elements.spaceView.hidden = true;
  elements.productView.hidden = true;
  elements.canvas.classList.remove("has-preview");
  elements.building.hidden = false;
  elements.emptyState.hidden = false;
  elements.viewTitle.textContent = "Waiting for a model";
  elements.action.classList.remove("is-export");
  elements.action.disabled = false;
  elements.actionLabel.textContent = "Read an IFC model";
  elements.support.hidden = true;
  elements.support.textContent = "";
  clearTimeout(showToast.timer);
  elements.toast.classList.remove("is-visible");
}

async function readFile(file) {
  isReading = true;
  elements.action.classList.add("is-loading");
  elements.action.disabled = true;
  elements.actionLabel.textContent = "Reading your model…";
  elements.support.hidden = false;
  elements.support.textContent = "Finding spaces, elements and property sets";

  try {
    const text = await file.text();
    model = parseIfc(text, file.name);
    try {
      const response = await fetch("/api/floorplans", { method: "POST", body: file });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || "Floorplan extraction failed");
      if (!Array.isArray(result.storeys)) throw new Error("The floorplan service returned no storeys.");
      model.floorplans = result.storeys;
      model.spaceGeometry = Array.isArray(result.spaces) ? result.spaces : [];
      model.interfaces = Array.isArray(result.interfaces) ? result.interfaces : [];
      model.products = Array.isArray(result.products) ? result.products : [];
      model.floorplanError = null;
      model.geometryError = Array.isArray(result.spaces) && Array.isArray(result.interfaces)
        ? null
        : "Restart the OneClick server to enable 3D space geometry.";
      model.productError = Array.isArray(result.products)
        ? null
        : "Restart the OneClick server to enable complete product data.";
    } catch (error) {
      console.error("OneClick geometry extraction failed:", error);
      model.floorplanError = error.message;
      model.geometryError = error.message;
      model.productError = error.message;
    }
    presentModel(file);
  } catch (error) {
    resetWorkflow();
    showToast("We couldn’t read that file. Please try another IFC model.");
  } finally {
    isReading = false;
    elements.action.classList.remove("is-loading");
  }
}

function presentModel(file) {
  elements.fileName.textContent = file.name;
  elements.fileMeta.textContent = `${model.schema} · ${formatBytes(file.size)}`;
  elements.fileSummary.hidden = false;
  elements.selections.hidden = false;
  showAvailableSelections();
  elements.emptyState.hidden = true;
  elements.canvas.classList.add("has-preview");
  elements.building.hidden = true;
  elements.planView.hidden = false;
  renderFloorplans();
  renderSpaces();
  elements.actionLabel.textContent = "Export";
  elements.action.classList.add("is-export");
  elements.viewTitle.textContent = model.projectName;
  updateSelection();
  setPreview(selectedScopes()[0]);
  showToast(model.floorplanError
    ? `Floorplan extraction failed: ${model.floorplanError}`
    : "Model ready — choose what you want to keep.");
}

function showAvailableSelections() {
  const available = model.productError ? new Set() : availableProductCategories(model.products);
  let position = 0;
  for (const card of elements.cards) {
    const category = card.dataset.view.startsWith("products:") ? card.dataset.view.slice(9) : null;
    card.hidden = Boolean(category && !available.has(category));
    card.querySelector("input").disabled = card.hidden;
    if (!card.hidden) {
      position += 1;
      card.querySelector(".selection-number").textContent = String(position).padStart(2, "0");
    }
  }
}

function updateSelection() {
  const selected = selectedScopes();
  const floorplanFailed = selected.includes("floorplan") && Boolean(model?.floorplanError);
  const spacesFailed = selected.includes("spaces") && Boolean(model?.geometryError);
  const productsFailed = selected[0]?.startsWith("products:") && Boolean(model?.productError);
  const category = selected[0]?.startsWith("products:") ? selected[0].slice(9) : null;
  const emptyCategory = category && !model?.productError &&
    !model?.products.some((product) => productCategory(product) === category);
  const names = { floorplan: "Floorplan", spaces: "Spaces & interfaces" };
  const name = category ? PRODUCT_CATEGORIES[category] : names[selected[0]];
  elements.selectionCount.textContent = selected.length ? `${name} selected` : "Choose one";
  elements.action.disabled = selected.length === 0 || floorplanFailed || spacesFailed || productsFailed || emptyCategory;
  elements.actionLabel.textContent = floorplanFailed || spacesFailed || productsFailed
    ? "Choose another export"
    : emptyCategory ? "Nothing to export"
    : selected.length ? "Export" : "Choose one export";
  elements.support.textContent = emptyCategory
    ? `No ${name.toLowerCase()} are present in this model.`
    : floorplanFailed || spacesFailed || productsFailed
    ? "Extraction failed; see the preview for details"
    : "";
  elements.support.hidden = !floorplanFailed && !spacesFailed && !productsFailed && !emptyCategory;
}

function selectedScopes() {
  return elements.cards
    .map((card) => card.querySelector("input"))
    .filter((input) => input.checked && !input.disabled)
    .map((input) => input.value);
}

function setPreview(view) {
  if (!model) return;
  const category = view.startsWith("products:") ? view.slice(9) : null;
  const titles = { floorplan: "Floorplan", spaces: "Spaces & interfaces" };
  elements.viewTitle.textContent = category ? PRODUCT_CATEGORIES[category] : titles[view];
  elements.planView.hidden = view !== "floorplan";
  elements.spaceView.hidden = view !== "spaces";
  elements.productView.hidden = !category;
  if (category) renderProducts(category);
  if (view === "spaces") requestAnimationFrame(() => spaceScene?.render());
}

function renderFloorplans() {
  elements.planView.replaceChildren();
  if (!model.floorplans.length) {
    const message = document.createElement("p");
    message.className = "plan-empty";
    message.textContent = model.floorplanError
      ? `Floorplans unavailable: ${model.floorplanError}`
      : "No storey floorplans are available in this model.";
    elements.planView.append(message);
    return;
  }
  model.floorplans.forEach((plan, index) => {
    const card = document.createElement("article");
    card.className = "plan-card";
    const heading = document.createElement("h3");
    heading.textContent = `${String(index + 1).padStart(2, "0")} · ${plan.name}`;
    const elevation = document.createElement("span");
    elevation.className = "plan-elevation";
    elevation.textContent = `${plan.elevation} m elevation`;
    card.append(heading, elevation);

    const lines = plan.lines || [];
    const features = plan.features || [];
    const status = document.createElement("p");
    status.className = "plan-status";
    if (lines.length || features.length) {
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.classList.add("floorplan-graph");
      svg.setAttribute("viewBox", "0 0 680 500");
      svg.setAttribute("role", "img");
      svg.setAttribute("aria-label", `${plan.name} floorplan with doors and windows`);
      renderFloorplanLines(svg, lines, features);
      card.append(svg);
      if (features.length) {
        const legend = document.createElement("div");
        legend.className = "floorplan-legend";
        for (const [kind, label] of [["door", "Doors"], ["window", "Windows"]]) {
          if (!features.some((feature) => feature.type === kind)) continue;
          const item = document.createElement("span");
          item.className = `${kind}-key`;
          item.textContent = label;
          legend.append(item);
        }
        card.append(legend);
      }
      status.textContent = `Section at ${plan.cutHeight} m`;
    } else {
      status.textContent = `No section geometry at ${plan.cutHeight} m.`;
    }
    card.append(status);

    if (plan.wkt) {
      const panel = document.createElement("div");
      panel.className = "wkt-panel";
      const heading = document.createElement("div");
      heading.className = "wkt-heading";
      const label = document.createElement("label");
      label.htmlFor = `floorplanWkt${index}`;
      label.textContent = "FLOORPLAN WKT";
      const copy = document.createElement("button");
      copy.className = "copy-wkt";
      copy.type = "button";
      copy.textContent = "Copy WKT";
      heading.append(label, copy);
      const wkt = document.createElement("textarea");
      wkt.id = label.htmlFor;
      wkt.readOnly = true;
      wkt.value = plan.wkt;
      panel.append(heading, wkt);
      card.append(panel);
    }
    elements.planView.append(card);
  });
}

function renderProducts(category) {
  elements.productView.replaceChildren();
  if (model.productError) {
    const message = document.createElement("p");
    message.className = "plan-empty";
    message.textContent = `Product data unavailable: ${model.productError}`;
    elements.productView.append(message);
    return;
  }
  const matches = model.products.map((product, index) => ({ product, index }))
    .filter(({ product }) => productCategory(product) === category);
  if (!matches.length) {
    const message = document.createElement("p");
    message.className = "plan-empty";
    message.textContent = `No ${PRODUCT_CATEGORIES[category].toLowerCase()} are present in this model.`;
    elements.productView.append(message);
    return;
  }

  const card = document.createElement("section");
  card.className = "plan-card product-card";
  const heading = document.createElement("h3");
  heading.textContent = PRODUCT_CATEGORIES[category];
  const search = document.createElement("input");
  search.className = "product-search";
  search.type = "search";
  search.placeholder = "Find a product, type, storey or material";
  search.setAttribute("aria-label", "Search products");
  const scroll = document.createElement("div");
  scroll.className = "product-table-wrap";
  const table = document.createElement("table");
  table.className = "product-table";
  const header = document.createElement("thead");
  const headerRow = document.createElement("tr");
  for (const title of ["Product", "IFC class", "Type", "Storey", "Material"]) {
    const cell = document.createElement("th");
    cell.scope = "col";
    cell.textContent = title;
    headerRow.append(cell);
  }
  header.append(headerRow);
  const body = document.createElement("tbody");
  const details = document.createElement("section");
  details.className = "product-details";
  details.setAttribute("aria-live", "polite");
  const rows = matches.map(({ product, index }) => {
    const row = document.createElement("tr");
    const nameCell = document.createElement("td");
    const button = document.createElement("button");
    button.className = "product-name";
    button.type = "button";
    button.textContent = product.name;
    button.addEventListener("click", () => showProduct(product, row, index));
    nameCell.append(button);
    row.append(nameCell);
    for (const value of [product.ifcType, product.typeName, product.storeyName,
      (product.materials || []).join(", ")]) {
      const cell = document.createElement("td");
      cell.textContent = value || "—";
      row.append(cell);
    }
    row.searchText = [product.name, product.ifcType, product.typeName,
      product.storeyName, product.guid, ...(product.materials || [])]
      .filter(Boolean).join(" ").toLocaleLowerCase();
    body.append(row);
    return row;
  });
  table.append(header, body);
  scroll.append(table);
  card.append(heading, search, scroll, details);
  elements.productView.append(card);

  function showProduct(product, row, index) {
    rows.forEach((item) => item.classList.toggle("is-selected", item === row));
    details.replaceChildren();
    const title = document.createElement("h4");
    title.textContent = product.name;
    const info = document.createElement("dl");
    info.className = "product-info";
    for (const [label, value] of [
      ["IFC GlobalId", product.guid], ["IFC class", product.ifcType],
      ["Type", product.typeName], ["Predefined type", product.predefinedType],
      ["Storey", product.storeyName], ["Material", (product.materials || []).join(", ")],
      ["Description", product.description]
    ]) {
      if (!value) continue;
      const term = document.createElement("dt");
      term.textContent = label;
      const definition = document.createElement("dd");
      definition.textContent = value;
      info.append(term, definition);
    }
    details.append(title, info);
    const setHeading = document.createElement("h5");
    setHeading.textContent = "Properties and quantities";
    details.append(setHeading);
    const groups = new Map();
    for (const set of product.propertySets || []) {
      const name = set.name || "Unnamed property set";
      if (!groups.has(name)) groups.set(name, []);
      groups.get(name).push(...(set.properties || []));
    }
    if (!groups.size) {
      const empty = document.createElement("p");
      empty.className = "plan-status";
      empty.textContent = "No properties or quantities are assigned to this product.";
      details.append(empty);
      appendProductTurtle(index);
      return;
    }
    const propertyTable = document.createElement("table");
    propertyTable.className = "product-table property-table";
    const propertyHeader = document.createElement("thead");
    const propertyHeaderRow = document.createElement("tr");
    for (const label of ["Property", "Value"]) {
      const cell = document.createElement("th");
      cell.scope = "col";
      cell.textContent = label;
      propertyHeaderRow.append(cell);
    }
    propertyHeader.append(propertyHeaderRow);
    propertyTable.append(propertyHeader);
    for (const [setName, properties] of groups) {
      const propertyBody = document.createElement("tbody");
      const groupRow = document.createElement("tr");
      groupRow.className = "property-set-row";
      const groupLabel = document.createElement("th");
      groupLabel.scope = "rowgroup";
      groupLabel.colSpan = 2;
      groupLabel.textContent = setName;
      groupRow.append(groupLabel);
      propertyBody.append(groupRow);
      for (const property of properties) {
        const propertyRow = document.createElement("tr");
        for (const value of [property.name, property.value == null ? "—" : String(property.value)]) {
          const cell = document.createElement("td");
          cell.textContent = value;
          propertyRow.append(cell);
        }
        propertyBody.append(propertyRow);
      }
      propertyTable.append(propertyBody);
    }
    details.append(propertyTable);
    appendProductTurtle(index);
  }

  function appendProductTurtle(index) {
    const panel = document.createElement("section");
    panel.className = "product-turtle-panel";
    const heading = document.createElement("div");
    heading.className = "product-turtle-heading";
    const title = document.createElement("h5");
    title.textContent = "Turtle for this element";
    const copy = document.createElement("button");
    copy.type = "button";
    copy.textContent = "Copy Turtle";
    const download = document.createElement("button");
    download.type = "button";
    download.textContent = "Download .ttl";
    const turtle = document.createElement("textarea");
    turtle.className = "product-turtle";
    turtle.readOnly = true;
    turtle.setAttribute("aria-label", "Turtle for this element");
    turtle.value = buildTurtle(model, ["products"], { productIndex: index });
    copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(turtle.value);
        showToast("Element Turtle copied.");
      } catch {
        turtle.select();
        showToast("Select and copy this element’s Turtle.");
      }
    });
    download.addEventListener("click", () => {
      const identifier = (model.products[index].guid || String(index + 1))
        .replace(/[^a-zA-Z0-9_-]+/g, "-");
      const stem = model.fileName.replace(/\.ifc(zip)?$/i, "") || "building";
      downloadTurtle(turtle.value, `${stem}-${identifier}.ttl`);
      showToast("Element Turtle is ready.");
    });
    const actions = document.createElement("div");
    actions.className = "product-turtle-actions";
    actions.append(copy, download);
    heading.append(title, actions);
    panel.append(heading, turtle);
    details.append(panel);
  }

  search.addEventListener("input", () => {
    const query = search.value.trim().toLocaleLowerCase();
    rows.forEach((row) => { row.hidden = !row.searchText.includes(query); });
    const first = rows.find((row) => !row.hidden);
    if (first) first.querySelector("button").click();
    else {
      details.replaceChildren();
      const message = document.createElement("p");
      message.className = "plan-status";
      message.textContent = "No products match this search.";
      details.append(message);
    }
  });
  rows[0].querySelector("button").click();
}

function renderSpaces() {
  spaceScene?.destroy();
  spaceScene = null;
  elements.spaceView.replaceChildren();
  if (model.geometryError) {
    const message = document.createElement("p");
    message.className = "plan-empty";
    message.textContent = `Space geometry unavailable: ${model.geometryError}`;
    elements.spaceView.append(message);
    return;
  }
  const spaces = model.spaceGeometry.length
    ? model.spaceGeometry
    : model.spaces.map((name) => ({ name }));
  if (!spaces.length) {
    const message = document.createElement("p");
    message.className = "plan-empty";
    message.textContent = "This model has no spaces to preview.";
    elements.spaceView.append(message);
    return;
  }

  const card = document.createElement("section");
  card.className = "plan-card space-card";
  const heading = document.createElement("h3");
  heading.textContent = "Spaces & interfaces in 3D";
  const toolbar = document.createElement("div");
  toolbar.className = "scene-toolbar";
  const layers = document.createElement("div");
  layers.className = "scene-layers";
  layers.setAttribute("role", "group");
  layers.setAttribute("aria-label", "Visible 3D layers");
  const spaceButton = document.createElement("button");
  spaceButton.type = "button";
  spaceButton.className = "scene-layer-button space-layer";
  spaceButton.textContent = "Spaces";
  spaceButton.setAttribute("aria-pressed", "true");
  const interfaceButton = document.createElement("button");
  interfaceButton.type = "button";
  interfaceButton.className = "scene-layer-button interface-layer";
  interfaceButton.textContent = "Interfaces";
  interfaceButton.setAttribute("aria-pressed", "true");
  layers.append(spaceButton, interfaceButton);
  const explainButton = document.createElement("button");
  explainButton.type = "button";
  explainButton.className = "scene-explain-button";
  explainButton.textContent = "Explain this view";
  explainButton.setAttribute("aria-expanded", "false");
  const explanation = document.createElement("p");
  explanation.className = "scene-explanation";
  explanation.id = "scene-explanation";
  explanation.hidden = true;
  explanation.textContent = "Green spaces are IFC room volumes. Orange interfaces are the surfaces where a space meets a building element or a virtual boundary. Show either layer or both to compare them; selecting a space highlights its related geometry and WKT.";
  explainButton.setAttribute("aria-controls", explanation.id);
  explainButton.addEventListener("click", () => {
    explanation.hidden = !explanation.hidden;
    explainButton.setAttribute("aria-expanded", String(!explanation.hidden));
  });
  toolbar.append(layers, explainButton);
  const scene = document.createElement("canvas");
  scene.className = "space-scene";
  scene.setAttribute("role", "img");
  scene.setAttribute("aria-label", "Interactive 3D view of all spaces and interface surfaces");
  scene.textContent = "3D space preview requires a canvas-enabled browser.";
  const sceneHelp = document.createElement("p");
  sceneHelp.className = "scene-help";
  sceneHelp.textContent = "Drag to rotate · scroll to zoom · double-click to reset";
  const picker = document.createElement("select");
  picker.className = "space-picker";
  picker.setAttribute("aria-label", "Highlight a space and inspect its WKT");
  picker.add(new Option("All spaces", ""));
  spaces.forEach((space, index) => picker.add(new Option(space.name, String(index))));
  const description = document.createElement("p");
  description.className = "plan-status";
  const geometry = document.createElement("div");
  const boundaries = document.createElement("div");
  boundaries.className = "interface-list";
  card.append(heading, toolbar, explanation, scene, sceneHelp, picker, description, geometry, boundaries);
  elements.spaceView.append(card);
  spaceScene = createSpaceScene(scene, spaces, model.interfaces,
    () => picker.value === "" ? null : spaces[Number(picker.value)].guid,
    () => ({ space: spaceButton.getAttribute("aria-pressed") === "true",
             interface: interfaceButton.getAttribute("aria-pressed") === "true" }));
  for (const button of [spaceButton, interfaceButton]) {
    button.addEventListener("click", () => {
      button.setAttribute("aria-pressed", String(button.getAttribute("aria-pressed") !== "true"));
      spaceScene.render();
    });
  }

  function showSpace() {
    const space = picker.value === "" ? null : spaces[Number(picker.value)];
    spaceScene.render();
    geometry.replaceChildren();
    boundaries.replaceChildren();
    if (!space) {
      description.textContent = "Use the layer buttons to show spaces, interfaces, or both.";
      return;
    }
    const interfaces = model.interfaces.filter((item) => item.spaceGuid === space.guid);
    const drawn = interfaces.filter((item) => item.faces?.length);
    description.textContent = `${space.name} selected. Use the layer buttons to choose what appears in 3D.`;
    if (space.wkt) geometry.append(wktPanel("SPACE 3D WKT", space.wkt));
    else {
      const message = document.createElement("p");
      message.className = "plan-status";
      message.textContent = "No 3D representation is available for this space.";
      geometry.append(message);
    }
    const title = document.createElement("h4");
    title.textContent = "Interface surfaces";
    boundaries.append(title);
    if (!drawn.length) {
      const message = document.createElement("p");
      message.className = "plan-status";
      message.textContent = "No explicit interface surfaces are available for this space.";
      boundaries.append(message);
    }
    drawn.forEach((item, index) => {
      const entry = document.createElement("details");
      const summary = document.createElement("summary");
      summary.textContent = `${index + 1}. ${item.elementName || "Virtual boundary"}`;
      entry.append(summary);
      entry.append(wktPanel("INTERFACE 3D WKT", item.wkt));
      boundaries.append(entry);
    });
  }

  picker.addEventListener("change", showSpace);
  showSpace();
}

function wktPanel(labelText, value) {
  const panel = document.createElement("div");
  panel.className = "wkt-panel";
  const heading = document.createElement("div");
  heading.className = "wkt-heading";
  const label = document.createElement("span");
  label.textContent = labelText;
  const copy = document.createElement("button");
  copy.className = "copy-wkt";
  copy.type = "button";
  copy.textContent = "Copy WKT";
  const wkt = document.createElement("textarea");
  wkt.readOnly = true;
  wkt.setAttribute("aria-label", labelText);
  wkt.value = value;
  heading.append(label, copy);
  panel.append(heading, wkt);
  return panel;
}

function createSpaceScene(canvas, spaces, interfaces, selectedSpaceGuid, visibleLayers) {
  const surfaces = [
    ...spaces.flatMap((space) => (space.faces || []).map((points) =>
      ({ points, kind: "space", spaceGuid: space.guid }))),
    ...interfaces.flatMap((item) => (item.faces || []).map((points) =>
      ({ points, kind: "interface", spaceGuid: item.spaceGuid })))
  ];
  const allPoints = surfaces.flatMap((surface) => surface.points);
  const center = [0, 1, 2].map((axis) => {
    let low = Infinity, high = -Infinity;
    for (const point of allPoints) {
      low = Math.min(low, point[axis]);
      high = Math.max(high, point[axis]);
    }
    return allPoints.length ? (low + high) / 2 : 0;
  });
  let yaw = 0;
  let pitch = 0.7;
  let zoom = 1;
  let dragging = null;
  let frame = null;

  function render() {
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    if (!width || !height) return;
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    const pixelWidth = Math.round(width * pixelRatio);
    const pixelHeight = Math.round(height * pixelRatio);
    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
    }
    const context = canvas.getContext("2d");
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    context.clearRect(0, 0, width, height);
    if (!surfaces.length) {
      context.fillStyle = "#5e736b";
      context.font = '13px "Manrope", sans-serif';
      context.textAlign = "center";
      context.fillText("No 3D geometry is available in this model.", width / 2, height / 2);
      return;
    }
    const visible = visibleLayers();
    canvas.setAttribute("aria-label", visible.space && visible.interface
      ? "Interactive 3D view of spaces and interface surfaces"
      : visible.space ? "Interactive 3D view of spaces"
      : visible.interface ? "Interactive 3D view of interface surfaces"
      : "3D view with both layers hidden");
    if (!visible.space && !visible.interface) {
      context.fillStyle = "#5e736b";
      context.font = '13px "Manrope", sans-serif';
      context.textAlign = "center";
      context.fillText("Choose a layer to show in 3D.", width / 2, height / 2);
      return;
    }

    const project = (point) => projectWorldPoint(point, center, yaw, pitch);
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    const projected = surfaces.map((surface) => {
      const points = surface.points.map(project);
      for (const [x, y] of points) {
        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
      }
      return { ...surface, points, depth: points.reduce((sum, point) => sum + point[2], 0) / points.length };
    });
    const scale = Math.min((width - 48) / Math.max(maxX - minX, 0.01),
      (height - 48) / Math.max(maxY - minY, 0.01)) * zoom;
    const offsetX = width / 2 - (minX + maxX) * scale / 2;
    const offsetY = height / 2 - (minY + maxY) * scale / 2;
    const selected = selectedSpaceGuid();
    projected.sort((first, second) => second.depth - first.depth);
    for (const kind of ["space", "interface"]) {
      if (!visible[kind]) continue;
      for (const surface of projected) {
        if (surface.kind !== kind) continue;
        const focused = !selected || surface.spaceGuid === selected;
        context.beginPath();
        surface.points.forEach(([x, y], index) => {
          if (index) context.lineTo(offsetX + x * scale, offsetY + y * scale);
          else context.moveTo(offsetX + x * scale, offsetY + y * scale);
        });
        context.closePath();
        context.fillStyle = kind === "space"
          ? focused ? "rgba(75,155,118,.19)" : "rgba(75,155,118,.035)"
          : focused ? "rgba(241,111,70,.38)" : "rgba(241,111,70,.06)";
        context.strokeStyle = kind === "space"
          ? focused ? "rgba(23,79,62,.5)" : "rgba(23,79,62,.09)"
          : focused ? "rgba(169,65,32,.8)" : "rgba(169,65,32,.12)";
        context.lineWidth = kind === "interface" ? 1.2 : .7;
        context.fill();
        context.stroke();
      }
    }
  }

  function scheduleRender() {
    if (frame !== null) return;
    frame = requestAnimationFrame(() => { frame = null; render(); });
  }

  canvas.addEventListener("pointerdown", (event) => {
    dragging = { x: event.clientX, y: event.clientY };
    canvas.setPointerCapture(event.pointerId);
    canvas.classList.add("is-dragging");
  });
  canvas.addEventListener("pointermove", (event) => {
    if (!dragging) return;
    yaw += (event.clientX - dragging.x) * .008;
    pitch = Math.max(.15, Math.min(1.45, pitch + (event.clientY - dragging.y) * .008));
    dragging = { x: event.clientX, y: event.clientY };
    scheduleRender();
  });
  const stopDragging = () => { dragging = null; canvas.classList.remove("is-dragging"); };
  canvas.addEventListener("pointerup", stopDragging);
  canvas.addEventListener("pointercancel", stopDragging);
  canvas.addEventListener("wheel", (event) => {
    event.preventDefault();
    zoom = Math.max(.4, Math.min(6, zoom * Math.exp(-event.deltaY * .001)));
    scheduleRender();
  }, { passive: false });
  canvas.addEventListener("dblclick", () => {
    yaw = 0; pitch = .7; zoom = 1;
    scheduleRender();
  });
  window.addEventListener("resize", scheduleRender);
  return {
    render,
    destroy() {
      window.removeEventListener("resize", scheduleRender);
      if (frame !== null) cancelAnimationFrame(frame);
    }
  };
}

function renderFloorplanLines(svg, lines, features = []) {
  const points = [...lines.flat(), ...features.flatMap((feature) => feature.outline || [])];
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (const [x, y] of points) {
    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
  }
  const scale = Math.min(610 / Math.max(maxX - minX, 0.01), 410 / Math.max(maxY - minY, 0.01));
  const left = (680 - (maxX - minX) * scale) / 2;
  const top = (500 - (maxY - minY) * scale) / 2;
  if (lines.length) {
    const commands = lines.map((line) => line.map((point, index) => {
      const [x, y] = projectPlanPoint(point, maxX, maxY, scale, left, top);
      return `${index ? "L" : "M"}${x.toFixed(2)} ${y.toFixed(2)}`;
    }).join(" "));
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("d", commands.join(" "));
    svg.append(path);
  }
  for (const feature of features) {
    const outline = feature.outline || [];
    if (outline.length < 2) continue;
    const shape = document.createElementNS("http://www.w3.org/2000/svg",
      outline.length > 2 ? "polygon" : "polyline");
    shape.classList.add("floorplan-feature", `floorplan-${feature.type}`);
    shape.setAttribute("points", outline.map((point) => {
      const [x, y] = projectPlanPoint(point, maxX, maxY, scale, left, top);
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    }).join(" "));
    const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
    title.textContent = feature.name || feature.type;
    shape.append(title);
    svg.append(shape);
  }
}

function exportSelection() {
  if ((selectedScopes().includes("floorplan") && model.floorplanError) ||
      (selectedScopes().includes("spaces") && model.geometryError) ||
      (selectedScopes()[0]?.startsWith("products:") && model.productError)) return;
  const turtle = buildTurtle(model, selectedScopes());
  downloadTurtle(turtle, exportFilename(model.fileName, selectedScopes()[0]));
  showToast("Your Linked Building Data is ready.");
}

function downloadTurtle(turtle, filename) {
  const blob = new Blob([turtle], { type: "text/turtle;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function showToast(message) {
  elements.toast.textContent = message;
  elements.toast.classList.add("is-visible");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => elements.toast.classList.remove("is-visible"), 3200);
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
}
