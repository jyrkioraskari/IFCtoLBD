package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javax.vecmath.Point3d;

import org.junit.jupiter.api.Test;

import de.rwth_aachen.dc.lbd.BoundingBox;

class IfcCoordinateReferenceSystemInfoTest {
	private static final String EPSG = "http://www.opengis.net/def/crs/EPSG/0/25832";

	@Test
	void normalizesDirectionAndTransformsMetreCoordinatesIncludingZ() {
		var crs = info(Map.of("eastings", "100", "northings", "200", "orthogonalHeight", "300",
				"xAxisAbscissa", "0", "xAxisOrdinate", "2", "scale", "1"), "IfcMapConversion", 1);
		var point = crs.transform(1, 2, 3, 1);
		assertPoint(point, 98, 201, 303);
	}

	@Test
	void convertsProviderMetresBackToMillimetreOperationSourceWithoutDoubleScaling() {
		var crs = info(Map.of("eastings", "500000", "northings", "6000000", "orthogonalHeight", "40",
				"scale", "0.001"), "IfcMapConversion", 0.001);
		assertPoint(crs.transform(2, 3, 4, 1), 500002, 6000003, 44);
	}

	@Test
	void leavesAlreadyNormalizedMetreInputAtOneToOneScale() {
		var crs = info(Map.of("eastings", "1", "northings", "2", "orthogonalHeight", "3"),
				"IfcMapConversion", 1);
		assertPoint(crs.transform(4, 5, 6, 1), 5, 7, 9);
	}

	@Test
	void appliesScaledFactorsBeforeRotation() {
		var crs = info(Map.of("eastings", "10", "northings", "20", "orthogonalHeight", "30",
				"xAxisAbscissa", "0", "xAxisOrdinate", "4", "scale", "2",
				"factorX", "3", "factorY", "5", "factorZ", "7"), "IfcMapConversionScaled", 1);
		assertPoint(crs.transform(1, 2, 3, 1), -10, 26, 72);
	}

	@Test
	void usesOnlySchemaDefaultsForOmittedOptionalValues() {
		var crs = info(Map.of("eastings", "10", "northings", "20", "orthogonalHeight", "30"),
				"IfcMapConversion", 1);
		assertPoint(crs.transform(1, 2, 3, 1), 11, 22, 33);

		// When exactly one direction component is supplied, IFC defaults only the
		// missing component to zero (not the whole direction to (1, 0)).
		var ordinateOnly = info(Map.of("eastings", "0", "northings", "0", "orthogonalHeight", "0",
				"xAxisOrdinate", "2"), "IfcMapConversion", 1);
		assertPoint(ordinateOnly.transform(1, 0, 0, 1), 0, 1, 0);
		var abscissaOnly = info(Map.of("eastings", "0", "northings", "0", "orthogonalHeight", "0",
				"xAxisAbscissa", "-2"), "IfcMapConversion", 1);
		assertPoint(abscissaOnly.transform(1, 0, 0, 1), -1, 0, 0);
	}

	@Test
	void rejectsZeroDirectionNonFiniteAndBadScale() {
		assertFalse(info(Map.of("eastings", "0", "northings", "0", "orthogonalHeight", "0",
				"xAxisAbscissa", "0", "xAxisOrdinate", "0"), "IfcMapConversion", 1).canTransform());
		assertFalse(info(Map.of("eastings", "NaN", "northings", "0", "orthogonalHeight", "0"),
				"IfcMapConversion", 1).canTransform());
		assertFalse(info(Map.of("eastings", "0", "northings", "0", "orthogonalHeight", "0", "scale", "-1"),
				"IfcMapConversion", 1).canTransform());
	}

	@Test
	void transformsStructuredWktAndAllBoundingBoxCorners() {
		var crs = info(Map.of("eastings", "0", "northings", "0", "orthogonalHeight", "10",
				"xAxisAbscissa", "1", "xAxisOrdinate", "1", "scale", "2"), "IfcMapConversion", 1);
		String wkt = crs.transformWkt("MULTILINESTRING Z ((0 0 1, 1 0 2), (1 1 3, 0 1 4))", 1);
		assertTrue(wkt.startsWith("MULTILINESTRING Z"));
		assertTrue(wkt.contains("12"));
		BoundingBox box = new BoundingBox();
		box.add(new Point3d(0, 0, 1)); box.add(new Point3d(2, 1, 3));
		String transformed = crs.transform(box, 1);
		assertTrue(transformed.contains("-1.414213562373095"));
		assertTrue(transformed.contains("4.242640687119285"));
		assertTrue(transformed.endsWith("16.0))"));
	}

	@Test
	void preservesEmptyAndCollectionWktDimensions() {
		var crs = info(Map.of("eastings", "1", "northings", "2", "orthogonalHeight", "3"),
				"IfcMapConversion", 1);
		assertEquals("POINT Z EMPTY", crs.transformWkt("POINT Z EMPTY", 1));
		String collection = crs.transformWkt("GEOMETRYCOLLECTION Z (POINT Z (1 2 3), LINESTRING Z (0 0 0, 1 1 1))", 1);
		assertTrue(collection.startsWith("GEOMETRYCOLLECTION Z"));
		assertTrue(collection.contains("2 4 6"));
	}

	private static IfcCoordinateReferenceSystemInfo info(Map<String, String> values, String type, double sourceUnit) {
		return new IfcCoordinateReferenceSystemInfo(EPSG, values, List.of(), type, sourceUnit, true);
	}

	private static void assertPoint(IfcCoordinateReferenceSystemInfo.Point point, double x, double y, double z) {
		assertEquals(x, point.x(), 1e-9); assertEquals(y, point.y(), 1e-9); assertEquals(z, point.z(), 1e-9);
	}
}
