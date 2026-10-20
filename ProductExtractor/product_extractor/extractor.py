"""Extract per-element summaries from IFCtoLBD Linked Building Data (LBD) Turtle.

The extractor reads the merged Turtle (or TriG) output of IFCtoLBD and builds,
for every building element (a resource typed ``bot:Element``), a JSON-ready
summary with:

- IFC attributes (the generated ``*_attribute_simple`` predicates, or their
  simplified ``lbd:`` equivalents, identified through their
  ``rdfs:comment "IFC standard attribute ..."`` marker),
- associated property sets and their values (level-1 flattened predicates,
  OPM level 2/3 property resources, and bSDD-typed property-set resources),
- materials and material layers (the opt-in ``materials-bmo`` profile output
  using BMO classes and the ``ifctolbd:`` material-assignment extension).

All output collections are sorted so repeated runs over the same input produce
byte-identical JSON.
"""

import json
import re
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

from rdflib import BNode, Graph, Dataset, Literal, URIRef
from rdflib.namespace import DCTERMS, OWL, RDF, RDFS

from . import namespaces as ns

__all__ = [
    "load_graph",
    "parse_graph_text",
    "find_elements",
    "element_type_counts",
    "parse_type_filter",
    "summarize_graph",
    "summarize_file",
    "write_json",
]

# "IFC property set <PsetName> property <PropertyName>" (PropertySet.java).
_PROPERTY_COMMENT_RE = re.compile(r"^IFC property set (.+?) property (.+)$")

# inst:IfcWall_13698 -> IfcWall (IFC OWL resource local name, STEP id stripped).
_IFC_LOCAL_NAME_RE = re.compile(r"^(Ifc\w+?)_\d+$")

# Namespaces whose predicates may carry IFC attributes or property values.
_PROPERTY_NAMESPACES = (str(ns.PROPS), str(ns.LBD), str(ns.BSDD_PROP))


def load_graph(path):
    """Parse an LBD Turtle file (or a TriG/N-Quads file) into one rdflib Graph."""
    path = Path(path)
    suffix = path.suffix.lower()
    if suffix in (".trig", ".nq", ".nquads"):
        # TriG/N-Quads carry several named graphs; merge them into one view.
        dataset = Dataset()
        dataset.parse(source=str(path), format="trig" if suffix == ".trig" else "nquads")
        return _merge_dataset(dataset)
    graph = Graph()
    graph.parse(source=str(path), format="turtle")
    return graph


def _merge_dataset(dataset):
    """Merge every graph of a parsed TriG/N-Quads dataset into one Graph."""
    graph = Graph()
    for context in dataset.graphs():
        for triple in context:
            graph.add(triple)
    return graph


def parse_graph_text(text):
    """Parse Turtle (or TriG/N-Quads) text into one merged rdflib Graph.

    Raises the parser's exception when the text is neither valid Turtle nor
    valid TriG.
    """
    graph = Graph()
    try:
        graph.parse(data=text, format="turtle")
        return graph
    except Exception:
        pass
    dataset = Dataset()
    dataset.parse(data=text, format="trig")
    return _merge_dataset(dataset)


def local_name(uri):
    """Return the local name of an IRI or prefixed name (``beo:Wall`` -> ``Wall``)."""
    text = str(uri)
    if "#" in text:
        return text.rsplit("#", 1)[-1]
    if "/" in text:
        return text.rsplit("/", 1)[-1]
    if ":" in text:
        return text.rsplit(":", 1)[-1]
    return text


def json_value(node):
    """Convert an rdflib node to a JSON-serialisable Python value."""
    if isinstance(node, Literal):
        value = node.toPython()
    else:
        return str(node)
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, bytes):
        return value.decode("utf-8", "replace")
    return value


def find_elements(graph):
    """Return all building elements (resources typed ``bot:Element``), sorted by IRI."""
    return sorted(graph.subjects(RDF.type, ns.ELEMENT_CLASS), key=str)


def _element_type_names(graph, element):
    """Local names of all URI-typed ``rdf:type`` values of the element."""
    return {
        local_name(t)
        for t in graph.objects(element, RDF.type)
        if isinstance(t, URIRef)
    }


def element_type_counts(graph):
    """Count element occurrences per ``rdf:type`` local name (for --list-types)."""
    counts = {}
    for element in find_elements(graph):
        for name in _element_type_names(graph, element):
            counts[name] = counts.get(name, 0) + 1
    return dict(sorted(counts.items(), key=lambda item: (-item[1], item[0])))


def parse_type_filter(types):
    """Normalise a comma-separated type filter (or list) to lowercase local names.

    Accepts plain local names (``Wall``), prefixed names (``beo:Wall``) and full
    IRIs; matching against element types is case-insensitive.
    """
    if not types:
        return []
    if isinstance(types, str):
        entries = types.split(",")
    else:
        entries = []
        for item in types:
            entries.extend(str(item).split(","))
    normalised = []
    for entry in entries:
        entry = entry.strip()
        if not entry:
            continue
        normalised.append(local_name(entry).lower())
    return normalised


def _matches_type_filter(graph, element, type_filter):
    if not type_filter:
        return True
    names = {name.lower() for name in _element_type_names(graph, element)}
    return any(entry in names for entry in type_filter)


def _first(graph, subject, predicate):
    for obj in graph.objects(subject, predicate):
        return obj
    return None


def _text(graph, subject, predicate):
    value = _first(graph, subject, predicate)
    return None if value is None else str(value)


def _ifc_type(graph, element):
    """IFC entity type of the element, from its ``owl:sameAs`` IFC OWL link."""
    for target in sorted(graph.objects(element, OWL.sameAs), key=str):
        if not isinstance(target, URIRef):
            continue
        for type_node in sorted(graph.objects(target, RDF.type), key=str):
            if isinstance(type_node, URIRef) and str(type_node).startswith(str(ns.IFC_OWL)):
                return local_name(type_node)
        match = _IFC_LOCAL_NAME_RE.match(local_name(target))
        if match:
            return match.group(1)
    return None


def _attribute_name(graph, predicate):
    """IFC attribute name for a predicate, from its comment marker or local name."""
    for comment in graph.objects(predicate, RDFS.comment):
        text = str(comment)
        if text.startswith(ns.ATTRIBUTE_COMMENT_PREFIX):
            return text[len(ns.ATTRIBUTE_COMMENT_PREFIX):].strip()
    name = local_name(predicate)
    if name.endswith(ns.ATTRIBUTE_SUFFIX):
        return name[: -len(ns.ATTRIBUTE_SUFFIX)]
    return name


def _property_comment_groups(graph, predicate):
    """(propertySetName, propertyName) pairs declared in the predicate's comments."""
    groups = []
    for comment in graph.objects(predicate, RDFS.comment):
        match = _PROPERTY_COMMENT_RE.match(str(comment))
        if match:
            groups.append((match.group(1), match.group(2)))
    return groups


def _is_property_predicate(graph, predicate):
    """True when the predicate carries IFC property values.

    Recognises generated predicates by namespace and ``_property_simple``
    suffix, bSDD property IRIs, and the ``rdfs:comment "IFC property set ..."``
    marker (the only reliable signal in simplified-properties mode, where the
    suffix is omitted).
    """
    text = str(predicate)
    if not text.startswith(_PROPERTY_NAMESPACES):
        return False
    if text.startswith(str(ns.BSDD_PROP)):
        return True
    if local_name(predicate).endswith(ns.PROPERTY_SUFFIX):
        return True
    return bool(_property_comment_groups(graph, predicate))


def extract_attributes(graph, element):
    """IFC attributes of the element as an ordered ``{name: value}`` dict."""
    attributes = {}
    for predicate in sorted(set(graph.predicates(element)), key=str):
        if predicate in (RDF.type, RDFS.label) or predicate in ns.STRUCTURAL_PREDICATES:
            continue
        text = str(predicate)
        has_attribute_comment = any(
            str(c).startswith(ns.ATTRIBUTE_COMMENT_PREFIX)
            for c in graph.objects(predicate, RDFS.comment)
        )
        has_attribute_suffix = text.startswith(ns.GENERATED_PROPERTY_NAMESPACES) and local_name(
            predicate
        ).endswith(ns.ATTRIBUTE_SUFFIX)
        if not (has_attribute_comment or has_attribute_suffix):
            continue
        values = [json_value(v) for v in sorted(graph.objects(element, predicate), key=str)]
        attributes[_attribute_name(graph, predicate)] = values[0] if len(values) == 1 else values
    return dict(sorted(attributes.items()))


def _opm_property_value(graph, property_resource):
    """Value of an OPM property resource (level 2 direct, level 3 via state)."""
    for value in graph.objects(property_resource, ns.SCHEMA.value):
        return json_value(value)
    for state in sorted(graph.objects(property_resource, ns.OPM.hasPropertyState), key=str):
        for value in graph.objects(state, ns.SCHEMA.value):
            return json_value(value)
    return None


def _opm_property_name(graph, property_resource, fallback):
    label = _text(graph, property_resource, RDFS.label)
    if label and ":" in label:
        return label.split(":", 1)[1]
    for type_node in graph.objects(property_resource, RDF.type):
        if isinstance(type_node, URIRef) and str(type_node).startswith(str(ns.BSDD_PROP)):
            return local_name(type_node)
    return fallback


def _opm_property_set_name(graph, property_resource, fallback):
    label = _text(graph, property_resource, RDFS.label)
    if label and ":" in label:
        return label.split(":", 1)[0]
    return fallback


def extract_property_sets(graph, element):
    """Associated property sets of the element.

    Returns a list of ``{"name": pset, "properties": {prop: value}}`` sorted by
    property-set name. Handles all converter representations: level-1 flattened
    predicates (grouped through the predicate's ``rdfs:comment`` markers), OPM
    level 2/3 property resources, and bSDD-typed property-set resources.
    """
    groups = {}

    def add(pset_name, prop_name, value):
        properties = groups.setdefault(pset_name, {})
        properties[prop_name] = value

    # bSDD-typed property sets (--propertiesAsPropertySets): hasPropertySet ->
    # containsProperty -> property resource with an OPM property state.
    for pset_resource in sorted(graph.objects(element, ns.BSDD_META.hasPropertySet), key=str):
        pset_name = _text(graph, pset_resource, RDFS.label)
        if not pset_name:
            for type_node in graph.objects(pset_resource, RDF.type):
                if isinstance(type_node, URIRef) and "identifier.buildingsmart.org" in str(type_node):
                    pset_name = local_name(type_node)
                    break
        if not pset_name:
            pset_name = local_name(pset_resource)
        for prop_resource in sorted(
            graph.objects(pset_resource, ns.BSDD_META.containsProperty), key=str
        ):
            add(
                pset_name,
                _opm_property_name(graph, prop_resource, local_name(prop_resource)),
                _opm_property_value(graph, prop_resource),
            )

    for predicate in sorted(set(graph.predicates(element)), key=str):
        if predicate in (RDF.type, RDFS.label) or predicate in ns.STRUCTURAL_PREDICATES:
            continue
        if not _is_property_predicate(graph, predicate):
            continue
        objects = sorted(graph.objects(element, predicate), key=str)
        resources = [o for o in objects if isinstance(o, (URIRef, BNode))]
        if resources:
            # OPM level 2/3: the predicate points at property resources.
            for prop_resource in resources:
                fallback = local_name(predicate)
                if fallback.endswith(ns.PROPERTY_SUFFIX):
                    fallback = fallback[: -len(ns.PROPERTY_SUFFIX)]
                add(
                    _opm_property_set_name(graph, prop_resource, fallback),
                    _opm_property_name(graph, prop_resource, fallback),
                    _opm_property_value(graph, prop_resource),
                )
            continue
        # Level 1: literal values on a generated predicate. The predicate is
        # shared by every property set that recognises the property name, so
        # the value is listed under each candidate property set.
        comment_groups = _property_comment_groups(graph, predicate)
        pset_names = [pset for pset, _ in comment_groups]
        if not pset_names:
            pset_names = [local_name(predicate)]
        prop_name = comment_groups[0][1] if comment_groups else local_name(predicate)
        if prop_name.endswith(ns.PROPERTY_SUFFIX):
            prop_name = prop_name[: -len(ns.PROPERTY_SUFFIX)]
        value = json_value(objects[0]) if len(objects) == 1 else [json_value(o) for o in objects]
        for pset_name in pset_names:
            add(pset_name, prop_name, value)

    return [
        {"name": pset_name, "properties": dict(sorted(groups[pset_name].items()))}
        for pset_name in sorted(groups)
    ]


def _material_summary(graph, material):
    return {
        "iri": str(material),
        "name": _text(graph, material, RDFS.label),
        "description": _text(graph, material, DCTERMS.description),
        "category": _text(graph, material, ns.IFCTOLBD.materialCategory),
    }


def _layer_summary(graph, layer, position):
    material = _first(graph, layer, ns.BMO.hasMaterial)
    thickness = _first(graph, layer, ns.BMO.hasThickness)
    thickness_unit = None
    thickness_source_value = None
    quantity = _first(graph, layer, ns.IFCTOLBD.thicknessQuantity)
    if quantity is not None:
        unit = _first(graph, quantity, ns.QUDT.unit)
        thickness_unit = None if unit is None else str(unit)
        source_value = _first(graph, quantity, ns.UNIT_META.originalValue)
        thickness_source_value = None if source_value is None else json_value(source_value)
    return {
        "position": position,
        "iri": str(layer),
        "material": None if material is None else _material_summary(graph, material),
        "thickness": None if thickness is None else json_value(thickness),
        "thicknessUnit": thickness_unit,
        "thicknessSourceValue": thickness_source_value,
    }


def _layer_set_summary(graph, layer_set):
    layers = []
    memberships = sorted(graph.objects(layer_set, ns.IFCTOLBD.layerMembership), key=str)
    for membership in memberships:
        position = _first(graph, membership, ns.IFCTOLBD.position)
        layer = _first(graph, membership, ns.IFCTOLBD.layer)
        if layer is None:
            continue
        layers.append(
            _layer_summary(graph, layer, int(position) if position is not None else len(layers) + 1)
        )
    if not layers:
        # Fall back to the unordered bmo:hasLayers links when no membership
        # resources carry the one-based positions.
        for index, layer in enumerate(sorted(graph.objects(layer_set, ns.BMO.hasLayers), key=str), 1):
            layers.append(_layer_summary(graph, layer, index))
    return {
        "iri": str(layer_set),
        "name": _text(graph, layer_set, RDFS.label),
        "description": _text(graph, layer_set, DCTERMS.description),
        "layers": layers,
    }


def _usage_summary(graph, usage):
    offset = _first(graph, usage, ns.IFCTOLBD.offsetFromReferenceLine)
    return {
        "iri": str(usage),
        "layerSetDirection": _text(graph, usage, ns.IFCTOLBD.layerSetDirection),
        "directionSense": _text(graph, usage, ns.IFCTOLBD.directionSense),
        "offsetFromReferenceLine": None if offset is None else json_value(offset),
    }


def _material_assignment_summary(graph, assignment):
    inherited = _first(graph, assignment, ns.IFCTOLBD.materialAssignmentInherited)
    inherited = bool(inherited.toPython()) if isinstance(inherited, Literal) else False

    material = _first(graph, assignment, ns.IFCTOLBD.assignedMaterial)
    material = None if material is None else _material_summary(graph, material)

    layer_set = None
    definition = _first(graph, assignment, ns.IFCTOLBD.assignedMaterialDefinition)
    if definition is not None:
        if (definition, RDF.type, ns.BMO.LayerSet) in graph:
            layer_set = _layer_set_summary(graph, definition)
        elif (definition, RDF.type, ns.BMO.MaterialDefinition) in graph and material is None:
            target = _first(graph, definition, ns.BMO.hasMaterial)
            material = None if target is None else _material_summary(graph, target)

    usage = _first(graph, assignment, ns.IFCTOLBD.layerSetUsage)
    usage = None if usage is None else _usage_summary(graph, usage)

    source_type = _text(graph, assignment, ns.IFCTOLBD.sourceIfcType)
    source_type_resource = _first(graph, assignment, ns.IFCTOLBD.sourceIfcTypeResource)

    return {
        "iri": str(assignment),
        "inherited": inherited,
        "sourceIfcType": source_type,
        "sourceIfcTypeResource": None if source_type_resource is None else str(source_type_resource),
        "material": material,
        "layerSet": layer_set,
        "usage": usage,
    }


def extract_materials(graph, element):
    """Material assignments of the element (materials-bmo profile output)."""
    return [
        _material_assignment_summary(graph, assignment)
        for assignment in sorted(graph.objects(element, ns.IFCTOLBD.materialAssignment), key=str)
    ]


def extract_element_summary(graph, element):
    """Full JSON-ready summary of one building element."""
    types = sorted(
        (str(t) for t in graph.objects(element, RDF.type) if isinstance(t, URIRef))
    )
    return {
        "iri": str(element),
        "label": _text(graph, element, RDFS.label),
        "types": types,
        "ifcType": _ifc_type(graph, element),
        "attributes": extract_attributes(graph, element),
        "propertySets": extract_property_sets(graph, element),
        "materials": extract_materials(graph, element),
    }


def summarize_graph(graph, type_filter=None):
    """Summaries for all building elements, optionally filtered by type."""
    type_filter = parse_type_filter(type_filter)
    summaries = []
    for element in find_elements(graph):
        if not _matches_type_filter(graph, element, type_filter):
            continue
        summaries.append(extract_element_summary(graph, element))
    return summaries


def summarize_file(path, type_filter=None):
    """Load an LBD Turtle/TriG file and summarise its building elements."""
    return summarize_graph(load_graph(path), type_filter)


def write_json(summaries, path, compact=False):
    """Write the element summaries as a JSON array to a file ('-' for stdout)."""
    text = json.dumps(
        summaries, indent=None if compact else 2, ensure_ascii=False, sort_keys=False
    )
    if path == "-":
        print(text)
        return
    Path(path).write_text(text + "\n", encoding="utf-8")
