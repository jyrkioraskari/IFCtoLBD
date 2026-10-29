package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class OpeningGeometryPreviewTest {
	@Test
	void openingVolumesStayNearlyTransparentWhenSelected() {
		assertEquals(0.02, IFCtoLBDController.previewOpacity(true, false));
		assertEquals(0.0032, IFCtoLBDController.previewOpacity(true, true), 0.000001);
		assertEquals(1.0, IFCtoLBDController.previewOpacity(false, false));
	}

	@Test
	void recognizesOpeningsAcrossExportIdentifierStyles() {
		Model model = ModelFactory.createDefaultModel();
		try {
			Resource legacy = model.createResource("https://example.test/model#ifcowl_ifcopeningelement_123");
			Resource legacyGeometry = model.createResource("https://example.test/model#ifcowl_ifcopeningelement_123_geometry");
			Resource typed = model.createResource("https://example.test/model#stable-guid");
			typed.addProperty(RDF.type, model.createResource("https://example.test/ifc#IfcOpeningStandardCase"));
			Resource linked = model.createResource("https://example.test/model#another-stable-guid");
			linked.addProperty(OWL.sameAs, model.createResource("https://example.test/ifc#IfcOpeningElement_456"));
			Resource wall = model.createResource("https://example.test/model#ifcowl_ifcwall_789");
			wall.addProperty(RDF.type, model.createResource("https://example.test/ifc#IfcWall"));

			assertTrue(IFCtoLBDController.isOpeningPreviewGeometry(model, legacy));
			assertTrue(IFCtoLBDController.isOpeningPreviewGeometry(model, legacyGeometry));
			assertTrue(IFCtoLBDController.isOpeningPreviewGeometry(model, typed));
			assertTrue(IFCtoLBDController.isOpeningPreviewGeometry(model, linked));
			assertFalse(IFCtoLBDController.isOpeningPreviewGeometry(model, wall));
		} finally {
			model.close();
		}
	}
}
