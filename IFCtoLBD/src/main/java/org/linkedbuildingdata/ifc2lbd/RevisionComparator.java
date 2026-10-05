package org.linkedbuildingdata.ifc2lbd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;

/** Compares stable-identity conversion snapshots and emits an RDF change graph. */
public final class RevisionComparator {
	public static final String NS = "https://w3id.org/ifctolbd/change#";
	private static final String PROV = "http://www.w3.org/ns/prov#";

	public ConversionDiff compare(ConversionResult previous, ConversionResult current) {
		Metadata oldMetadata = metadata(previous);
		Metadata newMetadata = metadata(current);
		requireCompatible(oldMetadata, newMetadata);

		Set<Quad> oldQuads = conversionQuads(previous);
		Set<Quad> newQuads = conversionQuads(current);
		List<Quad> removed = difference(oldQuads, newQuads);
		List<Quad> added = difference(newQuads, oldQuads);

		Dataset output = DatasetFactory.createTxnMem();
		Model changes = output.getNamedModel(ConversionDiff.CHANGE_GRAPH);
		changes.setNsPrefix("change", NS);
		changes.setNsPrefix("prov", PROV);
		Resource comparison = changes.createResource("urn:ifctolbd:comparison:" + oldMetadata.cacheKey + ":"
				+ newMetadata.cacheKey).addProperty(RDF.type, changes.createResource(NS + "RevisionComparison"))
				.addLiteral(changes.createProperty(NS + "previousChecksum"), oldMetadata.sourceChecksum)
				.addLiteral(changes.createProperty(NS + "currentChecksum"), newMetadata.sourceChecksum)
				.addLiteral(changes.createProperty(NS + "addedCount"), added.size())
				.addLiteral(changes.createProperty(NS + "removedCount"), removed.size());
		Resource oldRevision = changes.createResource("urn:ifctolbd:conversion:" + oldMetadata.cacheKey);
		Resource newRevision = changes.createResource("urn:ifctolbd:conversion:" + newMetadata.cacheKey)
				.addProperty(changes.createProperty(PROV + "wasRevisionOf"), oldRevision);
		comparison.addProperty(changes.createProperty(NS + "previousRevision"), oldRevision)
				.addProperty(changes.createProperty(NS + "currentRevision"), newRevision);
		addChanges(changes, comparison, removed, "RemovedStatement");
		addChanges(changes, comparison, added, "AddedStatement");
		return new ConversionDiff(output, added.size(), removed.size());
	}

	private static Set<Quad> conversionQuads(ConversionResult result) {
		Set<Quad> quads = new HashSet<>();
		ConversionGraphNames names = result.getGraphNames();
		result.getDataset().asDatasetGraph().find().forEachRemaining(quad -> {
			String graph = quad.getGraph().isURI() ? quad.getGraph().getURI() : "";
			if (!names.manifest().equals(graph) && !names.validation().equals(graph)) {
				quads.add(normalizeGraphRole(quad, names));
			}
		});
		return quads;
	}

	private static Quad normalizeGraphRole(Quad quad, ConversionGraphNames names) {
		if (!quad.getGraph().isURI()) return quad;
		String graph = quad.getGraph().getURI();
		String role = names.product().equals(graph) ? "product"
				: names.property().equals(graph) ? "property" : null;
		return role == null ? quad : new Quad(NodeFactory.createURI("urn:ifctolbd:graph-role:" + role),
				quad.getSubject(), quad.getPredicate(), quad.getObject());
	}

	private static List<Quad> difference(Set<Quad> left, Set<Quad> right) {
		List<Quad> result = new ArrayList<>(left);
		result.removeAll(right);
		result.sort((a, b) -> a.toString().compareTo(b.toString()));
		return result;
	}

	private static void addChanges(Model model, Resource comparison, List<Quad> quads, String type) {
		for (int index = 0; index < quads.size(); index++) {
			Quad quad = quads.get(index);
			Resource change = model.createResource(comparison.getURI() + "/" + type.toLowerCase() + "/" + index)
					.addProperty(RDF.type, model.createResource(NS + type))
					.addProperty(RDF.subject, asRdfNode(model, quad.getSubject()))
					.addProperty(RDF.predicate, asRdfNode(model, quad.getPredicate()))
					.addProperty(RDF.object, asRdfNode(model, quad.getObject()));
			if (!quad.isDefaultGraph()) change.addProperty(model.createProperty(NS + "graph"),
					asRdfNode(model, quad.getGraph()));
			comparison.addProperty(model.createProperty(NS + "change"), change);
		}
	}

	private static RDFNode asRdfNode(Model model, Node node) { return model.asRDFNode(node); }

	private static Metadata metadata(ConversionResult result) {
		Model manifest = result.getManifestModel();
		return new Metadata(value(manifest, "cacheKey"), value(manifest, "sourceChecksum"),
				value(manifest, "profile"), value(manifest, "uriPolicy"),
				value(manifest, "uriPolicyConfiguration"), value(manifest, "modelScope"), moduleSignature(manifest));
	}

	private static String value(Model manifest, String localName) {
		var statements = manifest.listStatements(null, manifest.createProperty(ConversionManifest.NS + localName),
				(RDFNode) null).toList();
		if (statements.size() != 1 || !statements.get(0).getObject().isLiteral())
			throw new IllegalArgumentException("Conversion manifest must contain exactly one " + localName);
		return statements.get(0).getString();
	}

	private static String moduleSignature(Model manifest) {
		return manifest.listStatements(null, manifest.createProperty(ConversionManifest.NS + "usesModule"),
				(RDFNode) null).toList().stream().map(statement -> statement.getResource().getURI()).sorted()
				.reduce("", (a, b) -> a + "|" + b);
	}

	private static void requireCompatible(Metadata previous, Metadata current) {
		if (!"stable-guid-v1".equals(previous.uriPolicy) || !previous.uriPolicy.equals(current.uriPolicy))
			throw new IllegalArgumentException("Revision comparison requires matching stable-guid-v1 URI policies");
		if (!previous.modelScope.equals(current.modelScope))
			throw new IllegalArgumentException("Revision comparison requires matching model scopes");
		if (!previous.uriPolicyConfiguration.equals(current.uriPolicyConfiguration))
			throw new IllegalArgumentException("Revision comparison requires matching URI policy configurations");
		if (!previous.profile.equals(current.profile) || !previous.modules.equals(current.modules))
			throw new IllegalArgumentException("Revision comparison requires matching profiles and module versions");
	}

	private record Metadata(String cacheKey, String sourceChecksum, String profile, String uriPolicy,
			String uriPolicyConfiguration, String modelScope, String modules) { }
}
