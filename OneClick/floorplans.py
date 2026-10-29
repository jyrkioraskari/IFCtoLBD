#!/usr/bin/env python3
"""Extract storey sections, spaces, and product data from an IFC model."""

import json
import math
import sys


def section_segments(vertices, faces, height):
    segments = set()
    for offset in range(0, len(faces), 3):
        triangle = [vertices[3 * faces[offset + side]:3 * faces[offset + side] + 3]
                    for side in range(3)]
        points = []
        for first, second in ((0, 1), (1, 2), (2, 0)):
            a, b = triangle[first], triangle[second]
            if (a[2] < height < b[2]) or (b[2] < height < a[2]):
                fraction = (height - a[2]) / (b[2] - a[2])
                point = (round(a[0] + fraction * (b[0] - a[0]), 4),
                         round(a[1] + fraction * (b[1] - a[1]), 4))
                points.append(point)
        if len(points) == 2 and points[0] != points[1]:
            segments.add(tuple(sorted(points)))
    return segments


def footprint_geometry(vertices):
    """Project an opening's mesh onto the floorplan, including high windows."""
    from shapely.geometry import MultiPoint

    points = sorted({(coordinate(vertices[index]), coordinate(vertices[index + 1]))
                     for index in range(0, len(vertices), 3)})
    if len(points) < 2:
        return None
    hull = MultiPoint(points).convex_hull
    if hull.geom_type == "Polygon":
        outline = [[coordinate(x), coordinate(y)] for x, y in list(hull.exterior.coords)[:-1]]
        wkt = "POLYGON ((" + ", ".join(point_wkt(point) for point in outline + [outline[0]]) + "))"
    elif hull.geom_type == "LineString":
        outline = [[coordinate(x), coordinate(y)] for x, y in hull.coords]
        wkt = "LINESTRING (" + ", ".join(point_wkt(point) for point in outline) + ")"
    else:
        return None
    return {"outline": outline, "wkt": wkt}


def coordinate(value):
    return round(float(value), 4)


def point_wkt(point):
    return " ".join(f"{coordinate(value):g}" for value in point)


def polygon_wkt(points):
    ring = points if points[0] == points[-1] else points + [points[0]]
    return "((" + ", ".join(point_wkt(point) for point in ring) + "))"


def mesh_geometry(vertices, faces):
    """Keep the IFC tessellation as a 3D polyhedral surface in metres."""
    points = [[coordinate(vertices[index + axis]) for axis in range(3)]
              for index in range(0, len(vertices), 3)]
    triangles = []
    for offset in range(0, len(faces), 3):
        triangle = [points[faces[offset + side]] for side in range(3)]
        if len({tuple(point) for point in triangle}) == 3:
            triangles.append(triangle)
    if not triangles:
        return None
    return {"faces": triangles, "wkt": "POLYHEDRALSURFACE Z (" +
            ", ".join(polygon_wkt(triangle) for triangle in triangles) + ")"}


def boundary_geometry(boundary, scale, space_bounds=None, related_bounds=None):
    """Map supported IFC boundary surfaces into world metres."""
    import ifcopenshell.util.placement

    connection = boundary.ConnectionGeometry
    if not connection or not connection.is_a("IfcConnectionSurfaceGeometry"):
        return None
    surface = connection.SurfaceOnRelatingElement
    if not surface:
        return None
    faces = []
    if surface.is_a("IfcCurveBoundedPlane"):
        curve = surface.OuterBoundary
        if not curve.is_a("IfcPolyline"):
            return None
        plane = ifcopenshell.util.placement.get_axis2placement(surface.BasisSurface.Position)
        faces = [[(plane @ [*vertex.Coordinates[:2], 0, 1])[:3]
                  for vertex in curve.Points]]
    elif surface.is_a("IfcSurfaceOfLinearExtrusion"):
        profile = surface.SweptCurve
        if not profile.is_a("IfcArbitraryOpenProfileDef") or not profile.Curve.is_a("IfcPolyline"):
            return None
        plane = ifcopenshell.util.placement.get_axis2placement(surface.Position)
        direction = surface.ExtrudedDirection.DirectionRatios
        length = math.sqrt(sum(value * value for value in direction))
        if not length:
            return None
        offset = [value * surface.Depth / length for value in direction]
        base = [(plane @ [*vertex.Coordinates[:2], 0, 1])[:3]
                for vertex in profile.Curve.Points]
        rise = plane[:3, :3] @ offset
        for first, second in zip(base, base[1:]):
            faces.append([first, second, second + rise, first + rise])
    else:
        return None
    placement = ifcopenshell.util.placement.get_local_placement(boundary.RelatingSpace.ObjectPlacement)
    transformed = [[[coordinate(value * scale) for value in (placement @ [*local, 1])[:3]]
                    for local in face] for face in faces]
    if space_bounds and all(abs(placement[axis, axis] - 1) < 1e-6
                            for axis in range(3)) and all(abs(placement[axis, other]) < 1e-6
                                                       for axis in range(3) for other in range(3) if axis != other):
        alternative = [[[coordinate(local[axis] * scale +
                                    (placement[2, 3] * scale if axis == 2 else 0))
                         for axis in range(3)] for local in face] for face in faces]

        def distance(polygons, bounds):
            low, high = bounds
            return max(max(low[axis] - point[axis], point[axis] - high[axis], 0)
                       for polygon in polygons for point in polygon for axis in range(3))

        misplaced = distance(transformed, space_bounds) > 1 or (related_bounds and
                     distance(transformed, related_bounds) - distance(alternative, related_bounds) > 1)
        if misplaced and distance(alternative, space_bounds) < 0.6:
            transformed = alternative

    world_faces = []
    for points in transformed:
        cleaned = []
        for point in points:
            if not cleaned or point != cleaned[-1]:
                cleaned.append(point)
        if len(cleaned) > 1 and cleaned[0] == cleaned[-1]:
            cleaned.pop()
        if len({tuple(point) for point in cleaned}) >= 3:
            world_faces.append(cleaned)
    if not world_faces:
        return None
    if len(world_faces) == 1:
        wkt = "POLYGON Z " + polygon_wkt(world_faces[0])
    else:
        wkt = "POLYHEDRALSURFACE Z (" + ", ".join(polygon_wkt(face) for face in world_faces) + ")"
    return {"faces": world_faces, "wkt": wkt}


def extract_spaces(model, scale):
    import ifcopenshell.geom

    settings = ifcopenshell.geom.settings()
    settings.set("use-world-coords", True)
    spaces = []
    for index, space in enumerate(model.by_type("IfcSpace")):
        item = {"guid": space.GlobalId, "name": space.Name or f"Space {index + 1}"}
        if space.Representation:
            try:
                shape = ifcopenshell.geom.create_shape(settings, space)
                item.update(mesh_geometry(shape.geometry.verts, shape.geometry.faces) or {})
            except RuntimeError:
                pass
        spaces.append(item)

    space_points = [point for space in spaces for face in space.get("faces", []) for point in face]
    space_bounds = ([min(point[axis] for point in space_points) for axis in range(3)],
                    [max(point[axis] for point in space_points) for axis in range(3)]) if space_points else None
    related_bounds = {}
    for space in spaces:
        points = [point for face in space.get("faces", []) for point in face]
        if points:
            related_bounds[space["guid"]] = (
                [min(point[axis] for point in points) for axis in range(3)],
                [max(point[axis] for point in points) for axis in range(3)])

    interfaces = []
    for boundary in model.by_type("IfcRelSpaceBoundary"):
        space = boundary.RelatingSpace
        if not space:
            continue
        element = boundary.RelatedBuildingElement
        corresponding = boundary.CorrespondingBoundary if boundary.is_a("IfcRelSpaceBoundary2ndLevel") else None
        item = {"guid": boundary.GlobalId, "spaceGuid": space.GlobalId,
                "correspondingSpaceGuid": (corresponding.RelatingSpace.GlobalId
                                           if corresponding and corresponding.RelatingSpace else None),
                "elementGuid": element.GlobalId if element else None,
                "elementName": (element.Name or element.is_a()) if element else None}
        try:
            item.update(boundary_geometry(boundary, scale, space_bounds,
                                          related_bounds.get(space.GlobalId)) or {})
        except (AttributeError, RuntimeError, TypeError, ValueError):
            pass
        interfaces.append(item)
    return spaces, interfaces


def property_value(value):
    if value is None or isinstance(value, (str, bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else str(value)
    if isinstance(value, (list, tuple)):
        return ", ".join(str(property_value(item)) for item in value)
    return str(value)


def flatten_properties(values, prefix=""):
    properties = []
    for name, value in values.items():
        if name in ("id", "type"):
            continue
        label = f"{prefix}{name}"
        if isinstance(value, dict):
            properties.extend(flatten_properties(value.get("properties", value), f"{label} / "))
        else:
            properties.append({"name": label, "value": property_value(value)})
    return properties


def attribute_value(value):
    if value is None or isinstance(value, (str, bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else str(value)
    if isinstance(value, (list, tuple)):
        return [attribute_value(item) for item in value if item is not None]
    if hasattr(value, "is_a") and hasattr(value, "id"):
        reference = {"ifcType": value.is_a()}
        guid = getattr(value, "GlobalId", None)
        if guid:
            reference["globalId"] = guid
        if not value.id() and len(value) == 1:
            reference["value"] = attribute_value(value[0])
        return reference
    return str(value)


OMITTED_ATTRIBUTES = {"OwnerHistory", "ObjectPlacement", "Representation"}


def extract_attributes(product):
    attributes = {}
    for index in range(len(product)):
        name = product.attribute_name(index)
        value = product[index]
        if name not in OMITTED_ATTRIBUTES and value is not None:
            attributes[name] = attribute_value(value)
    return attributes


HVAC_CLASSES = {
    "IfcAirTerminal", "IfcAirTerminalBox", "IfcAirToAirHeatRecovery", "IfcBoiler",
    "IfcBurner", "IfcChiller", "IfcCoil", "IfcCompressor", "IfcCondenser",
    "IfcCooledBeam", "IfcCoolingTower", "IfcDamper", "IfcDuctFitting",
    "IfcDuctSegment", "IfcDuctSilencer", "IfcFan", "IfcFilter",
    "IfcHeatExchanger", "IfcHumidifier", "IfcSpaceHeater", "IfcUnitaryEquipment"
}
ELECTRICAL_CLASSES = {
    "IfcCableCarrierFitting", "IfcCableCarrierSegment", "IfcCableFitting",
    "IfcCableSegment", "IfcElectricAppliance", "IfcElectricDistributionBoard",
    "IfcElectricFlowStorageDevice", "IfcElectricGenerator", "IfcElectricMotor",
    "IfcElectricTimeControl", "IfcJunctionBox", "IfcLamp", "IfcLightFixture",
    "IfcOutlet", "IfcProtectiveDevice", "IfcProtectiveDeviceTrippingUnit",
    "IfcSolarDevice", "IfcSwitchingDevice", "IfcTransformer"
}
PLUMBING_CLASSES = {
    "IfcInterceptor", "IfcPipeFitting", "IfcPipeSegment", "IfcPump",
    "IfcSanitaryTerminal", "IfcTank", "IfcValve", "IfcWasteTerminal"
}


def product_category(product, type_object=None):
    if product.is_a("IfcBuildingElement") or product.is_a("IfcBuiltElement"):
        return "building"
    if product.is_a("IfcFurnishingElement") or product.is_a("IfcFurniture"):
        return "furnishing"
    if product.is_a("IfcDistributionElement"):
        specific_class = product.is_a()
        if type_object and type_object.is_a().endswith("Type"):
            specific_class = type_object.is_a()[:-4]
        if specific_class in HVAC_CLASSES:
            return "hvac"
        if specific_class in ELECTRICAL_CLASSES:
            return "electrical"
        if specific_class in PLUMBING_CLASSES:
            return "plumbing"
        return "services"
    return "other"


def extract_products(model):
    import ifcopenshell.util.element

    products = []
    for index, product in enumerate(model.by_type("IfcProduct")):
        if product.is_a("IfcSpatialStructureElement") or product.is_a("IfcSpatialElement"):
            continue
        type_object = ifcopenshell.util.element.get_type(product)
        storey = ifcopenshell.util.element.get_container(product, ifc_class="IfcBuildingStorey")
        material_names = list(dict.fromkeys(
            material.Name for material in ifcopenshell.util.element.get_materials(product)
            if material and material.Name))
        property_sets = []
        for name, values in sorted(ifcopenshell.util.element.get_psets(product).items()):
            property_sets.append({"name": name, "properties": flatten_properties(values)})
        products.append({
            "guid": product.GlobalId,
            "ifcType": product.is_a(),
            "category": product_category(product, type_object),
            "name": product.Name or f"{product.is_a()} {index + 1}",
            "description": product.Description,
            "predefinedType": ifcopenshell.util.element.get_predefined_type(product),
            "typeName": (type_object.Name or type_object.is_a()) if type_object else None,
            "storeyGuid": storey.GlobalId if storey else None,
            "storeyName": (storey.Name or "Unnamed storey") if storey else None,
            "materials": material_names,
            "attributes": extract_attributes(product),
            "propertySets": property_sets
        })
    return products


def extract(path, include_details=True):
    import ifcopenshell
    import ifcopenshell.geom
    import ifcopenshell.util.element
    import ifcopenshell.util.placement
    import ifcopenshell.util.unit
    from shapely.geometry import MultiLineString
    from shapely.ops import linemerge, unary_union

    model = ifcopenshell.open(path)
    scale = ifcopenshell.util.unit.calculate_unit_scale(model)
    storeys = sorted(model.by_type("IfcBuildingStorey"),
                    key=lambda item: (float(ifcopenshell.util.placement.get_storey_elevation(item)), item.id()))
    plans = []
    for index, storey in enumerate(storeys):
        elevation = float(ifcopenshell.util.placement.get_storey_elevation(storey)) * scale
        next_elevation = (float(ifcopenshell.util.placement.get_storey_elevation(storeys[index + 1])) * scale
                          if index + 1 < len(storeys) else math.inf)
        cut_height = elevation + min(1.2, max(0.1, (next_elevation - elevation) / 2))
        plans.append({"guid": storey.GlobalId, "name": storey.Name or f"Storey {index + 1}",
                      "elevation": round(elevation, 4), "cutHeight": round(cut_height, 4),
                      "segments": set(), "features": []})

    by_guid = {plan["guid"]: plan for plan in plans}
    products = []
    for ifc_type in ("IfcWall", "IfcDoor", "IfcWindow", "IfcColumn"):
        products.extend(model.by_type(ifc_type))
    products = [item for item in products if item.Representation and
                ifcopenshell.util.element.get_container(item, ifc_class="IfcBuildingStorey")]

    if products:
        settings = ifcopenshell.geom.settings()
        settings.set("use-world-coords", True)
        iterator = ifcopenshell.geom.iterator(settings, model, 1, include=products)
        if iterator.initialize():
            while True:
                shape = iterator.get()
                product = model.by_id(shape.id)
                storey = ifcopenshell.util.element.get_container(product, ifc_class="IfcBuildingStorey")
                plan = by_guid.get(storey.GlobalId) if storey else None
                if plan:
                    if product.is_a("IfcDoor") or product.is_a("IfcWindow"):
                        footprint = footprint_geometry(shape.geometry.verts)
                        if footprint:
                            plan["features"].append({"guid": product.GlobalId,
                                                     "name": product.Name or product.is_a(),
                                                     "type": "door" if product.is_a("IfcDoor") else "window",
                                                     **footprint})
                    else:
                        plan["segments"].update(section_segments(shape.geometry.verts,
                                                                  shape.geometry.faces, plan["cutHeight"]))
                if not iterator.next():
                    break

    for plan in plans:
        segments = sorted(plan.pop("segments"))
        if segments:
            merged = linemerge(unary_union(MultiLineString(segments))).simplify(0.005)
            lines = [merged] if merged.geom_type == "LineString" else list(merged.geoms)
            lines = [list(line.coords) for line in lines if len(line.coords) > 1]
            plan["lines"] = [[[round(x, 4), round(y, 4)] for x, y in line] for line in lines]
            plan["wkt"] = merged.wkt
        else:
            plan["lines"] = []
            plan["wkt"] = None
    if not include_details:
        return plans, [], [], []
    spaces, interfaces = extract_spaces(model, scale)
    return plans, spaces, interfaces, extract_products(model)


if __name__ == "__main__":
    try:
        storeys_only = len(sys.argv) > 2 and sys.argv[2] == "--storeys-only"
        storeys, spaces, interfaces, products = extract(sys.argv[1], include_details=not storeys_only)
        if storeys_only:
            print(json.dumps({"storeys": storeys}))
        else:
            print(json.dumps({"storeys": storeys, "spaces": spaces, "interfaces": interfaces,
                              "products": products}))
    except Exception as error:
        print(f"OneClick extraction failed: {error}", file=sys.stderr)
        sys.exit(1)
