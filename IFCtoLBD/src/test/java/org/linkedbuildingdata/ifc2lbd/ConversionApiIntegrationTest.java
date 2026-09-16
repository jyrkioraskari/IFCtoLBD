package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.HashSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class ConversionApiIntegrationTest {

	@Test
	void requestProducesAZeroCopyNonDuplicatedResult() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		ConversionProperties properties = new ConversionProperties();
		properties.setExportIfcOWL(false);
		properties.setHasPerformanceBoost(false);

		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/")) {
			ConversionRequest request = new ConversionRequest(ifcFile.getAbsolutePath(), properties);
			try (ConversionResult result = converter.convert(request)) {
				assertFalse(result.getModel().isEmpty());
				assertTrue(result.getGeneralModel().intersection(result.getProductModel()).isEmpty());
				assertTrue(result.getGeneralModel().intersection(result.getPropertyModel()).isEmpty());
				assertTrue(result.getGraphNames().product().contains(manifestValue(result, "cacheKey")));
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
	void coreProfileReadsIfcJson(@TempDir Path temporaryDirectory) throws Exception {
		Path input = temporaryDirectory.resolve("wall.ifcjson");
		Files.writeString(input, """
				{"type":"ifcJSON","schemaIdentifier":"IFC4","data":[
				  {"type":"IfcWall","globalId":"1hOSvn6df7F8_7GcBWlN4K","name":"JSON wall","predefinedType":"STANDARD"}
				]}
				""");
		assertStructuredIfcConverts(input);
	}

	@Test
	void coreProfileReadsIfcXml(@TempDir Path temporaryDirectory) throws Exception {
		Path input = temporaryDirectory.resolve("wall.ifcxml");
		Files.writeString(input, """
				<?xml version="1.0" encoding="UTF-8"?>
				<IfcWall xmlns="https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2/XML/IFC4_ADD2.xsd"
				    type="IfcWall" globalId="1hOSvn6df7F8_7GcBWlN4K" name="XML wall" predefinedType="STANDARD"/>
				""");
		assertStructuredIfcConverts(input);
	}

	private void assertStructuredIfcConverts(Path input) throws Exception {
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(), ConversionProfiles.CORE))) {
			assertFalse(result.getModel().isEmpty());
			assertEquals("IFC4_ADD2", manifestValue(result, "ifcSchema"));
		}
	}

	@Test
	void coreProfileDoesNotRunSupplyChainOrSustainabilityAndSkipsPsetOntologies() throws Exception {
		File ifcFile = new File(getClass().getResource("/TWO WALLS.ifc").toURI());
		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/");
				ConversionResult result = converter.convert(
						new ConversionRequest(ifcFile.getAbsolutePath(), ConversionProfiles.CORE))) {
			assertFalse(result.getModel().listStatements().toList().stream().anyMatch(statement ->
					statement.getPredicate().getURI().startsWith(SupplyChainStage.NS)
					|| statement.getPredicate().getURI().startsWith("https://w3id.org/ifctolbd/sustainability#")));
			assertFalse(converter.getOntology_model().containsResource(converter.getOntology_model()
					.createResource("http://www.buildingsmart-tech.org/ifcOWL/IFC4-PSD#InternalRefrigerantVolume")));
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
					"2.52.0"));
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
						ConversionProfiles.REVISION_READY).withModelScope("sample-house"))) {
			assertTrue(result.getModel().listSubjects().toList().stream()
					.anyMatch(resource -> resource.isURIResource() && resource.getURI().matches(
							"https://example\\.com/model/sample-house/element/[0-9a-f-]{36}")));
			var manifest = result.getManifestModel();
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "uriPolicy"),
					"stable-guid-v1"));
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "modelScope"),
					"sample-house"));
			assertTrue(manifest.contains(null,
					manifest.createProperty(ConversionManifest.NS + "uriPolicyConfiguration"),
					"stable-guid-v1@sample-house"));
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
						ConversionProfiles.REVISION_READY).withModelScope("sample-house"));
				ConversionResult second = secondConverter.convert(new ConversionRequest(ifcFile.getAbsolutePath(),
						ConversionProfiles.REVISION_READY).withModelScope("sample-house"));
				ConversionDiff diff = new RevisionComparator().compare(first, second)) {
			assertFalse(diff.hasChanges());
		}
	}

	@Test
	void stableIdentityRequiresModelScope() throws Exception {
		File ifcFile = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		try (ConversionSession session = new ConversionSession();
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/")) {
			assertThrows(IllegalArgumentException.class, () -> converter.convert(
					new ConversionRequest(ifcFile.getAbsolutePath(), ConversionProfiles.REVISION_READY)));
		}
	}

	@Test
	void changedIfcPropertyKeepsElementUrisAcrossRevisions(@TempDir Path temporaryDirectory) throws Exception {
		Path source = Path.of(getClass().getResource("/SampleHouse.ifc").toURI());
		Path previousIfc = temporaryDirectory.resolve("previous.ifc");
		Path currentIfc = temporaryDirectory.resolve("current.ifc");
		String original = Files.readString(source);
		String changed = original.replace("Living room 1 - Living room", "Living room 1 - Revised living room");
		assertNotEquals(original, changed);
		Files.writeString(previousIfc, original);
		Files.writeString(currentIfc, changed);
		ConversionProfile profile = ConversionProfile.of("revision-properties",
				BuiltInConversionModule.BOT_TOPOLOGY, BuiltInConversionModule.PRODUCT_ONTOLOGY,
				BuiltInConversionModule.SIMPLE_PROPERTIES, BuiltInConversionModule.STABLE_IDENTITY);

		try (ConversionSession previousSession = new ConversionSession();
				ConversionSession currentSession = new ConversionSession();
				IFCtoLBDConverter previousConverter = new IFCtoLBDConverter(previousSession, "https://example.com/");
				IFCtoLBDConverter currentConverter = new IFCtoLBDConverter(currentSession, "https://example.com/");
				ConversionResult previous = previousConverter.convert(
						new ConversionRequest(previousIfc.toString(), profile).withModelScope("building-42"));
				ConversionResult current = currentConverter.convert(
						new ConversionRequest(currentIfc.toString(), profile).withModelScope("building-42"));
				ConversionDiff diff = new RevisionComparator().compare(previous, current)) {
			Set<String> previousElements = elementUris(previous);
			Set<String> currentElements = elementUris(current);
			assertFalse(previousElements.isEmpty());
			assertEquals(previousElements, currentElements);
			assertNotEquals(manifestValue(previous, "sourceChecksum"), manifestValue(current, "sourceChecksum"));
			assertTrue(diff.hasChanges());
		}
	}

	private static Set<String> elementUris(ConversionResult result) {
		Set<String> uris = new HashSet<>();
		result.getDataset().asDatasetGraph().find().forEachRemaining(quad -> {
			if (quad.getSubject().isURI() && quad.getSubject().getURI().contains("/model/building-42/element/"))
				uris.add(quad.getSubject().getURI());
			if (quad.getObject().isURI() && quad.getObject().getURI().contains("/model/building-42/element/"))
				uris.add(quad.getObject().getURI());
		});
		return uris;
	}

	private static String manifestValue(ConversionResult result, String localName) {
		return result.getManifestModel().listStatements(null,
				result.getManifestModel().createProperty(ConversionManifest.NS + localName), (org.apache.jena.rdf.model.RDFNode) null)
				.nextStatement().getString();
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
