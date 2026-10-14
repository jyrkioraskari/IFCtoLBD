package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
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

	@Test
	void previewShowsTheFullPathForOneOutput() {
		Path target = Path.of("exports", "model.icdd");
		assertEquals(target.toAbsolutePath().toString(),
				IFCtoLBDController.proposedOutputText(List.of(target.toString())));
	}

	@Test
	void previewNamesEveryOutputWhenSeveralFilesAreCreated() {
		String target = Path.of("exports", "model.ttl").toString();
		List<String> files = IFCtoLBDController.proposedOutputFiles(target, false, false,
				true, true, true, true, true);
		assertEquals("4 output files:\n" + files.stream()
				.map(path -> Path.of(path).toAbsolutePath().toString())
				.collect(java.util.stream.Collectors.joining("\n")),
				IFCtoLBDController.proposedOutputText(files));
	}
}
