package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class ValidationStageTest {

	private static final String BOT = "https://w3id.org/bot#";

	@Test
	void validBotRelationConforms() {
		var data = ModelFactory.createDefaultModel();
		var site = data.createResource("urn:site");
		var building = data.createResource("urn:building").addProperty(RDF.type, data.createResource(BOT + "Building"));
		site.addProperty(data.createProperty(BOT + "hasBuilding"), building);

		var result = ValidationStage.validate(Optional.of(ConversionProfiles.COMPLIANCE), data);
		assertEquals("conforms", result.status());
		assertFalse(result.report().isEmpty());
	}

	@Test
	void invalidBotRelationProducesViolationReport() {
		var data = ModelFactory.createDefaultModel();
		data.createResource("urn:site").addProperty(data.createProperty(BOT + "hasBuilding"),
				data.createResource("urn:not-typed-as-building"));

		var result = ValidationStage.validate(Optional.of(ConversionProfiles.COMPLIANCE), data);
		assertEquals("violations", result.status());
		assertFalse(result.report().isEmpty());
		assertEquals(1, data.size(), "Validation must not rewrite failed data");
	}

	@Test
	void profileWithoutShapesSkipsValidation() {
		var result = ValidationStage.validate(Optional.of(ConversionProfiles.CORE), ModelFactory.createDefaultModel());
		assertEquals("not-run", result.status());
		assertEquals(0, result.report().size());
	}

	@Test
	void standardValidationLoadsEveryVersionedShapePack() {
		var request = new ConversionRequest("unused.ifc", new ConversionProperties()).withStandardValidation();
		var result = ValidationStage.validate(request, ModelFactory.createDefaultModel());
		assertEquals(ValidationShapePack.values().length, result.shapeResources().size());
		assertTrue(result.shapeResources().stream().allMatch(resource -> resource.contains("-v1.0.0.ttl")));
	}
}
