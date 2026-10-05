package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

class RevisionComparatorTest {
	private static final String NAME = "urn:test:name";

	@Test
	void unchangedDataIgnoresManifestDifferences() {
		try (ConversionResult previous = result("old", "checksum-1", "stable-guid-v1", "building-a", model("Wall"));
				ConversionResult current = result("new", "checksum-2", "stable-guid-v1", "building-a", model("Wall"));
				ConversionDiff diff = new RevisionComparator().compare(previous, current)) {
			assertFalse(diff.hasChanges());
			assertEquals(0, diff.getAddedCount());
			assertEquals(0, diff.getRemovedCount());
			assertTrue(diff.getChangeModel().contains(null,
					diff.getChangeModel().createProperty("http://www.w3.org/ns/prov#wasRevisionOf")));
		}
	}

	@Test
	void changedAssertionProducesAddedAndRemovedStatements() {
		try (ConversionResult previous = result("old", "checksum-1", "stable-guid-v1", "building-a", model("Wall"));
				ConversionResult current = result("new", "checksum-2", "stable-guid-v1", "building-a", model("Renamed wall"));
				ConversionDiff diff = new RevisionComparator().compare(previous, current)) {
			assertTrue(diff.hasChanges());
			assertEquals(1, diff.getAddedCount());
			assertEquals(1, diff.getRemovedCount());
			assertTrue(diff.getChangeModel().contains(null, org.apache.jena.vocabulary.RDF.type,
					diff.getChangeModel().createResource(RevisionComparator.NS + "AddedStatement")));
			assertTrue(diff.getChangeModel().contains(null, org.apache.jena.vocabulary.RDF.type,
					diff.getChangeModel().createResource(RevisionComparator.NS + "RemovedStatement")));
		}
	}

	@Test
	void legacyIdentityIsRejected() {
		try (ConversionResult previous = result("old", "checksum-1", "legacy-v1", "building-a", model("Wall"));
				ConversionResult current = result("new", "checksum-2", "legacy-v1", "building-a", model("Wall"))) {
			assertThrows(IllegalArgumentException.class, () -> new RevisionComparator().compare(previous, current));
		}
	}

	@Test
	void differentModelScopesAreRejected() {
		try (ConversionResult previous = result("old", "checksum-1", "stable-guid-v1", "building-a", model("Wall"));
				ConversionResult current = result("new", "checksum-2", "stable-guid-v1", "building-b", model("Wall"))) {
			assertThrows(IllegalArgumentException.class, () -> new RevisionComparator().compare(previous, current));
		}
	}

	private static Model model(String name) {
		Model model = ModelFactory.createDefaultModel();
		model.createResource("https://example.com/model/a/element/guid").addLiteral(model.createProperty(NAME), name);
		return model;
	}

	private static ConversionResult result(String cacheKey, String checksum, String uriPolicy, String modelScope,
			Model data) {
		Model manifest = ModelFactory.createDefaultModel();
		var conversion = manifest.createResource("urn:ifctolbd:conversion:" + cacheKey)
				.addLiteral(manifest.createProperty(ConversionManifest.NS + "cacheKey"), cacheKey)
				.addLiteral(manifest.createProperty(ConversionManifest.NS + "sourceChecksum"), checksum)
				.addLiteral(manifest.createProperty(ConversionManifest.NS + "profile"), "revision-ready")
				.addLiteral(manifest.createProperty(ConversionManifest.NS + "uriPolicy"), uriPolicy)
				.addLiteral(manifest.createProperty(ConversionManifest.NS + "uriPolicyConfiguration"),
						uriPolicy + "@" + modelScope)
				.addLiteral(manifest.createProperty(ConversionManifest.NS + "modelScope"), modelScope);
		conversion.addProperty(manifest.createProperty(ConversionManifest.NS + "usesModule"),
				manifest.createResource("urn:ifctolbd:module:stable-identity:1"));
		String base = "urn:ifctolbd:conversion:" + cacheKey + ":graph:";
		return ConversionResult.of(data, ModelFactory.createDefaultModel(), ModelFactory.createDefaultModel(),
				manifest, ModelFactory.createDefaultModel(), new ConversionGraphNames(base + "product",
						base + "property", base + "manifest", base + "validation"));
	}
}
