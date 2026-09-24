package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class SoftwareIntentStableIdentityTest {

	private static final String EXPECTED_IRI =
			"https://example.com/model/model%20a/element/9808fd7f-dc48-478e-9217-628e833d7d42";

	@Test
	void executesStableIdentitySpecificationExample(@TempDir Path temporaryDirectory) throws Exception {
		Path example = findSpecRoot().resolve("examples/stable-identity");
		Path original = example.resolve("wall.ifcjson");
		Path renamed = temporaryDirectory.resolve("revision-renamed.ifcjson");
		Files.copy(original, renamed);
		Path changed = temporaryDirectory.resolve("revision-changed.ifcjson");
		Files.writeString(changed, Files.readString(original).replace(
				"Stable identity example wall", "Revised wall name"));

		Set<String> originalUris = convertAndAssert(original, "model a", example);
		Set<String> renamedUris = convertAndAssert(renamed, "model a", example);
		assertEquals(originalUris, renamedUris, "renaming the input must not change stable element IRIs");
		assertEquals(originalUris, convertAndAssert(changed, "model a", example),
				"changing non-identity data must not change stable element IRIs");
		assertTrue(originalUris.contains(EXPECTED_IRI));

		Set<String> otherScopeUris = convertElementUris(original, "model-b", "https://example.com/#");
		assertFalse(otherScopeUris.contains(EXPECTED_IRI));
		assertTrue(otherScopeUris.contains(
				"https://example.com/model/model-b/element/9808fd7f-dc48-478e-9217-628e833d7d42"));

		Set<String> otherBaseUris = convertElementUris(original, "model a", "https://other.example/#");
		assertFalse(otherBaseUris.contains(EXPECTED_IRI));
		assertTrue(otherBaseUris.contains(
				"https://other.example/model/model%20a/element/9808fd7f-dc48-478e-9217-628e833d7d42"));

		assertThrows(IllegalArgumentException.class, () ->
				new ConversionRequest(original.toString(), ConversionProfiles.REVISION_READY).withModelScope(" "));
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/#")) {
			assertThrows(IllegalArgumentException.class, () -> converter.convert(
					new ConversionRequest(original.toString(), ConversionProfiles.REVISION_READY)));
		}
	}

	private static Set<String> convertAndAssert(Path input, String scope, Path example) throws Exception {
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.com/#");
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(),
						ConversionProfiles.REVISION_READY).withModelScope(scope))) {
			Model output = result.getModel();
			assertTrue(ask(output, example.resolve("required.ask.rq")));
			assertFalse(ask(output, example.resolve("forbidden.ask.rq")));
			Model manifest = result.getManifestModel();
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "uriPolicy"),
					"stable-guid-v1"));
			assertTrue(manifest.contains(null, manifest.createProperty(ConversionManifest.NS + "modelScope"), scope));
			assertTrue(manifest.contains(null,
					manifest.createProperty(ConversionManifest.NS + "uriPolicyConfiguration"),
					"stable-guid-v1@" + scope));
			return elementUris(output);
		}
	}

	private static Set<String> convertElementUris(Path input, String scope, String baseIri) throws Exception {
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, baseIri);
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(),
						ConversionProfiles.REVISION_READY).withModelScope(scope))) {
			return elementUris(result.getModel());
		}
	}

	private static boolean ask(Model model, Path queryFile) throws Exception {
		var query = QueryFactory.create(Files.readString(queryFile));
		try (var execution = QueryExecutionFactory.create(query, model)) {
			return execution.execAsk();
		}
	}

	private static Set<String> elementUris(Model model) {
		return model.listSubjects().toList().stream()
				.filter(resource -> resource.isURIResource() && resource.getURI().contains("/model/"))
				.map(resource -> resource.getURI())
				.collect(Collectors.toSet());
	}

	private static Path findSpecRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path candidate = current.resolve("spec");
			if (Files.isRegularFile(candidate.resolve("index.md"))) return candidate;
			current = current.getParent();
		}
		throw new IllegalStateException("Cannot locate spec/index.md from " + System.getProperty("user.dir"));
	}
}
