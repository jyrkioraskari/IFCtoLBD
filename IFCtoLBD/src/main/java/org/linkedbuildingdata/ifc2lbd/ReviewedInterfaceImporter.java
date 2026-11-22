package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Imports local reviewer-confirmed contacts between two converted model revisions. */
public final class ReviewedInterfaceImporter {
	public record Endpoint(String modelScope, String sourceRevision, String elementIri) { }
	public record ReviewedPair(Endpoint first, Endpoint second, String verificationMethod,
			String reviewEvidence) { }
	public record Diagnostic(String code, String message) { }
	public record Result(Model graph, List<Diagnostic> diagnostics) implements AutoCloseable {
		@Override public void close() { graph.close(); }
	}
	private record Input(Model elements, String scope, String revision) { }

	/** Reads a JSON array of reviewed pairs without changing either conversion output. */
	public Result importPairs(ConversionResult first, ConversionResult second, Path reviewedPairs) throws IOException {
		List<ReviewedPair> pairs = new ObjectMapper().readValue(reviewedPairs.toFile(),
				new TypeReference<List<ReviewedPair>>() { });
		return importPairs(first.getModel(), first.getManifestModel(), second.getModel(),
				second.getManifestModel(), pairs);
	}

	/** The caller owns the input models and must close the returned companion graph. */
	public Result importPairs(Model firstElements, Model firstManifest, Model secondElements,
			Model secondManifest, List<ReviewedPair> pairs) {
		Objects.requireNonNull(pairs, "pairs");
		Model graph = ModelFactory.createDefaultModel();
		BOT.addNameSpace(graph);
		IFCtoLBDMapping.addNameSpace(graph);
		List<Diagnostic> diagnostics = new ArrayList<>();
		Input first = input(firstElements, firstManifest, "first", diagnostics);
		Input second = input(secondElements, secondManifest, "second", diagnostics);
		if (first == null || second == null) return new Result(graph, List.copyOf(diagnostics));
		for (ReviewedPair pair : pairs) {
			if (pair == null || pair.first() == null || pair.second() == null
					|| blank(pair.verificationMethod()) || blank(pair.reviewEvidence())) {
				diagnostics.add(new Diagnostic("INVALID_PAIR", "Each reviewed pair needs two endpoints, a method, and evidence."));
				continue;
			}
			Input left = resolveInput(pair.first(), first, second, diagnostics);
			Input right = resolveInput(pair.second(), first, second, diagnostics);
			if (left == null || right == null) continue;
			if (left == right) {
				diagnostics.add(new Diagnostic("SAME_MODEL_PAIR", "Reviewed interface endpoints must belong to different input models."));
				continue;
			}
			Resource a = endpoint(pair.first(), left, diagnostics);
			Resource b = endpoint(pair.second(), right, diagnostics);
			if (a == null || b == null) continue;
			if (a.getURI().equals(b.getURI())) {
				diagnostics.add(new Diagnostic("IRI_COLLISION", "Both models resolve to " + a.getURI()
						+ "; reconvert with distinct model scopes before importing this pair."));
				continue;
			}
			String leftKey = key(pair.first());
			String rightKey = key(pair.second());
			String[] sorted = new String[] { leftKey, rightKey };
			java.util.Arrays.sort(sorted);
			Endpoint canonicalFirst = leftKey.compareTo(rightKey) <= 0 ? pair.first() : pair.second();
			Endpoint canonicalSecond = leftKey.compareTo(rightKey) <= 0 ? pair.second() : pair.first();
			Resource contact = graph.createResource("urn:ifctolbd:reviewed-interface:"
					+ hash(sorted[0] + "|" + sorted[1]))
					.addProperty(RDF.type, BOT.bot_interface)
					.addProperty(BOT.bot_interfaceOf, graph.createResource(a.getURI()))
					.addProperty(BOT.bot_interfaceOf, graph.createResource(b.getURI()))
					.addProperty(IFCtoLBDMapping.interfaceOrigin,
						graph.createResource(IFCtoLBDMapping.ns + "ReviewedPair"))
					.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "verificationMethod"),
						pair.verificationMethod());
			contact.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "reviewEvidence"), pair.reviewEvidence());
			contact.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "sourceRevision"),
					pair.first().modelScope() + ":" + pair.first().sourceRevision());
			contact.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "sourceRevision"),
					pair.second().modelScope() + ":" + pair.second().sourceRevision());
			contact.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "firstModelScope"),
					canonicalFirst.modelScope());
			contact.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "secondModelScope"),
					canonicalSecond.modelScope());
			contact.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "firstSourceRevision"),
					canonicalFirst.sourceRevision());
			contact.addLiteral(graph.createProperty(IFCtoLBDMapping.ns + "secondSourceRevision"),
					canonicalSecond.sourceRevision());
		}
		return new Result(graph, List.copyOf(diagnostics));
	}

	private static Input input(Model elements, Model manifest, String label, List<Diagnostic> diagnostics) {
		List<Resource> conversions = manifest.listResourcesWithProperty(RDF.type,
				manifest.createResource(ConversionManifest.NS + "Conversion")).toList();
		if (conversions.size() != 1) {
			diagnostics.add(new Diagnostic("INVALID_MANIFEST", label + " manifest must identify one conversion."));
			return null;
		}
		Resource conversion = conversions.getFirst();
		String scope = literal(conversion, "modelScope");
		String revision = literal(conversion, "sourceChecksum");
		if (blank(scope) || blank(revision)) {
			diagnostics.add(new Diagnostic("MISSING_SCOPE_OR_REVISION", label
					+ " manifest needs modelScope and sourceChecksum."));
			return null;
		}
		return new Input(elements, scope, revision);
	}

	private static Input resolveInput(Endpoint endpoint, Input first, Input second,
			List<Diagnostic> diagnostics) {
		List<Input> matching = new ArrayList<>();
		for (Input input : List.of(first, second))
			if (input.scope().equals(endpoint.modelScope()) && input.revision().equals(endpoint.sourceRevision()))
				matching.add(input);
		if (matching.size() != 1) {
			diagnostics.add(new Diagnostic(matching.isEmpty() ? "MISSING_MODEL_REVISION" : "AMBIGUOUS_MODEL_REVISION",
					"Endpoint " + endpoint + " matches " + matching.size() + " input revisions."));
			return null;
		}
		return matching.getFirst();
	}

	private static Resource endpoint(Endpoint endpoint, Input input, List<Diagnostic> diagnostics) {
		if (blank(endpoint.elementIri())) {
			diagnostics.add(new Diagnostic("MISSING_ENDPOINT", "Endpoint has no element IRI."));
			return null;
		}
		Resource element = input.elements().createResource(endpoint.elementIri());
		if (!element.hasProperty(RDF.type, BOT.element)) {
			diagnostics.add(new Diagnostic("MISSING_ENDPOINT", "No BOT element " + endpoint.elementIri()
					+ " in " + input.scope() + ":" + input.revision() + "."));
			return null;
		}
		return element;
	}

	private static String literal(Resource resource, String name) {
		var statement = resource.getProperty(resource.getModel().createProperty(ConversionManifest.NS + name));
		return statement == null || !statement.getObject().isLiteral() ? null : statement.getString();
	}
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static String key(Endpoint endpoint) {
		return endpoint.modelScope() + "|" + endpoint.sourceRevision() + "|" + endpoint.elementIri();
	}
	private static String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}
