package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Offline checks against a small, reviewed snapshot of two published ETIM classes. */
class EtimPublishedExamplesTest {
	private static final String IFC = "https://example.test/ifc#";
	private static final String SUPPLY = SupplyChainStage.NS;
	private static final String ETIM = "https://identifier.buildingsmart.org/uri/etim/etim/9.0";
	private static final List<ClassificationDefinition> CLASSES = loadClasses();

	@Test
	void publishedFixtureContainsTheRequestedClassesAndDistinctWidthProperties() {
		assertEquals("Cable end sleeve", definitionByCode("EC000005").name());
		assertEquals("Interior door (complete with frame)", definitionByCode("EC003535").name());
		var door = definitionByCode("EC003535");
		assertEquals(List.of("EF011812", "EF011817", "EF011815"), door.classProperties().stream()
				.map(value -> value.property().code()).toList());
		assertFalse(door.classProperties().stream().anyMatch(value ->
				"Overall width".equalsIgnoreCase(value.property().name())));
	}

	@Test
	void explicitDoorClassificationAnnotatesLeafFrameAndModuleWidthsSeparately() {
		Model source = ModelFactory.createDefaultModel();
		Model general = ModelFactory.createDefaultModel();
		Model properties = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource door = source.createResource("urn:ifc:door")
				.addProperty(RDF.type, source.createResource(IFC + "IfcDoor"));
		Resource leaf = measured(source, ifc, "LeafWidth", "800", "MILLI", "METRE");
		Resource frame = measured(source, ifc, "FrameWidth", "90", "MILLI", "METRE");
		Resource module = measured(source, ifc, "ModuleWidth", "9", "DECI", "METRE");
		addProperty(source, ifc, door, "Pset_Door", leaf, "EF011812");
		addProperty(source, ifc, door, "Pset_Door", frame, "EF011817");
		addProperty(source, ifc, door, "Pset_Door", module, "EF011815");
		Resource unclassifiedOverall = measured(source, ifc, "OverallWidth", "890", "MILLI", "METRE");
		addProperty(source, ifc, door, "Pset_Door", unclassifiedOverall, null);
		classify(source, ifc, door, "EC003535");
		Resource lbdDoor = general.createResource("urn:lbd:door");
		ClassificationResolver resolver = resolverFor("EC003535");

		SupplyChainStage.enrichSupplyChain(source, ifc, general, properties,
				Map.of(door, lbdDoor), resolver, List.of());

		List<Resource> annotations = properties.listResourcesWithProperty(RDF.type,
				properties.createResource(SUPPLY + "EtimPropertyAnnotation")).toList();
		assertEquals(3, annotations.size());
		assertTrue(annotations.stream().anyMatch(value -> hasClassProperty(value, "EC003535", "EF011812")
				&& "800".equals(observed(value))));
		assertTrue(annotations.stream().anyMatch(value -> hasClassProperty(value, "EC003535", "EF011817")
				&& "90".equals(observed(value))));
		assertTrue(annotations.stream().anyMatch(value -> hasClassProperty(value, "EC003535", "EF011815")
				&& "9".equals(observed(value))));
		assertFalse(annotations.stream().anyMatch(value ->
				value.hasProperty(properties.createProperty(SUPPLY + "sourcePropertyName"), "OverallWidth")));
		assertEquals("registry-verified", lbdDoor.getPropertyResourceValue(
				general.createProperty(SUPPLY + "hasClassification"))
				.getProperty(general.createProperty(SUPPLY + "identifierVerification")).getString());
		assertTrue(annotations.stream().anyMatch(value -> value.hasProperty(
				properties.createProperty("http://qudt.org/schema/qudt/unit"),
				properties.createResource("http://qudt.org/vocab/unit/DeciM"))));
	}

	@Test
	void cableEndSleeveDoesNotAcceptDoorLeafWidthIdentifierAsSameMeaning() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource fitting = source.createResource("urn:ifc:fitting");
		Resource leafWidth = measured(source, ifc, "DoorLeafWidth", "800", "MILLI", "METRE");
		addProperty(source, ifc, fitting, "Pset_CableEndSleeve", leafWidth, "EF011812");

		EtimPropertyAnnotationStage.annotate(source, ifc, output, fitting,
				output.createResource("urn:lbd:fitting"), definitionByCode("EC000005"), List.of());

		assertFalse(output.contains(null, RDF.type, output.createResource(SUPPLY + "EtimPropertyAnnotation")));
	}

	@Test
	void ifcDoorAloneAbstainsFromEc003535AndDoesNotTypeTheElement() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource door = source.createResource("urn:ifc:unclassified-door")
				.addProperty(RDF.type, source.createResource(IFC + "IfcDoor"))
				.addProperty(RDF.type, org.linkedbuildingdata.ifc2lbd.namespace.BOT.element);
		Resource target = output.createResource("urn:lbd:unclassified-door")
				.addProperty(RDF.type, output.createResource(org.linkedbuildingdata.ifc2lbd.namespace.BOT.ns + "Element"));
		ClassificationResolver candidates = new ClassificationResolver() {
			@Override public Optional<Resolution> resolve(Request request) { return Optional.empty(); }
			@Override public List<Candidate> candidates(CandidateRequest request) {
				return List.of(new Candidate(new Resolution(definitionByCode("EC003535").uri(), "fixture", "10.0", 1),
						definitionByCode("EC003535"), "published-ifc-relationship", null));
			}
		};

		EtimClassificationSuggestionStage.suggest(source, ifc, output, ModelFactory.createDefaultModel(),
				Map.of(door, target), candidates, java.util.Set.of(), List.of(), List.of());

		assertFalse(output.contains(null, RDF.type, output.createResource(definitionByCode("EC003535").uri())));
		assertFalse(target.hasProperty(output.createProperty(SUPPLY + "hasClassificationSuggestion")));
		assertEquals("abstained", target.getPropertyResourceValue(
				output.createProperty(SUPPLY + "hasClassificationAssessment"))
				.getProperty(output.createProperty(SUPPLY + "classificationStatus")).getString());
	}

	@Test
	void similarlySupportedDoorClassesRemainSeparateRankedSuggestions() {
		Model source = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC);
		Resource door = source.createResource("urn:ifc:ambiguous-door")
				.addProperty(RDF.type, source.createResource(IFC + "IfcDoor"))
				.addProperty(ifc.getProperty("description_IfcRoot"), wrapped(source,
						"Interior door complete with frame"));
		Resource target = output.createResource("urn:lbd:ambiguous-door")
				.addProperty(RDF.type, output.createResource(org.linkedbuildingdata.ifc2lbd.namespace.BOT.ns + "Element"));
		ClassificationDefinition published = definitionByCode("EC003535");
		ClassificationDefinition alternate = new ClassificationDefinition(
				"https://fixture.example/etim/class/alternate-door-set", published.dictionaryUri(), "FIXTURE-DOOR",
				"Interior door set", "Interior door complete with frame set", published.relatedIfcEntities(),
				published.classProperties(), List.of());
		ClassificationResolver candidates = new ClassificationResolver() {
				@Override public Optional<Resolution> resolve(Request request) { return Optional.empty(); }
				@Override public List<Candidate> candidates(CandidateRequest request) {
					return List.of(new Candidate(new Resolution(published.uri(), "fixture", "10.0", 1),
							published, "published-ifc-relationship", null),
						new Candidate(new Resolution(alternate.uri(), "fixture", "10.0", 1),
							alternate, "published-ifc-relationship", null));
				}
			};

		EtimClassificationSuggestionStage.suggest(source, ifc, output, ModelFactory.createDefaultModel(),
				Map.of(door, target), candidates, java.util.Set.of(), List.of(), List.of());

		List<Resource> suggestions = output.listResourcesWithProperty(RDF.type,
				output.createResource(SUPPLY + "ClassificationSuggestion")).toList();
		assertEquals(2, suggestions.size());
		assertEquals(1, suggestions.stream().filter(value -> value.hasLiteral(
				output.createProperty(SUPPLY + "rank"), 1)).count());
		assertFalse(target.hasProperty(output.createProperty(SUPPLY + "hasClassification")));
		assertFalse(output.contains(null, RDF.type, output.createResource(published.uri())));
	}

	@Test
	void matchingMeasurementUnitCanBeNormalizedWithoutChangingTheOriginalObservedValue() {
		var original = ModelFactory.createDefaultModel().createTypedLiteral("800", XSD.decimal.getURI());
		var grams = UnitResolver.fromText("g");
		var normalized = UnitResolver.normalizeValue(ModelFactory.createDefaultModel(), original, grams);
		assertEquals("0.8", normalized.asLiteral().getLexicalForm());
		assertEquals("800", original.asLiteral().getLexicalForm());
	}

	private static ClassificationResolver resolverFor(String code) {
		ClassificationDefinition definition = definitionByCode(code);
		return new ClassificationResolver() {
			@Override public Optional<Resolution> resolve(Request request) {
				if (!code.equals(request.code())) return Optional.empty();
				return Optional.of(new Resolution(definition.uri(), "fixture-registry", "9.0", 1,
						IdentifierVerification.REGISTRY_VERIFIED, request.edition(), "9.0",
						BsddClassificationResolver.editionCompatibility(request.edition(), "9.0")));
			}
			@Override public Optional<ClassificationDefinition> definition(Resolution resolution) {
				return definition.uri().equals(resolution.conceptUri()) ? Optional.of(definition) : Optional.empty();
			}
		};
	}

	private static ClassificationDefinition definitionByCode(String code) {
		return CLASSES.stream().filter(value -> code.equals(value.code())).findFirst().orElseThrow();
	}

	private static List<ClassificationDefinition> loadClasses() {
		try (InputStream input = EtimPublishedExamplesTest.class.getResourceAsStream("/etim/etim-examples.json")) {
			if (input == null) throw new IllegalStateException("Missing ETIM example fixture");
			return new ObjectMapper().readValue(input, new TypeReference<>() { });
		} catch (Exception exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static Resource measured(Model model, IfcOWL ifc, String name, String lexical,
			String prefix, String unitName) {
		Resource value = model.createResource().addProperty(RDF.type, model.createResource(IFC + "IfcLengthMeasure"))
				.addLiteral(model.createProperty("https://w3id.org/express#hasDecimal"),
						model.createTypedLiteral(lexical, XSD.decimal.getURI()));
		Resource unit = model.createResource()
				.addProperty(ifc.getUnitType_IfcNamedUnit(), model.createResource(IFC + "LENGTHUNIT"))
				.addProperty(ifc.getPrefix_IfcSIUnit(), model.createResource(IFC + prefix))
				.addProperty(ifc.getName_IfcSIUnit(), model.createResource(IFC + unitName));
		return model.createResource().addProperty(ifc.getProperty("name_IfcProperty"), wrapped(model, name))
				.addProperty(ifc.getNominalValue_IfcPropertySingleValue(), value)
				.addProperty(ifc.getUnit_IfcPropertySingleValue(), unit);
	}

	private static void addProperty(Model model, IfcOWL ifc, Resource element, String setName,
			Resource property, String propertyCode) {
		Resource set = model.createResource().addProperty(RDF.type, model.createResource(IFC + "IfcPropertySet"))
				.addProperty(ifc.getProperty("name_IfcRoot"), wrapped(model, setName))
				.addProperty(ifc.getHasProperties_IfcPropertySet(), property);
		model.createResource().addProperty(RDF.type, model.createResource(IFC + "IfcRelDefinesByProperties"))
				.addProperty(ifc.getRelatedObjects_IfcRelDefines(), element)
				.addProperty(ifc.getRelatingPropertyDefinition_IfcRelDefinesByProperties(), set);
		if (propertyCode != null) {
			String propertyUri = ETIM + "/prop/" + propertyCode;
			property.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(model, propertyCode));
			Resource reference = model.createResource().addProperty(ifc.getProperty("location_IfcExternalReference"),
					wrapped(model, propertyUri)).addProperty(ifc.getProperty("identification_IfcExternalReference"),
					wrapped(model, propertyCode));
			model.createResource().addProperty(ifc.getProperty("relatedResourceObjects_IfcExternalReferenceRelationship"), property)
					.addProperty(ifc.getProperty("relatingReference_IfcExternalReferenceRelationship"), reference);
		}
	}

	private static void classify(Model model, IfcOWL ifc, Resource element, String code) {
		Resource system = model.createResource().addProperty(ifc.getProperty("name_IfcClassification"), wrapped(model, "ETIM"))
				.addProperty(ifc.getProperty("edition_IfcClassification"), wrapped(model, "9.0"));
		Resource reference = model.createResource()
				.addProperty(ifc.getProperty("identification_IfcExternalReference"), wrapped(model, code))
				.addProperty(ifc.getProperty("referencedSource_IfcClassificationReference"), system);
		model.createResource().addProperty(RDF.type, model.createResource(IFC + "IfcRelAssociatesClassification"))
				.addProperty(ifc.getProperty("relatedObjects_IfcRelAssociates"), element)
				.addProperty(ifc.getProperty("relatingClassification_IfcRelAssociatesClassification"), reference);
	}

	private static boolean hasClassProperty(Resource annotation, String classCode, String propertyCode) {
		return annotation.hasProperty(annotation.getModel().createProperty(SUPPLY + "classProperty"),
				annotation.getModel().createResource(ETIM + "/class/" + classCode
						+ "/prop/undefined_set/" + propertyCode));
	}
	private static String observed(Resource annotation) {
		return annotation.getProperty(annotation.getModel().createProperty(SUPPLY + "observedValue"))
				.getObject().asLiteral().getLexicalForm();
	}
	private static Resource wrapped(Model model, String value) {
		return model.createResource().addLiteral(model.createProperty("https://w3id.org/express#hasString"), value);
	}
}
