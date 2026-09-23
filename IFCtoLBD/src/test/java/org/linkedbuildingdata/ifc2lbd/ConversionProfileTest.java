package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConversionProfileTest {
	@Test
	void resolvesProfilesByPublicName() {
		assertSame(ConversionProfiles.PROPERTIES_OPM, ConversionProfiles.named("properties-opm"));
		assertSame(ConversionProfiles.GEOMETRY_FULL, ConversionProfiles.named(" GEOMETRY-FULL "));
		assertThrows(IllegalArgumentException.class, () -> ConversionProfiles.named("missing"));
	}

	@Test
	void coreProfileDoesNotEnableOptionalOutput() {
		ConversionProperties properties = ConversionProfiles.CORE.toConversionProperties();
		assertTrue(properties.isHasBuildingElements());
		assertFalse(properties.isHasBuildingProperties());
		assertFalse(properties.isHasGeometry());
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
}
