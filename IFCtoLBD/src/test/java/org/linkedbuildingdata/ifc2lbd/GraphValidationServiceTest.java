package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class GraphValidationServiceTest {
	@Test
	void producesMachineReportAndHumanSummaryForTraceabilityViolations() {
		var data = ModelFactory.createDefaultModel();
		data.createResource("urn:ifctolbd:conversion:test")
				.addProperty(RDF.type, data.createResource(ConversionManifest.NS + "Conversion"))
				.addLiteral(data.createProperty(ConversionManifest.NS + "sourceChecksum"), "not-a-checksum");
		try (var report = GraphValidationService.validate(data, ValidationShapePack.TRACEABILITY)) {
			assertFalse(report.conforms());
			assertEquals("violations", report.status());
			assertTrue(report.violationCount() > 0);
			assertFalse(report.reportModel().isEmpty());
			assertTrue(report.summary().contains("conformance"));
			assertTrue(report.summary().contains("not factual correctness"));
		}
	}

	@Test
	void rejectsUnknownShapePackPredictably() {
		var data = ModelFactory.createDefaultModel();
		var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> GraphValidationService.validate(data, "unknown"));
		assertTrue(error.getMessage().contains("Unknown SHACL shape pack"));
	}
}
