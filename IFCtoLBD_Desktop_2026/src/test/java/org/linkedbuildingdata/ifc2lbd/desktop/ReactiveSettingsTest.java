package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReactiveSettingsTest {
	@Test
	void previewsAllTurtleOutputFiles() {
		assertEquals(List.of("/tmp/model.ttl", "/tmp/model_building_elements.ttl",
				"/tmp/model_element_properties.ttl", "/tmp/model.trig"),
				IFCtoLBDController.proposedOutputFiles("/tmp/model.ttl", false, false,
						true, true, true, true, true));
	}

	@Test
	void previewsJsonLdPartitionNamesUsedByConverter() {
		assertEquals(List.of("/tmp/model.jsonld", "/tmp/model_building_elements.json",
				"/tmp/model_element_properties.json"),
				IFCtoLBDController.proposedOutputFiles("/tmp/model.jsonld", true, false,
						true, true, true, true, false));
	}

	@Test
	void packagedOutputHasNoSeparateSidecars() {
		assertEquals(List.of("/tmp/model.icdd"),
				IFCtoLBDController.proposedOutputFiles("/tmp/model.icdd", false, true,
						true, true, true, true, true));
	}
}
