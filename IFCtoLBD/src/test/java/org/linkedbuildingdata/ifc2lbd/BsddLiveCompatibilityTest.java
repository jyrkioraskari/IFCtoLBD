package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicit, opt-in compatibility check for the public buildingSMART bSDD API. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "BSDD_LIVE_TEST", matches = "true")
class BsddLiveCompatibilityTest {
	private static final String DICTIONARY =
			"https://identifier.buildingsmart.org/uri/etim/etim/9.0";

	@Test
	void retrievesRegistryIdentifiersWithoutUsingLabelDerivedUris() {
		var resolver = new BsddClassificationResolver(Map.of("ETIM", DICTIONARY));
		var resolution = new ClassificationResolver.Resolution(DICTIONARY + "/class/EC002288",
				"buildingSMART-bSDD-v1", "9.0", 1.0,
				ClassificationResolver.IdentifierVerification.REGISTRY_VERIFIED, "9.0", "9.0",
				ClassificationResolver.EditionCompatibility.MATCH);
		assertEquals(DICTIONARY + "/class/EC002288", resolution.conceptUri());
		assertTrue(resolution.isRegistryVerified());

		var definition = resolver.definition(resolution).orElseThrow();
		var mounting = definition.classProperties().stream()
				.filter(value -> value.property() != null && "EF000003".equals(value.property().code()))
				.findFirst().orElseThrow();
		assertEquals(DICTIONARY + "/prop/EF000003", mounting.property().uri());
		assertEquals(DICTIONARY + "/class/EC002288/prop/undefined_set/EF000003", mounting.uri());
		assertNotEquals(mounting.property().uri(), mounting.uri());

		var dictionary = resolver.dictionary("ETIM").orElseThrow();
		assertEquals(DICTIONARY, dictionary.uri());
		assertEquals("ETIM International", dictionary.organizationNameOwner());
		assertEquals("No license (rights reserved)", dictionary.license());
		assertTrue(dictionary.licenseUrl().startsWith("https://"));
	}
}
