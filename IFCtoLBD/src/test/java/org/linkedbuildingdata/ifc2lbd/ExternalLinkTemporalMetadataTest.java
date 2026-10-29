package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalLinkTemporalMetadataTest {
	private static final String GUID = "1hOSvn6df7F8_7GcBWlN4K";

	@Test
	void retainsSuppliedTemporalMetadataInSeparateReport(@TempDir Path temporaryDirectory) throws Exception {
		Path mapping = temporaryDirectory.resolve("links.json");
		Files.writeString(mapping, """
				[{"globalId":"%s","externalResourceIri":"https://example.test/manual",
				  "predicateIri":"http://purl.org/dc/terms/references","source":"CMMS export",
				  "method":"reviewed","validFrom":"2026-01-01T00:00:00Z",
				  "validTo":"2027-01-01T00:00:00Z","recordedAt":"2026-02-01T12:00:00Z"}]
				""".formatted(GUID));
		var model = ModelFactory.createDefaultModel();
		var result = ExternalLinkProcessor.process(List.of(mapping), Map.of(GUID,
				List.of(model.createResource("https://example.test/element"))));
		assertTrue(result.report().contains(null, result.report().createProperty(ExternalLinkProcessor.NS + "validFrom")));
		assertTrue(result.report().contains(null, result.report().createProperty(ExternalLinkProcessor.NS + "recordedAt")));
		assertEquals(1, result.links().size());
	}

	@Test
	void reportsInvalidTemporalRangesWithoutAttachingLink(@TempDir Path temporaryDirectory) throws Exception {
		Path mapping = temporaryDirectory.resolve("links.csv");
		Files.writeString(mapping, "globalId,externalResourceIri,predicateIri,validFrom,validTo\n"
				+ GUID + ",https://example.test/work-order,http://purl.org/dc/terms/references,"
				+ "2027-01-01T00:00:00Z,2026-01-01T00:00:00Z\n");
		var model = ModelFactory.createDefaultModel();
		var result = ExternalLinkProcessor.process(List.of(mapping), Map.of(GUID,
				List.of(model.createResource("https://example.test/element"))));
		assertTrue(result.report().contains(null, result.report().createProperty(ExternalLinkProcessor.NS + "status"), "invalid"));
		assertTrue(result.links().isEmpty());
	}
}
