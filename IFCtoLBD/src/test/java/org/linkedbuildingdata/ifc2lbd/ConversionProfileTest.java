package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConversionProfileTest {
	@Test
	void resolvesProfilesByPublicName() {
		assertSame(ConversionProfiles.PROPERTIES_OPM, ConversionProfiles.named("properties-opm"));
		assertSame(ConversionProfiles.GEOMETRY_FULL, ConversionProfiles.named(" GEOMETRY-FULL "));
		assertSame(ConversionProfiles.MATERIALS_BMO, ConversionProfiles.named("materials-bmo"));
		assertThrows(IllegalArgumentException.class, () -> ConversionProfiles.named("missing"));
	}

	@Test
	void materialProfileIsOptionalAndEnablesOnlyItsElementPrerequisite() {
		ConversionProperties materials = ConversionProfiles.MATERIALS_BMO.toConversionProperties();
		assertTrue(materials.isHasBuildingElements());
		assertFalse(materials.isHasBuildingProperties());
		assertFalse(materials.isHasGeometry());
		assertFalse(materials.hasPerformanceBoost());
		assertEquals(List.of(BuiltInConversionModule.BOT_TOPOLOGY,
				BuiltInConversionModule.PRODUCT_ONTOLOGY, ConversionProfiles.MATERIALS_BMO_MODULE),
				ConversionProfiles.MATERIALS_BMO.modules());
		assertFalse(ConversionProfiles.CORE.modules().contains(ConversionProfiles.MATERIALS_BMO_MODULE));
		assertTrue(ConversionProfiles.MATERIALS_BMO_MODULE instanceof ConversionModule);
		assertEquals(List.of(ValidationShapePack.MATERIALS_BMO.resource()),
				ConversionProfiles.MATERIALS_BMO_MODULE.shapeResources());
	}

	@Test
	void coreProfileDoesNotEnableOptionalOutput() {
		ConversionProperties properties = ConversionProfiles.CORE.toConversionProperties();
		assertTrue(properties.isHasBuildingElements());
		assertFalse(properties.isHasBuildingProperties());
		assertFalse(properties.isHasGeometry());
		assertTrue(properties.hasIfcSpaceBoundaries());
		assertFalse(properties.hasGeometryInferredInterfaces());
		assertFalse(properties.isHasGeolocation());
		assertFalse(properties.isExportIfcOWL());
	}

	@Test
	void profilesProduceIndependentConfigurationSnapshots() {
		ConversionProperties first = ConversionProfiles.BIM_GIS.toConversionProperties();
		ConversionProperties second = ConversionProfiles.BIM_GIS.toConversionProperties();
		assertNotSame(first, second);
		assertTrue(first.isHasGeometry());
		assertTrue(first.hasBoundingBoxWKT());
		assertTrue(first.isHasGeolocation());
	}

	@Test
	void propertyProfilesSelectTheirRepresentation() {
		assertEquals(ConversionProperties.PropertyMode.SIMPLE,
				ConversionProfiles.PROPERTIES_SIMPLE.toConversionProperties().getPropertyMode());
		assertEquals(ConversionProperties.PropertyMode.OPM,
				ConversionProfiles.PROPERTIES_OPM.toConversionProperties().getPropertyMode());
		ConversionProperties evidence = ConversionProfiles.EVIDENCE.toConversionProperties();
		assertEquals(ConversionProperties.PropertyMode.OPM, evidence.getPropertyMode());
		assertTrue(evidence.isHasUnits());
		assertTrue(evidence.isExportIfcOWL());
	}

	@Test
	void duplicateModuleIdsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> ConversionProfile.of("invalid",
				BuiltInConversionModule.BOT_TOPOLOGY, BuiltInConversionModule.BOT_TOPOLOGY));
	}

	@Test
	void stableIdentityIsOptIn() {
		assertFalse(ConversionProfiles.CORE.toConversionProperties().hasStableIdentity());
		assertTrue(ConversionProfiles.REVISION_READY.toConversionProperties().hasStableIdentity());
	}

	@Test
	void geometryInterfaceInferenceEnablesItsGeometryPrerequisite() {
		ConversionProperties properties = new ConversionProperties();
		properties.setGeometryInferredInterfaces(true);
		assertTrue(properties.hasGeometryInferredInterfaces());
		assertTrue(properties.isHasGeometry());
	}

	@Test
	void etimSuggestionsAndAcceptancesAreExplicitAndSurviveRequestCopies() {
		ConversionRequest defaults = new ConversionRequest("model.ifc", ConversionProfiles.SUPPLY_CHAIN);
		assertFalse(defaults.hasEtimClassificationSuggestions());
		assertTrue(defaults.getAcceptedEtimClassifications().isEmpty());

		var acceptance = new EtimClassificationAcceptance("urn:lbd:pump",
				"https://identifier.example/etim/class/EC0001", "review:42");
		ConversionRequest configured = defaults.withAcceptedEtimClassifications(List.of(acceptance))
				.withStandardValidation();
		assertTrue(configured.hasEtimClassificationSuggestions());
		assertEquals(List.of(acceptance), configured.getAcceptedEtimClassifications());
	}

	@Test
	void conversionRequestTreatsUnknownResolverAsNetworkCapable() {
		ClassificationResolver network = new ClassificationResolver() {
			@Override public java.util.Optional<Resolution> resolve(Request request) {
				return java.util.Optional.empty();
			}
		};
		assertThrows(IllegalArgumentException.class, () -> new ConversionRequest("model.ifc",
				ConversionProfiles.SUPPLY_CHAIN).withClassificationResolver(network));
	}

	@Test
	void cliExposesExplicitEtimLoadingWithoutEnablingImplicitNetworkAccess() {
		var command = new picocli.CommandLine(new IFCtoLBDConverter_CLI()).getCommandSpec();
		assertTrue(command.findOption("--profile").description()[0].contains("materials-bmo"));
		assertTrue(command.findOption("--load-etim-metadata") != null);
		assertTrue(command.findOption("--load-etim") != null);
		assertTrue(command.findOption("--load-etim-candidates") != null);
		assertTrue(command.findOption("--classification-cache") != null);
		assertTrue(command.findOption("--etim-dictionary") != null);
	}
}
