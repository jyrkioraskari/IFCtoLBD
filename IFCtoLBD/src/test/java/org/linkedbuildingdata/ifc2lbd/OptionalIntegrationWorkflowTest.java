package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

@Tag("integration")
class OptionalIntegrationWorkflowTest {
	private static final String GUID = "3cUkl32yn9qRSPvBJVyWw5";
	private static final String SENSOR = "https://example.org/sensors/temperature-1";
	private static final String PREDICATE = "https://www.w3.org/ns/sosa/hosts";

	@Test
	void defaultAndExplicitExistingIdentityAreGraphEquivalent() throws Exception {
		File ifc = new File(getClass().getResource("/SampleHouse.ifc").toURI());
		try (ConversionSession firstSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				ConversionSession secondSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter first = new IFCtoLBDConverter(firstSession, "https://example.com/");
				IFCtoLBDConverter second = new IFCtoLBDConverter(secondSession, "https://example.com/");
				ConversionResult implicit = first.convert(new ConversionRequest(ifc.getAbsolutePath(), ConversionProfiles.CORE));
				ConversionResult explicit = second.convert(new ConversionRequest(ifc.getAbsolutePath(), ConversionProfiles.CORE)
						.withExistingIdentity())) {
			assertTrue(implicit.getModel().isIsomorphicWith(explicit.getModel()));
			assertTrue(implicit.getExternalLinkModel().isEmpty());
			assertTrue(implicit.getIdentityReportModel().isEmpty());
		}
	}

	@Test
	void stableIdentitySurvivesFileRenameAndMappingsStaySeparate(@TempDir Path temp) throws Exception {
		Path original = Path.of(getClass().getResource("/SampleHouse.ifc").toURI());
		Path renamed = temp.resolve("renamed-and-reordered-project-revision.ifc");
		Files.copy(original, renamed);
		Path mapping = temp.resolve("links.json");
		Files.writeString(mapping, """
				[{"globalId":"%s","externalResourceIri":"%s","predicateIri":"%s",
				  "source":"commissioning-register","method":"reviewed-import","confidence":0.95}]
				""".formatted(GUID, SENSOR, PREDICATE));
		String namespace = "https://example.org/projects/persistent-a";
		try (ConversionSession firstSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				ConversionSession secondSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter first = new IFCtoLBDConverter(firstSession, "https://ignored.example/");
				IFCtoLBDConverter second = new IFCtoLBDConverter(secondSession, "https://another-base.example/");
				ConversionResult a = first.convert(new ConversionRequest(original.toString(), ConversionProfiles.CORE)
						.withStableProjectIdentity(namespace).withExternalLinkMappings(mapping));
				ConversionResult b = second.convert(new ConversionRequest(renamed.toString(), ConversionProfiles.CORE)
						.withStableProjectIdentity(namespace).withExternalLinkMappings(mapping))) {
			String iri = namespace + "/ifc-globalid/" + GUID;
			assertTrue(a.getModel().containsResource(a.getModel().createResource(iri)));
			assertTrue(b.getModel().containsResource(b.getModel().createResource(iri)));
			assertFalse(a.getModel().contains(null, a.getModel().createProperty(PREDICATE)));
			assertTrue(a.getExternalLinkModel().contains(a.getExternalLinkModel().createResource(iri),
					a.getExternalLinkModel().createProperty(PREDICATE), a.getExternalLinkModel().createResource(SENSOR)));
			assertTrue(a.getExternalLinkReportModel().contains(null,
					a.getExternalLinkReportModel().createProperty(ExternalLinkProcessor.NS + "status"), "matched"));
			String manifest = a.getManifestModel().toString();
			try (var serialized = new java.io.ByteArrayOutputStream()) {
				org.apache.jena.riot.RDFDataMgr.write(serialized, a.getManifestModel(),
						org.apache.jena.riot.RDFFormat.TURTLE_PRETTY);
				manifest = serialized.toString(java.nio.charset.StandardCharsets.UTF_8);
			}
			assertFalse(manifest.contains(original.toAbsolutePath().toString()));
			assertFalse(manifest.contains(mapping.toAbsolutePath().toString()));
			assertTrue(manifest.contains("sourceChecksum"));
			assertTrue(manifest.contains("mappingChecksum"));
			assertTrue(manifest.contains("validationStatus"));
		}
	}

	@Test
	void mappingsReportInvalidUnmatchedAmbiguousAndDuplicateRows(@TempDir Path temp) throws Exception {
		Path csv = temp.resolve("links.csv");
		Files.writeString(csv, """
				globalId,externalResourceIri,predicateIri,source,method,confidence
				3cUkl32yn9qRSPvBJVyWw5,https://example.org/a,https://example.org/rel,s,m,0.8
				3cUkl32yn9qRSPvBJVyWw5,https://example.org/a,https://example.org/rel,s,m,0.8
				09J5N7xMHBfQZeQGAEMota,https://example.org/b,https://example.org/rel,s,m,0.5
				bad guid,not an iri,https://example.org/rel,s,m,2
				""");
		Model model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
		var one = model.createResource("https://example.org/one");
		var two = model.createResource("https://example.org/two");
		var result = ExternalLinkProcessor.process(java.util.List.of(csv), java.util.Map.of(
				GUID, java.util.List.of(one), "09J5N7xMHBfQZeQGAEMota", java.util.List.of(one, two)));
		var status = result.report().createProperty(ExternalLinkProcessor.NS + "status");
		assertTrue(result.report().contains(null, status, "matched"));
		assertTrue(result.report().contains(null, status, "duplicate"));
		assertTrue(result.report().contains(null, status, "ambiguous"));
		assertTrue(result.report().contains(null, status, "invalid"));
		assertEquals(1, result.links().size());
	}

	@Test
	void stableProjectNamespaceIsMandatoryAndDistinct() {
		assertThrows(IllegalArgumentException.class, () -> new ConversionRequest("x.ifc", new ConversionProperties())
				.withStableProjectIdentity("relative/project"));
		var first = new StableProjectUriPolicy("https://example.org/project/a");
		var second = new StableProjectUriPolicy("https://example.org/project/b");
		assertNotEquals(first.configurationId(), second.configurationId());
	}

	@Test
	void missingAndDuplicateGlobalIdsAreReportedWithoutMerging() {
		var sourceModel = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
		var output = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
		var ifcOWL = new IfcOWL("https://standards.buildingsmart.org/IFC/DEV/IFC4/ADD2/OWL#");
		var first = sourceModel.createResource("urn:ifc:first");
		var second = sourceModel.createResource("urn:ifc:second");
		var missing = sourceModel.createResource("urn:ifc:missing");
		first.addProperty(ifcOWL.getGuid(), sourceModel.createResource()
				.addLiteral(IfcOWL.Express.getHasString(), GUID));
		second.addProperty(ifcOWL.getGuid(), sourceModel.createResource()
				.addLiteral(IfcOWL.Express.getHasString(), GUID));

		var policy = new StableProjectUriPolicy("https://example.org/project");
		var firstResult = policy.createResource(first, output, "Wall", ifcOWL, "https://ignored/", false);
		var secondResult = policy.createResource(second, output, "Wall", ifcOWL, "https://ignored/", false);
		var missingResult = policy.createResource(missing, output, "Wall", ifcOWL, "https://ignored/", false);

		assertNotEquals(firstResult, secondResult);
		assertTrue(secondResult.getURI().contains("/.unstable/duplicate/"));
		assertTrue(missingResult.getURI().contains("/.unstable/missing/"));
		assertTrue(policy.report().contains(null, org.apache.jena.vocabulary.RDF.type,
				policy.report().createResource("https://w3id.org/ifctolbd/identity#DuplicateGlobalId")));
		assertTrue(policy.report().contains(null, org.apache.jena.vocabulary.RDF.type,
				policy.report().createResource("https://w3id.org/ifctolbd/identity#MissingGlobalId")));
	}
}
