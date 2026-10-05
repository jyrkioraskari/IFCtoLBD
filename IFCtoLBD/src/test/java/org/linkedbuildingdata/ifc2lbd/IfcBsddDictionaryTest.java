package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcBsddDictionary;

class IfcBsddDictionaryTest {

	@Test
	void resolvesCanonicalUrisFromBundledIfc43Index() {
		IfcBsddDictionary dictionary = IfcBsddDictionary.get();

		assertEquals("IFC4X3_ADD2", dictionary.version());
		assertEquals("https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/class/Pset_BuildingCommon",
				dictionary.propertySet("pset_buildingcommon").orElseThrow().uri());
		assertEquals("https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/prop/NumberOfStoreys",
				dictionary.property("Pset_BuildingCommon", "numberofstoreys").orElseThrow().uri());
	}

	@Test
	void requiresThePropertyToBelongToTheResolvedSet() {
		IfcBsddDictionary dictionary = IfcBsddDictionary.get();

		assertTrue(dictionary.property("Pset_WallCommon", "NumberOfStoreys").isEmpty());
		assertTrue(dictionary.property("Acme_Custom", "NumberOfStoreys").isEmpty());
		assertTrue(dictionary.propertySet("Acme_Custom").isEmpty());
	}
}
