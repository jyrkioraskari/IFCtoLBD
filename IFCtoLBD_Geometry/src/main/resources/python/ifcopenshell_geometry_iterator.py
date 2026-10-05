#!/usr/bin/env python3
import multiprocessing
import struct
import sys
import traceback


_PROTOCOL_MAGIC = 0x49464347  # IFCG
_PROTOCOL_VERSION = 1
_RECORD_MAGIC = 0x47454F4D  # GEOM
_BINARY_CHUNK_VALUES = 8192


def _as_float(value, default=0.0):
    try:
        return float(value)
    except Exception:
        return default


def _color_channels(color):
    if color is None:
        return None
    if hasattr(color, "r") and hasattr(color, "g") and hasattr(color, "b"):
        red = color.r() if callable(color.r) else color.r
        green = color.g() if callable(color.g) else color.g
        blue = color.b() if callable(color.b) else color.b
        return [_as_float(red), _as_float(green), _as_float(blue)]
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
    xmin = xmax = float(verts[0])
    ymin = ymax = float(verts[1])
    zmin = zmax = float(verts[2])
    for index in range(3, len(verts) - 2, 3):
        x = float(verts[index])
        y = float(verts[index + 1])
        z = float(verts[index + 2])
        xmin = min(xmin, x)
        ymin = min(ymin, y)
        zmin = min(zmin, z)
        xmax = max(xmax, x)
        ymax = max(ymax, y)
        zmax = max(zmax, z)
    return [xmin, ymin, zmin, xmax, ymax, zmax]


def _write_string(output, value):
    encoded = str(value).encode("utf-8")
    output.write(struct.pack(">I", len(encoded)))
    output.write(encoded)


def _write_values(output, values, format_code):
    output.write(struct.pack(">I", len(values)))
    for offset in range(0, len(values), _BINARY_CHUNK_VALUES):
        chunk = values[offset:offset + _BINARY_CHUNK_VALUES]
        output.write(struct.pack(f">{len(chunk)}{format_code}", *chunk))


def _write_record(output, guid, bbox, vertices, faces, materials):
    output.write(struct.pack(">I", _RECORD_MAGIC))
    _write_string(output, guid)
    output.write(b"\x01" if bbox is not None else b"\x00")
    if bbox is not None:
        output.write(struct.pack(">6d", *bbox))
    _write_values(output, vertices, "d")
    _write_values(output, faces, "i")
    output.write(struct.pack(">I", len(materials)))
    for material in materials:
        _write_string(output, material["name"])
        output.write(struct.pack(">4d", *material["diffuse"], material["alpha"]))


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
    output = sys.stdout.buffer
    output.write(struct.pack(">II", _PROTOCOL_MAGIC, _PROTOCOL_VERSION))
    output.flush()
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
            _write_record(output, guid, _bbox(verts), verts, faces, materials)
        if not iterator.next():
            break
    output.flush()
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
