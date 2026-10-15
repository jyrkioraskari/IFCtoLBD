package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

class EtimClassificationSuggestionStageTest {
	private static final String IFC = "https://example.test/ifc#";
	private static final String SUPPLY = SupplyChainStage.NS;
	private static final String CLASS_URI = "https://identifier.example/etim/class/EC0001";

	@Test
	void ranksCandidateWithIndependentEvidenceAndReportsContradictionsAndMissingInformation() {
		Fixture fixture = fixture("Centrifugal circulation pump for heating water");
		Resource flow = property(fixture.source(), fixture.ifc(), "urn:ifc:flow", "Flow rate", "20");
		externalIdentifier(fixture.source(), fixture.ifc(), flow, "EF0001");
		Resource voltage = stringProperty(fixture.source(), fixture.ifc(), "urn:ifc:voltage", "Voltage", "wrong");
		propertySet(fixture.source(), fixture.ifc(), fixture.element(), flow, voltage);

		EtimClassificationSuggestionStage.suggest(fixture.source(), fixture.ifc(), fixture.output(),
				fixture.properties(), Map.of(fixture.element(), fixture.target()), resolver(candidateDefinition()),
				Set.of(), List.of(), List.of());

		Resource suggestion = fixture.output().listResourcesWithProperty(RDF.type,
				fixture.output().createResource(SUPPLY + "ClassificationSuggestion")).next();
		assertTrue(suggestion.hasLiteral(fixture.output().createProperty(SUPPLY + "classificationStatus"),
				"suggested"));
		assertTrue(suggestion.hasProperty(fixture.output().createProperty(SUPPLY + "suggestedConcept"),
				fixture.output().createResource(CLASS_URI)));
		assertTrue(suggestion.hasProperty(fixture.output().createProperty(SUPPLY + "evidence")));
		assertTrue(suggestion.hasProperty(fixture.output().createProperty(SUPPLY + "contradiction")));
		assertTrue(suggestion.hasProperty(fixture.output().createProperty(SUPPLY + "missingInformation")));
		assertFalse(fixture.target().hasProperty(fixture.output().createProperty(SUPPLY + "hasClassification")));
		assertFalse(fixture.output().contains(fixture.target(), RDF.type,
				fixture.output().createResource(CLASS_URI)));
	}

	@Test
	void abstainsWhenPublishedRelationshipHasNoIndependentSupport() {
		Fixture fixture = fixture(null);

		EtimClassificationSuggestionStage.suggest(fixture.source(), fixture.ifc(), fixture.output(),
				fixture.properties(), Map.of(fixture.element(), fixture.target()), resolver(candidateDefinition()),
				Set.of(), List.of(), List.of());

		assertFalse(fixture.output().contains(null, RDF.type,
				fixture.output().createResource(SUPPLY + "ClassificationSuggestion")));
		Resource assessment = fixture.target().getPropertyResourceValue(
				fixture.output().createProperty(SUPPLY + "hasClassificationAssessment"));
		assertEquals("abstained",
				assessment.getProperty(fixture.output().createProperty(SUPPLY + "classificationStatus")).getString());
	}

	@Test
	void reviewedCandidateAcceptanceIsSeparateFromSuggestion() {
		Fixture fixture = fixture("Centrifugal circulation pump for heating water");
		Resource flow = property(fixture.source(), fixture.ifc(), "urn:ifc:flow", "Flow rate", "20");
		externalIdentifier(fixture.source(), fixture.ifc(), flow, "EF0001");
		propertySet(fixture.source(), fixture.ifc(), fixture.element(), flow);
		var acceptance = new EtimClassificationAcceptance(fixture.target().getURI(), CLASS_URI,
				"review:classification-board/42");

		EtimClassificationSuggestionStage.suggest(fixture.source(), fixture.ifc(), fixture.output(),
				fixture.properties(), Map.of(fixture.element(), fixture.target()), resolver(candidateDefinition()),
				Set.of(), List.of(acceptance), List.of());

		Resource assertion = fixture.target().getPropertyResourceValue(
				fixture.output().createProperty(SUPPLY + "hasClassification"));
		assertEquals("accepted",
				assertion.getProperty(fixture.output().createProperty(SUPPLY + "classificationStatus")).getString());
		assertTrue(assertion.hasProperty(fixture.output().createProperty(SUPPLY + "resolvedConcept"),
				fixture.output().createResource(CLASS_URI)));
		assertTrue(assertion.hasLiteral(fixture.output().createProperty(SUPPLY + "reviewReference"),
				"review:classification-board/42"));
		assertTrue(fixture.target().hasProperty(
				fixture.output().createProperty(SUPPLY + "hasClassificationSuggestion")));
	}

	@Test
	void candidateScopedPropertyMappingCannotConfirmItsOwnClass() {
		Fixture fixture = fixture(null);
		Resource legacy = property(fixture.source(), fixture.ifc(), "urn:ifc:legacy", "LegacyFlow", "20");
		propertySet(fixture.source(), fixture.ifc(), fixture.element(), legacy);
		EtimPropertyMapping circular = new EtimPropertyMapping(CLASS_URI, "IfcPump", "Pset_Test",
				"LegacyFlow", "https://identifier.example/etim/class-property/EC0001-EF0001",
				"https://identifier.example/etim/property/EF0001", "review:property-map");

		EtimClassificationSuggestionStage.suggest(fixture.source(), fixture.ifc(), fixture.output(),
				fixture.properties(), Map.of(fixture.element(), fixture.target()), resolver(candidateDefinition()),
				Set.of(), List.of(), List.of(circular));

		assertFalse(fixture.output().contains(null, RDF.type,
				fixture.output().createResource(SUPPLY + "ClassificationSuggestion")));
		assertTrue(fixture.output().contains(null, fixture.output().createProperty(SUPPLY + "reason"),
				"insufficient-independent-evidence"));
	}

	@Test
	void doesNotSuggestForAnElementWithASuppliedEtimClassification() {
		Fixture fixture = fixture("Centrifugal circulation pump for heating water");

		EtimClassificationSuggestionStage.suggest(fixture.source(), fixture.ifc(), fixture.output(),
				fixture.properties(), Map.of(fixture.element(), fixture.target()), resolver(candidateDefinition()),
				Set.of(fixture.element()), List.of(), List.of());

		assertFalse(fixture.output().contains(null, RDF.type,
				fixture.output().createResource(SUPPLY + "ClassificationSuggestion")));
		assertFalse(fixture.target().hasProperty(
				fixture.output().createProperty(SUPPLY + "hasClassificationAssessment")));
	}

	private static Fixture fixture(String description) {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource element = source.createResource("urn:ifc:pump")
				.addProperty(RDF.type, source.createResource(IFC + "IfcPump"));
		if (description != null)
			element.addProperty(ifc.getProperty("description_IfcRoot"), wrapped(source, description));
		Resource target = output.createResource("urn:lbd:pump").addProperty(RDF.type, BOT.element);
		return new Fixture(source, output, ModelFactory.createDefaultModel(), ifc, element, target);
	}

	private static ClassificationResolver resolver(ClassificationDefinition definition) {
		return new ClassificationResolver() {
			@Override public java.util.Optional<Resolution> resolve(Request request) {
				return java.util.Optional.empty();
			}
			@Override public List<Candidate> candidates(CandidateRequest request) {
				return List.of(new Candidate(new Resolution(CLASS_URI, "test", "10", 1), definition,
						"published-ifc-relationship", null));
			}
		};
	}

	private static ClassificationDefinition candidateDefinition() {
		var flow = new ClassificationDefinition.ClassProperty(
				"https://identifier.example/etim/class-property/EC0001-EF0001",
				new ClassificationDefinition.Property("https://identifier.example/etim/property/EF0001",
						"EF0001", "Flow rate", null), "Hydraulics", "Real", null, null, true, null,
				List.of(), List.of());
		var voltage = new ClassificationDefinition.ClassProperty(
				"https://identifier.example/etim/class-property/EC0001-EF0002",
				new ClassificationDefinition.Property("https://identifier.example/etim/property/EF0002",
						"EF0002", "Voltage", null), "Electrical", "Real", null, null, null, null,
				List.of(), List.of());
		var missing = new ClassificationDefinition.ClassProperty(
				"https://identifier.example/etim/class-property/EC0001-EF0003",
				new ClassificationDefinition.Property("https://identifier.example/etim/property/EF0003",
						"EF0003", "Connection size", null), "Connections", "Real", null, null, true, null,
				List.of(), List.of());
		return new ClassificationDefinition(CLASS_URI, "https://identifier.example/etim/10", "EC0001",
				"Centrifugal circulation pump", "Pump for circulation of heating water",
				List.of("IfcPump"), List.of(flow, voltage, missing), List.of());
	}

	private static Resource property(Model model, IfcOWL ifc, String uri, String name, String value) {
		Resource wrappedValue = model.createResource()
				.addProperty(RDF.type, model.createResource(IFC + "IfcReal"))
				.addLiteral(model.createProperty("https://w3id.org/express#hasDecimal"),
						model.createTypedLiteral(value, XSD.decimal.getURI()));
		return model.createResource(uri)
				.addProperty(ifc.getProperty("name_IfcProperty"), wrapped(model, name))
				.addProperty(ifc.getNominalValue_IfcPropertySingleValue(), wrappedValue);
	}

	private static Resource stringProperty(Model model, IfcOWL ifc, String uri, String name, String value) {
		Resource wrappedValue = model.createResource()
				.addProperty(RDF.type, model.createResource(IFC + "IfcLabel"))
				.addLiteral(model.createProperty("https://w3id.org/express#hasString"), value);
		return model.createResource(uri)
				.addProperty(ifc.getProperty("name_IfcProperty"), wrapped(model, name))
				.addProperty(ifc.getNominalValue_IfcPropertySingleValue(), wrappedValue);
	}

	private static void propertySet(Model model, IfcOWL ifc, Resource element, Resource... properties) {
		Resource set = model.createResource("urn:ifc:pset")
				.addProperty(RDF.type, model.createResource(IFC + "IfcPropertySet"))
				.addProperty(ifc.getProperty("name_IfcRoot"), wrapped(model, "Pset_Test"));
		for (Resource property : properties) set.addProperty(ifc.getHasProperties_IfcPropertySet(), property);
		model.createResource("urn:ifc:defines")
				.addProperty(RDF.type, model.createResource(IFC + "IfcRelDefinesByProperties"))
				.addProperty(ifc.getRelatedObjects_IfcRelDefines(), element)
				.addProperty(ifc.getRelatingPropertyDefinition_IfcRelDefinesByProperties(), set);
	}

	private static void externalIdentifier(Model model, IfcOWL ifc, Resource property, String identifier) {
		Resource reference = model.createResource("urn:ifc:property-reference")
				.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(model, identifier));
		model.createResource("urn:ifc:property-reference-relation")
				.addProperty(ifc.getProperty("relatedResourceObjects_IfcExternalReferenceRelationship"), property)
				.addProperty(ifc.getProperty("relatingReference_IfcExternalReferenceRelationship"), reference);
	}

	private static Resource wrapped(Model model, String value) {
		return model.createResource().addLiteral(model.createProperty("https://w3id.org/express#hasString"), value);
	}

	private record Fixture(Model source, Model output, Model properties, IfcOWL ifc, Resource element,
			Resource target) { }
}
