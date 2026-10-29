import unittest

from floorplans import (boundary_geometry, extract_attributes, extract_products, extract_spaces, footprint_geometry,
                        product_category,
                        mesh_geometry, section_segments)


class SectionTest(unittest.TestCase):
    def test_triangle_intersects_horizontal_plane(self):
        vertices = [0, 0, 0, 2, 0, 2, 0, 2, 2]
        self.assertEqual(section_segments(vertices, [0, 1, 2], 1),
                         {((0.0, 1.0), (1.0, 0.0))})

    def test_triangle_outside_plane_is_empty(self):
        self.assertEqual(section_segments([0, 0, 0, 1, 0, 0, 0, 1, 0], [0, 1, 2], 1), set())

    def test_space_mesh_is_3d_wkt(self):
        geometry = mesh_geometry([0, 0, 0, 1, 0, 0, 0, 1, 2], [0, 1, 2])
        self.assertEqual(geometry["wkt"],
                         "POLYHEDRALSURFACE Z (((0 0 0, 1 0 0, 0 1 2, 0 0 0)))")
        self.assertEqual(len(geometry["faces"]), 1)

    def test_explicit_boundary_is_transformed_into_3d_world_coordinates(self):
        import ifcopenshell
        from pathlib import Path

        model = ifcopenshell.open(str(Path(__file__).resolve().parents[2] /
                                     "IFCtoLBD/src/main/resources/Duplex_A.ifc"))
        boundary = model.by_type("IfcRelSpaceBoundary")[0]
        geometry = boundary_geometry(boundary, 1)
        self.assertTrue(geometry["wkt"].startswith("POLYGON Z ((6.226 -10.995 0.019,"))
        self.assertEqual(len(geometry["faces"][0]), 4)

    def test_duplex_wall_boundaries_and_misplaced_ceiling_are_visible(self):
        import ifcopenshell
        from pathlib import Path

        model = ifcopenshell.open(str(Path(__file__).resolve().parents[2] / "Duplex.ifc"))
        spaces, interfaces = extract_spaces(model, 1)
        self.assertEqual(len(interfaces), 265)
        self.assertTrue(all(interface.get("faces") for interface in interfaces))
        wall = next(interface for interface in interfaces
                    if interface["guid"] == "3fWnuEx3P0og1rpzyjtqAc")
        self.assertEqual(wall["faces"][0][0], [0.2085, -17.5915, 0])
        self.assertEqual(wall["faces"][0][2], [6.262, -17.5915, 2.6])
        ceiling = next(interface for interface in interfaces
                       if interface["guid"] == "2ougjVQnTBXxJ5srz3ladJ")
        self.assertEqual(ceiling["faces"][0][0], [6.418, -6.25, 5.719])
        room = next(interface for interface in interfaces
                    if interface["guid"] == "1CxRzFYOv6uh1cdV2K8ERB")
        self.assertGreater(min(point[1] for point in room["faces"][0]), -12)
        bounds = {}
        for space in spaces:
            points = [point for face in space["faces"] for point in face]
            bounds[space["guid"]] = ([min(point[axis] for point in points) for axis in range(3)],
                                     [max(point[axis] for point in points) for axis in range(3)])
        for interface in interfaces:
            low, high = bounds[interface["spaceGuid"]]
            excess = max(max(low[axis] - point[axis], point[axis] - high[axis], 0)
                         for face in interface["faces"] for point in face for axis in range(3))
            self.assertLess(excess, 0.6, interface["guid"])

    def test_high_window_footprint_is_visible_in_plan(self):
        geometry = footprint_geometry([0, 0, 3, 2, 0, 3, 2, 0.2, 4, 0, 0.2, 4])
        self.assertTrue(geometry["wkt"].startswith("POLYGON (("))
        self.assertEqual({tuple(point) for point in geometry["outline"]},
                         {(0, 0), (2, 0), (2, 0.2), (0, 0.2)})

    def test_product_inventory_includes_all_nonspatial_classes_and_properties(self):
        import ifcopenshell
        from pathlib import Path

        model = ifcopenshell.open(str(Path(__file__).resolve().parents[2] /
                                     "IFCtoLBD/src/main/resources/Duplex_A.ifc"))
        products = extract_products(model)
        self.assertEqual(len(products), len(model.by_type("IfcElement")))
        covering = next(product for product in products if product["ifcType"] == "IfcCovering")
        self.assertTrue(covering["guid"])
        self.assertTrue(any(product["propertySets"] for product in products))
        self.assertTrue(any(product["materials"] for product in products))
        self.assertEqual(covering["attributes"]["GlobalId"], covering["guid"])
        self.assertNotIn("Description", covering["attributes"])
        self.assertNotIn("OwnerHistory", covering["attributes"])
        self.assertNotIn("ObjectPlacement", covering["attributes"])
        self.assertNotIn("Representation", covering["attributes"])

    def test_direct_ifc_attributes_are_json_serializable(self):
        import ifcopenshell
        import json
        from pathlib import Path

        model = ifcopenshell.open(str(Path(__file__).resolve().parents[2] /
                                     "IFCtoLBD/src/main/resources/Duplex_A.ifc"))
        attributes = extract_attributes(model.by_type("IfcCovering")[0])
        self.assertNotIn("Description", attributes)
        self.assertNotIn("OwnerHistory", attributes)
        self.assertNotIn("ObjectPlacement", attributes)
        self.assertNotIn("Representation", attributes)
        self.assertEqual(json.loads(json.dumps(attributes)), attributes)

    def test_product_inventory_handles_missing_material_references_in_ifc4(self):
        import ifcopenshell
        from pathlib import Path

        model = ifcopenshell.open(str(Path(__file__).resolve().parents[2] /
                                     "IFCtoLBD/src/test/resources/materials/materials-ifc4.ifc"))
        products = extract_products(model)
        self.assertEqual(len(products), len(model.by_type("IfcElement")))

    def test_product_categories_cover_ifc_building_and_distribution_classes(self):
        import ifcopenshell

        for schema in ("IFC2X3", "IFC4"):
            model = ifcopenshell.file(schema=schema)
            for ifc_class, expected in (
                ("IfcWall", "building"), ("IfcFurnishingElement", "furnishing"),
                ("IfcOpeningElement", "other")
            ):
                with self.subTest(schema=schema, ifc_class=ifc_class):
                    self.assertEqual(product_category(model.create_entity(ifc_class)), expected)
            for type_class, expected in (
                ("IfcDuctSegmentType", "hvac"), ("IfcCableSegmentType", "electrical"),
                ("IfcPipeSegmentType", "plumbing"), ("IfcFlowMeterType", "services")
            ):
                with self.subTest(schema=schema, type_class=type_class):
                    segment = model.create_entity("IfcFlowSegment")
                    self.assertEqual(product_category(segment, model.create_entity(type_class)), expected)

    def test_storeys_only_cli_keeps_mcp_response_small(self):
        import json
        import subprocess
        import sys
        from pathlib import Path

        root = Path(__file__).resolve().parents[2]
        output = subprocess.check_output([
            sys.executable, str(root / "OneClick/floorplans.py"),
            str(root / "IFCtoLBD/src/main/resources/Duplex_A.ifc"), "--storeys-only"
        ], text=True)
        data = json.loads(output)
        self.assertEqual(set(data), {"storeys"})
        self.assertTrue(data["storeys"])


if __name__ == "__main__":
    unittest.main()
