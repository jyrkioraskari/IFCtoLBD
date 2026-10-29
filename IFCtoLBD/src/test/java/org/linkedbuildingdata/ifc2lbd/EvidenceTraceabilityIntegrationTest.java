package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

class EvidenceTraceabilityIntegrationTest {
	@Test
	void evidenceProfileAddsElementScopedCompanionProvenanceWithoutLocalPaths() throws Exception {
		File source = new File(getClass().getResource("/TWO WALLS.ifc").toURI());
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.test/");
				ConversionResult result = converter.convert(new ConversionRequest(source.getAbsolutePath(),
						ConversionProfiles.EVIDENCE))) {
			var manifest = result.getManifestModel();
			assertTrue(manifest.contains(null, manifest.createProperty(EvidenceTraceabilityStage.NS + "mappingKind"), "DIRECT"));
			assertTrue(manifest.contains(null, IFCtoLBDMapping.ifcGlobalId));
			assertTrue(manifest.contains(null, manifest.createProperty("http://www.w3.org/ns/prov#wasDerivedFrom")));
			assertFalse(manifest.listObjects().toList().stream().anyMatch(node ->
					node.isURIResource() && node.asResource().getURI().startsWith("file:")));
			assertFalse(manifest.listObjects().toList().stream().anyMatch(node ->
					node.isLiteral() && node.asLiteral().getString().equals(source.getAbsolutePath())));
		}
	}

	@Test
	void coreProfileDoesNotAddElementEvidence() throws Exception {
		File source = new File(getClass().getResource("/TWO WALLS.ifc").toURI());
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.test/");
				ConversionResult result = converter.convert(new ConversionRequest(source.getAbsolutePath(),
						ConversionProfiles.CORE))) {
			assertFalse(result.getManifestModel().contains(null,
					result.getManifestModel().createProperty(EvidenceTraceabilityStage.NS + "mappingKind")));
		}
	}
}
