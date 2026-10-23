package org.linkedbuildingdata.ifc2lbd;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.OWL;
import org.linkedbuildingdata.ifc2lbd.core.utils.IfcOWLUtils;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Stable element identity based exclusively on project namespace and IFC GlobalId. */
public final class StableProjectUriPolicy implements UriPolicy {
	public static final String ID = "stable-project-globalid-v1";
	private static final String NS = "https://w3id.org/ifctolbd/identity#";
	private final String projectNamespace;
	private final Map<String, Resource> firstSourceByGlobalId = new HashMap<>();
	private final Model report = ModelFactory.createDefaultModel();

	public StableProjectUriPolicy(String projectNamespace) {
		this.projectNamespace = validateNamespace(projectNamespace);
		report.setNsPrefix("identity", NS);
	}

	public String projectNamespace() { return projectNamespace; }
	@Override public String id() { return ID; }
	@Override public String configurationId() { return ID + "@" + projectNamespace; }

	@Override public Resource createResource(Resource source, Model output, String productType, IfcOWL ifcOWL,
			String ignoredBaseUri, boolean exportIfcOWL) {
		String globalId = IfcOWLUtils.getGUID(source, ifcOWL);
		Resource result;
		if (!usable(globalId)) {
			result = output.createResource(unstableIri(source, "missing"));
			issue(source, globalId, "MissingGlobalId", "No usable IFC GlobalId; this element has conversion-local identity.");
		} else {
			Resource previous = firstSourceByGlobalId.putIfAbsent(globalId, source);
			if (previous == null) {
				result = output.createResource(join(projectNamespace, "ifc-globalid/" + encode(globalId)));
			} else {
				result = output.createResource(unstableIri(source, "duplicate"));
				issue(previous, globalId, "DuplicateGlobalId", "The GlobalId occurs more than once; mappings are ambiguous.");
				issue(source, globalId, "DuplicateGlobalId", "The GlobalId occurs more than once; this element has conversion-local identity.");
			}
		}
		if (exportIfcOWL) result.addProperty(OWL.sameAs, source);
		return result;
	}

	public Model report() { return report; }

	private void issue(Resource source, String globalId, String type, String message) {
		String key = hash(source.toString() + "|" + type);
		Resource issue = report.createResource("urn:ifctolbd:identity-issue:" + key)
				.addProperty(RDF.type, report.createResource(NS + type))
				.addLiteral(report.createProperty(NS + "message"), message)
				.addLiteral(report.createProperty(NS + "sourceResource"), source.toString());
		if (globalId != null) issue.addLiteral(report.createProperty(NS + "globalId"), globalId);
	}

	private String unstableIri(Resource source, String reason) {
		return join(projectNamespace, ".unstable/" + reason + "/" + hash(source.toString()));
	}

	static String validateNamespace(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("projectNamespace must not be blank");
		try {
			URI uri = URI.create(value.trim());
			if (!uri.isAbsolute() || uri.getScheme().equalsIgnoreCase("file") || uri.getRawQuery() != null)
				throw new IllegalArgumentException("projectNamespace must be an absolute non-file IRI without a query");
			return value.trim();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("projectNamespace must be a valid absolute IRI", e);
		}
	}

	private static boolean usable(String value) {
		return value != null && value.matches("[0-9A-Za-z_$]{22}");
	}

	private static String join(String namespace, String suffix) {
		return namespace + (namespace.endsWith("/") || namespace.endsWith("#") ? "" : "/") + suffix;
	}

	/** RFC 3986 percent encoding over UTF-8; no form-url '+' substitution. */
	static String encode(String value) {
		StringBuilder encoded = new StringBuilder();
		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xff;
			if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
					|| c == '-' || c == '.' || c == '_' || c == '~') encoded.append((char) c);
			else encoded.append('%').append(String.format("%02X", c));
		}
		return encoded.toString();
	}

	private static String hash(String value) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}
