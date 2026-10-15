package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/** End-to-end, offline regression test for the checked-in ETIM IFC4 sample. */
class EtimElectricalSampleTest {
	private static final String ETIM = "https://identifier.buildingsmart.org/uri/etim/etim/9.0";
	private static final String SUPPLY = SupplyChainStage.NS;

	@Test
	void enrichesExplicitEtimClassificationsAndPropertiesOffline() throws Exception {
		File ifcFile = new File(getClass().getResource("/ETIM-Electrical-Sample_IFC4.ifc").toURI());
		ClassificationResolver resolver = new OfflineFixtureResolver();

		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.SUPPLY_CHAIN).withClassificationResolver(resolver))) {
			Model model = result.getModel();
			var assertionType = model.createResource(SUPPLY + "ClassificationAssertion");
			List<Resource> assertions = model.listResourcesWithProperty(RDF.type, assertionType).toList();
			assertEquals(4, assertions.size());
			assertTrue(assertions.stream().allMatch(value -> value.hasLiteral(
					model.createProperty(SUPPLY + "classificationStatus"), "supplied")));
			assertTrue(assertions.stream().allMatch(value -> value.hasLiteral(
					model.createProperty(SUPPLY + "identifierVerification"), "registry-verified")));
			assertEquals(Set.of("EC002288", "EC001957", "EC000054", "EC000247"), assertions.stream()
					.map(value -> value.getProperty(model.createProperty(SUPPLY + "code")).getString())
					.collect(java.util.stream.Collectors.toSet()));

			List<Resource> annotations = model.listResourcesWithProperty(RDF.type,
					model.createResource(SUPPLY + "EtimPropertyAnnotation")).toList();
			assertEquals(7, annotations.size());
			assertTrue(annotations.stream().allMatch(value -> value.hasLiteral(
					model.createProperty(SUPPLY + "mappingMethod"), "explicit-identifier")));
			assertTrue(annotations.stream().allMatch(value -> value.hasLiteral(
					model.createProperty(SUPPLY + "sourceKind"), "occurrence")));
			assertEquals(Set.of("Mounting method", "Number of rows", "Protection", "Colour", "Core identification"),
					annotations.stream().map(value -> value.getProperty(
							model.createProperty(SUPPLY + "sourcePropertyName")).getString())
							.collect(java.util.stream.Collectors.toSet()));
			assertEquals(Set.of(property("EF000003"), property("EF000266"), property("EF000781"),
					property("EF000007"), property("EF000067")), annotations.stream()
					.map(value -> value.getPropertyResourceValue(model.createProperty(SUPPLY + "property")).getURI())
					.collect(java.util.stream.Collectors.toSet()));
			assertTrue(annotations.stream().allMatch(value -> {
				Resource reusable = value.getPropertyResourceValue(model.createProperty(SUPPLY + "property"));
				Resource assignment = value.getPropertyResourceValue(model.createProperty(SUPPLY + "classProperty"));
				return reusable != null && assignment != null && !reusable.equals(assignment)
						&& assignment.getURI().contains("/prop/undefined_set/");
			}));
			Set<String> observedValues = annotations.stream().map(value -> value.getProperty(
					model.createProperty(SUPPLY + "observedValue")).getLiteral().getLexicalForm())
					.collect(java.util.stream.Collectors.toSet());
			assertTrue(observedValues.contains("2"));
			assertTrue(observedValues.contains("Green-yellow"));
			assertTrue(result.getManifestModel().contains(null,
					result.getManifestModel().createProperty(ConversionManifest.NS + "classificationResolver"),
					"fixture-bsdd-offline"));
			Resource dictionary = model.createResource(ETIM);
			assertTrue(dictionary.hasLiteral(model.createProperty("http://purl.org/dc/terms/rights"),
					"No license (rights reserved)"));
			assertTrue(result.getManifestModel().contains(null, result.getManifestModel().createProperty(
					ConversionManifest.NS + "usesClassificationDictionary"),
					result.getManifestModel().createResource(ETIM)));
		}
	}

	private static String classification(String code) { return ETIM + "/class/" + code; }
	private static String property(String code) { return ETIM + "/prop/" + code; }

	private static final class OfflineFixtureResolver implements ClassificationResolver {
		private final Map<String, ClassificationDefinition> definitions = definitions();

		@Override public String id() { return "fixture-bsdd-offline"; }
		@Override public String version() { return "9.0"; }
		@Override public boolean allowsNetworkAccess() { return false; }

		@Override
		public Optional<Resolution> resolve(Request request) {
			ClassificationDefinition definition = definitions.get(request.code());
			if (definition == null || !"ETIM".equalsIgnoreCase(request.system())
					|| !definition.uri().equals(request.sourceLocation())) return Optional.empty();
			return Optional.of(new Resolution(definition.uri(), id(), version(), 1.0,
					IdentifierVerification.REGISTRY_VERIFIED, request.edition(), version(),
					BsddClassificationResolver.editionCompatibility(request.edition(), version())));
		}

		@Override
		public Optional<ClassificationDefinition> definition(Resolution resolution) {
			return definitions.values().stream().filter(value -> value.uri().equals(resolution.conceptUri())).findFirst();
		}

		@Override
		public List<DictionaryMetadata> dictionaries() {
			return List.of(new DictionaryMetadata(ETIM, "etim", "ETIM", "9.0", "Active", "etim",
					"ETIM International", "No license (rights reserved)",
					"https://technical.buildingsmart.org/services/bsdd/license/"));
		}
	}

	private static Map<String, ClassificationDefinition> definitions() {
		Map<String, ClassificationDefinition> values = new LinkedHashMap<>();
		values.put("EC002288", definition("EC002288", "Small distribution board equipped", "IfcElectricDistributionBoard",
				feature("EC002288", "EF000003", "Mounting method", "Alphanumeric"),
				feature("EC002288", "EF000266", "Number of rows", "Integer"),
				feature("EC002288", "EF000781", "Protection", "Alphanumeric")));
		values.put("EC001957", definition("EC001957", "Emergency luminaire", "IfcLightFixture",
				feature("EC001957", "EF000003", "Mounting method", "Alphanumeric"),
				feature("EC001957", "EF000007", "Colour", "Alphanumeric")));
		values.put("EC000054", definition("EC000054", "Incandescent lamp with ring head", "IfcLamp",
				feature("EC000054", "EF000007", "Colour", "Alphanumeric")));
		values.put("EC000247", definition("EC000247", "Spiralized cable", "IfcCableSegment",
				feature("EC000247", "EF000067", "Core identification", "Alphanumeric")));
		return Map.copyOf(values);
	}

	private static ClassificationDefinition definition(String code, String name, String ifcEntity,
			ClassificationDefinition.ClassProperty... properties) {
		return new ClassificationDefinition(classification(code), ETIM, code, name, name, List.of(ifcEntity),
				List.of(properties), List.of());
	}

	private static ClassificationDefinition.ClassProperty feature(String classCode, String code, String name,
			String dataType) {
		return new ClassificationDefinition.ClassProperty(
				classification(classCode) + "/prop/undefined_set/" + code,
				new ClassificationDefinition.Property(property(code), code, name, null),
				"undefined_set", dataType, null, null, null, null, List.of(), List.of());
	}
}
