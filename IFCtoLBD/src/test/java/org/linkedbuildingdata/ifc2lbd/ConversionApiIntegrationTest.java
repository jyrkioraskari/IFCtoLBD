package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class ConversionApiIntegrationTest {

	@Test
	void requestProducesAResultSnapshot() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		ConversionProperties properties = new ConversionProperties();
		properties.setExportIfcOWL(false);
		properties.setHasPerformanceBoost(false);

		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/")) {
			ConversionRequest request = new ConversionRequest(ifcFile.getAbsolutePath(), properties);
			try (ConversionResult result = converter.convert(request)) {
				assertFalse(result.getModel().isEmpty());
			}
		}
	}

	@Test
	void coreProfileConvertsThroughTheCompatibilityEngine() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/")) {
			assertFalse(converter.convert(ifcFile.getAbsolutePath(), ConversionProfiles.CORE).isEmpty());
		}
	}

	@Test
	void profileRequestIncludesAConversionManifest() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
		try (ConversionSession session = new ConversionSession(clock);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.CORE))) {
			var manifest = result.getManifestModel();
			assertFalse(manifest.isEmpty());
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "profile"), "core"));
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "converterVersion"),
					"2.51.0"));
			assertTrue(manifest.contains(null, manifest.createProperty("http://www.w3.org/ns/prov#generatedAtTime")));
		}
	}

	@Test
	void complianceProfileReturnsShaclReportAndStatus() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.COMPLIANCE))) {
			assertFalse(result.getValidationModel().isEmpty());
			var manifest = result.getManifestModel();
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "validationStatus"),
					"conforms"));
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "shapePack"),
					"shacl/core-bot-v1.0.0.ttl"));
		}
	}

	@Test
	void revisionReadyProfileUsesStableGuidUris() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.REVISION_READY))) {
			assertTrue(result.getModel().listSubjects().toList().stream()
					.anyMatch(resource -> resource.isURIResource() && resource.getURI().matches(
							"https://example\\.com/model/[0-9a-f]{16}/element/[0-9a-f-]{36}")));
			var manifest = result.getManifestModel();
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "uriPolicy"),
					"stable-guid-v1"));
		}
	}

	@Test
	void independentlyConvertedStableRevisionsCompareEqual() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		try (ConversionSession firstSession = new ConversionSession();
				ConversionSession secondSession = new ConversionSession();
				IFCtoLBDConverter firstConverter = new IFCtoLBDConverter(firstSession, "https://example.com/");
				IFCtoLBDConverter secondConverter = new IFCtoLBDConverter(secondSession, "https://example.com/");
				ConversionResult first = firstConverter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.REVISION_READY));
				ConversionResult second = secondConverter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.REVISION_READY));
				ConversionDiff diff = new RevisionComparator().compare(first, second)) {
			assertFalse(diff.hasChanges());
		}
	}

	@Test
	void manifestRecordsInjectedGeometryProvider() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.CORE))) {
			var manifest = result.getManifestModel();
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "geometryProvider"),
					"none"));
			assertTrue(manifest.contains(null,
					manifest.createProperty(ConversionManifest.NS + "geometryProviderVersion"), "1"));
		}
	}

	@Test
	void realisticIfcFixtureEmitsClassificationAssertions() throws Exception {
		File ifcFile = new File(getClass().getResource("/TWO WALLS.ifc").toURI());
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.SUPPLY_CHAIN))) {
			var model = result.getModel();
			assertTrue(model.contains(null, model.createProperty(SupplyChainStage.NS + "hasClassification")));
			assertTrue(model.contains(null, model.createProperty(SupplyChainStage.NS + "code"), "B2010"));
		}
	}
}
