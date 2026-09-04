package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

class ConversionManifestFingerprintTest {
	private final Path source;

	ConversionManifestFingerprintTest() throws java.net.URISyntaxException {
		this.source = Path.of(getClass().getResource("/SampleHouse.ifc").toURI());
	}

	@Test
	void legacyFlagsCannotShareACacheIdentity() {
		ConversionProperties first = new ConversionProperties();
		ConversionProperties second = new ConversionProperties();
		second.setHasGeolocation(true);
		assertNotEquals(fingerprint(new ConversionRequest(source.toString(), first), "https://example.com/", store("none")),
				fingerprint(new ConversionRequest(source.toString(), second), "https://example.com/", store("none")));
	}

	@Test
	void allExternalRequestConfigurationAffectsFingerprint() {
		ConversionRequest base = new ConversionRequest(source.toString(), ConversionProfiles.CORE);
		String original = fingerprint(base, "https://example.com/", store("none"));
		assertNotEquals(original, fingerprint(base, "https://other.example/", store("none")));
		assertNotEquals(original, fingerprint(base.withSelectedTypes(Set.of("Wall")), "https://example.com/", store("none")));
		assertNotEquals(original, fingerprint(base.withSelectedPropertySets(Set.of("Pset_WallCommon")), "https://example.com/", store("none")));
		assertNotEquals(original, fingerprint(base.withValidation(ValidationShapePack.CORE_BOT), "https://example.com/", store("none")));
		assertNotEquals(original, fingerprint(base.withPropertyReplacements(Map.of("urn:old", "urn:new")), "https://example.com/", store("none")));
		assertNotEquals(original, fingerprint(base, "https://example.com/", store("filesystem")));
	}

	@Test
	void manifestCataloguesNamedGraphsAndPolicyConfiguration() {
		ConversionManifest.Created created = createdManifest(new ConversionRequest(source.toString(), ConversionProfiles.REVISION_READY)
				.withModelScope("asset-7"), "https://example.com/", store("none"));
		Model manifest = created.model();
		assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "uriPolicyConfiguration"),
				"stable-guid-v1@asset-7"));
		for (String graph : created.graphNames().all())
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "hasNamedGraph"),
					manifest.createResource(graph)));
	}

	private String fingerprint(ConversionRequest request, String baseUri, GeometryArtifactStore store) {
		Model manifest = manifest(request, baseUri, store);
		return manifest.listStatements(null, manifest.createProperty(ConversionManifest.NS + "requestFingerprint"),
				(org.apache.jena.rdf.model.RDFNode) null).nextStatement().getString();
	}

	private Model manifest(ConversionRequest request, String baseUri, GeometryArtifactStore store) {
		return createdManifest(request, baseUri, store).model();
	}

	private ConversionManifest.Created createdManifest(ConversionRequest request, String baseUri, GeometryArtifactStore store) {
		UriPolicy policy = request.getModelScope().<UriPolicy>map(StableGuidUriPolicy::new)
				.orElse(LegacyUriPolicy.INSTANCE);
		return ConversionManifest.create(request, Instant.EPOCH,
				new ValidationStage.Result("not-run", ModelFactory.createDefaultModel(), List.of()), policy,
				NoGeometryProvider.INSTANCE, store, List.of(), baseUri, true, false);
	}

	private static GeometryArtifactStore store(String id) {
		return new GeometryArtifactStore() {
			@Override public String id() { return id; }
			@Override public Optional<GeometryArtifact> store(byte[] content, String mediaType, String extension,
					String levelOfDetail, String coordinateReferenceSystem) { return Optional.empty(); }
		};
	}
}
