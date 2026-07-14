#!/usr/bin/env python3
import json
import multiprocessing
import sys
import traceback


def _as_float(value, default=0.0):
    try:
        return float(value)
    except Exception:
        return default


def _color_channels(color):
    if color is None:
        return None
    if hasattr(color, "r") and hasattr(color, "g") and hasattr(color, "b"):
        return [_as_float(color.r), _as_float(color.g), _as_float(color.b)]
    try:
        values = list(color)
    except TypeError:
        return None
    if len(values) < 3:
        return None
    return [_as_float(values[0]), _as_float(values[1]), _as_float(values[2])]


def _material_to_json(material, index):
    diffuse = _color_channels(getattr(material, "diffuse", None))
    if diffuse is None:
        diffuse = _color_channels(getattr(material, "surface", None))
    if diffuse is None:
        return None
    alpha = _as_float(getattr(material, "transparency", 0.0))
    if alpha > 0.0:
        alpha = 1.0 - alpha
    else:
        alpha = _as_float(getattr(material, "alpha", 1.0), 1.0)
    return {
        "name": getattr(material, "name", None) or f"material_{index}",
        "diffuse": diffuse,
        "alpha": max(0.0, min(1.0, alpha)),
    }


def _bbox(verts):
    if not verts:
        return None
    xs = verts[0::3]
    ys = verts[1::3]
    zs = verts[2::3]
    return [min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)]


def main():
    if len(sys.argv) != 2:
        print("Usage: ifcopenshell_geometry_iterator.py <model.ifc>", file=sys.stderr)
        return 2

    import ifcopenshell
    import ifcopenshell.geom

    ifc_path = sys.argv[1]
    ifc_file = ifcopenshell.open(ifc_path)

    settings = ifcopenshell.geom.settings()
    settings.set("use-world-coords", True)
    try:
        settings.set("apply-default-materials", True)
    except Exception:
        pass

    iterator = ifcopenshell.geom.iterator(settings, ifc_file, multiprocessing.cpu_count())
    if not iterator.initialize():
        return 0

    while True:
        shape = iterator.get()
        element = ifc_file.by_id(shape.id)
        guid = getattr(element, "GlobalId", None)
        if guid:
            geometry = shape.geometry
            verts = list(getattr(geometry, "verts", []) or [])
            faces = list(getattr(geometry, "faces", []) or [])
            materials = []
            for index, material in enumerate(list(getattr(geometry, "materials", []) or [])):
                material_json = _material_to_json(material, index)
                if material_json is not None:
                    materials.append(material_json)
            print(json.dumps({
                "guid": guid,
                "bbox": _bbox(verts),
                "vertices": verts,
                "faces": faces,
                "materials": materials,
            }, separators=(",", ":")), flush=True)
        if not iterator.next():
            break
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
