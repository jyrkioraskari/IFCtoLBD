package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javafx.geometry.Point3D;

class GeometryExplosionTest {
	@Test
	void movesElementsRadiallyByAStableModelRelativeDistance() {
		IFCtoLBDController.GeometryBounds model = new IFCtoLBDController.GeometryBounds();
		model.include(-10, -5, -2);
		model.include(10, 5, 2);
		IFCtoLBDController.GeometryBounds element = new IFCtoLBDController.GeometryBounds();
		element.include(4, 1, -1);
		element.include(6, 3, 1);

		Point3D offset = IFCtoLBDController.explosionVector(model, element, "urn:wall");
		assertEquals(3.6, offset.magnitude(), 0.000001);
		assertTrue(offset.getX() > 0);
		assertTrue(offset.getY() > 0);
		assertEquals(0, offset.getZ(), 0.000001);
		assertEquals(offset, IFCtoLBDController.explosionVector(model, element, "urn:wall"));
	}

	@Test
	void centerElementGetsDeterministicOutwardDirection() {
		IFCtoLBDController.GeometryBounds model = new IFCtoLBDController.GeometryBounds();
		model.include(-10, -10, -10);
		model.include(10, 10, 10);
		IFCtoLBDController.GeometryBounds centerElement = new IFCtoLBDController.GeometryBounds();
		centerElement.include(0, 0, 0);

		Point3D offset = IFCtoLBDController.explosionVector(model, centerElement, "urn:center");
		assertEquals(3.6, offset.magnitude(), 0.000001);
		assertEquals(offset, IFCtoLBDController.explosionVector(model, centerElement, "urn:center"));
	}
}
