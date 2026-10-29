package org.linkedbuildingdata.ifc2lbd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.namespace.BMO;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.BSDD;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

/**
 * Deterministic, read-only retrieval over converted LBD data and its companion
 * external-link and provenance graphs.
 *
 * <p>This service deliberately does not interpret natural language or execute
 * caller-supplied SPARQL. It provides a bounded contract that CLI, HTTP and MCP
 * adapters can share.</p>
 */
public final class GraphRetrievalService {
	public static final int MAX_LIMIT = 1000;
	public static final int MAX_OFFSET = 100_000;
	private static final String PROV = "http://www.w3.org/ns/prov#";
	private static final String EVIDENCE = "https://w3id.org/ifctolbd/evidence#";
	private static final String QUDT = "http://qudt.org/schema/qudt/";
	private static final String SCHEMA = "http://schema.org/";
	private static final String BEO = "https://pi.pauwel.be/voc/buildingelement#";
	private static final String MEP = "https://pi.pauwel.be/voc/distributionelement#";
	private static final String PRODUCT = "https://w3id.org/product#";
	private static final String EXTERNAL_LINK = "https://w3id.org/ifctolbd/external-link#";
	private static final Model EMPTY_COMPANION = ModelFactory.createDefaultModel();

	private static final Set<Property> SPATIAL_EDGES = Set.of(BOT.hasBuilding, BOT.hasStorey, BOT.hasSpace,
			BOT.containsZone, BOT.containsElement, BOT.adjacentElement, BOT.hasSubElement);
	private static final Set<String> MATERIAL_PREDICATES = Set.of(
			IFCtoLBDMapping.materialAssignment.getURI(), IFCtoLBDMapping.assignedMaterialDefinition.getURI(),
			IFCtoLBDMapping.assignedMaterial.getURI(), IFCtoLBDMapping.layerSetUsage.getURI(),
			IFCtoLBDMapping.layerMembership.getURI(), IFCtoLBDMapping.layer.getURI(),
			IFCtoLBDMapping.thicknessQuantity.getURI(), BMO.hasMaterial.getURI(), BMO.hasLayers.getURI(),
			BMO.hasThickness.getURI(), BMO.hasMaterialName.getURI());

	public enum Status { FOUND, EMPTY, NOT_FOUND, AMBIGUOUS, UNSUPPORTED, ERROR }
	public enum QueryKind { FIND_ELEMENTS, INSPECT_ELEMENT, LINKED_RECORDS }

	/** All non-null criteria are combined with logical AND. */
	public record ElementQuery(String identifier, String type, String spatialLocation) {
		public ElementQuery {
			identifier = clean(identifier); type = clean(type); spatialLocation = clean(spatialLocation);
		}
		public boolean isEmpty() { return identifier == null && type == null && spatialLocation == null; }
	}

	public record Value(String lexicalForm, String kind, String datatype, String language) { }
	public record GraphStatement(String subject, String predicate, Value object, String unit, String graphRole) { }
	public record Provenance(String scope, GraphStatement statement) { }
	public record Entity(String identifier, List<String> types, List<String> labels,
			List<GraphStatement> properties, List<GraphStatement> materials,
			List<GraphStatement> relationships, List<GraphStatement> linkedRecords) { }
	public record Result(Status status, QueryKind queryKind, List<Entity> entities,
			List<GraphStatement> supportingStatements, List<Provenance> provenance,
			int offset, int limit, long totalAvailable, boolean truncated, String message) {
		static Result problem(Status status, QueryKind kind, int offset, int limit, String message) {
			return new Result(status, kind, List.of(), List.of(), List.of(), offset, limit, 0, false, message);
		}
	}

	private final Model data;
	private final Model externalLinks;
	private final Model provenance;
	private final Model externalLinkReport;

	public GraphRetrievalService(Model data) {
		this(data, EMPTY_COMPANION, EMPTY_COMPANION, EMPTY_COMPANION);
	}

	public GraphRetrievalService(Model data, Model externalLinks, Model provenance) {
		this(data, externalLinks, provenance, EMPTY_COMPANION);
	}

	public GraphRetrievalService(Model data, Model externalLinks, Model provenance, Model externalLinkReport) {
		this.data = Objects.requireNonNull(data, "data");
		this.externalLinks = Objects.requireNonNull(externalLinks, "externalLinks");
		this.provenance = Objects.requireNonNull(provenance, "provenance");
		this.externalLinkReport = Objects.requireNonNull(externalLinkReport, "externalLinkReport");
	}

	public Result findElements(ElementQuery query, int offset, int limit) {
		QueryKind kind = QueryKind.FIND_ELEMENTS;
		try {
			checkPage(offset, limit);
			if (query == null || query.isEmpty())
				return Result.problem(Status.UNSUPPORTED, kind, offset, limit,
						"At least one identifier, type, or spatialLocation criterion is required");

			Set<Resource> candidates = elementCandidates();
			if (query.identifier() != null) {
				List<Resource> identified = resolve(query.identifier());
				if (identified.isEmpty()) return Result.problem(Status.NOT_FOUND, kind, offset, limit,
						"No entity has the requested identifier");
				if (identified.size() > 1) return Result.problem(Status.AMBIGUOUS, kind, offset, limit,
						"The identifier resolves to more than one entity");
				candidates.retainAll(identified);
			}
			if (query.type() != null) candidates.removeIf(resource -> !hasType(resource, query.type()));
			if (query.spatialLocation() != null) {
				List<Resource> locations = resolve(query.spatialLocation());
				if (locations.isEmpty()) return Result.problem(Status.NOT_FOUND, kind, offset, limit,
						"No spatial resource has the requested identifier");
				if (locations.size() > 1) return Result.problem(Status.AMBIGUOUS, kind, offset, limit,
						"The spatial identifier resolves to more than one entity");
				candidates.retainAll(elementsBelow(locations.get(0)));
			}

			List<Resource> ordered = candidates.stream().filter(Resource::isURIResource)
					.sorted(Comparator.comparing(Resource::getURI)).toList();
			long total = ordered.size();
			List<Entity> page = ordered.stream().skip(offset).limit(limit).map(this::summary).toList();
			List<GraphStatement> support = page.stream().flatMap(entity -> entity.relationships().stream()).toList();
			return new Result(page.isEmpty() ? Status.EMPTY : Status.FOUND, kind, page, support,
					conversionProvenance(limit), offset, limit, total, offset + page.size() < total,
					page.isEmpty() ? "The query executed successfully but matched no elements" : null);
		} catch (IllegalArgumentException e) {
			return Result.problem(Status.UNSUPPORTED, kind, offset, limit, e.getMessage());
		} catch (RuntimeException e) {
			return Result.problem(Status.ERROR, kind, offset, limit, safeMessage(e));
		}
	}

	public Result inspectElement(String identifier, int statementLimit) {
		QueryKind kind = QueryKind.INSPECT_ELEMENT;
		try {
			checkPage(0, statementLimit);
			List<Resource> matches = resolveRequired(identifier);
			if (matches.isEmpty()) return Result.problem(Status.NOT_FOUND, kind, 0, statementLimit,
					"No entity has the requested identifier");
			if (matches.size() > 1) return Result.problem(Status.AMBIGUOUS, kind, 0, statementLimit,
					"The identifier resolves to more than one entity");
			Resource element = matches.get(0);
			Traversal traversal = describe(element, statementLimit);
			Entity entity = new Entity(id(element), types(element), labels(element), traversal.properties(),
					traversal.materials(), traversal.relationships(), linkStatements(element, statementLimit));
			List<GraphStatement> supporting = new ArrayList<>();
			supporting.addAll(traversal.properties()); supporting.addAll(traversal.materials());
			supporting.addAll(traversal.relationships()); supporting.addAll(entity.linkedRecords());
			if (supporting.size() > statementLimit) supporting = supporting.subList(0, statementLimit);
			List<Provenance> evidence = entityProvenance(element, traversal.visited(), statementLimit);
			return new Result(Status.FOUND, kind, List.of(entity), List.copyOf(supporting), evidence, 0,
					statementLimit, traversal.available() + entity.linkedRecords().size(), traversal.truncated(), null);
		} catch (IllegalArgumentException e) {
			return Result.problem(Status.UNSUPPORTED, kind, 0, statementLimit, e.getMessage());
		} catch (RuntimeException e) {
			return Result.problem(Status.ERROR, kind, 0, statementLimit, safeMessage(e));
		}
	}

	public Result linkedRecords(String identifier, int offset, int limit) {
		QueryKind kind = QueryKind.LINKED_RECORDS;
		try {
			checkPage(offset, limit);
			List<Resource> matches = resolveRequired(identifier);
			if (matches.isEmpty()) return Result.problem(Status.NOT_FOUND, kind, offset, limit,
					"No entity has the requested identifier");
			if (matches.size() > 1) return Result.problem(Status.AMBIGUOUS, kind, offset, limit,
					"The identifier resolves to more than one entity");
			Resource element = matches.get(0);
			List<GraphStatement> all = linkStatements(element, MAX_LIMIT).stream()
					.sorted(Comparator.comparing(GraphStatement::predicate)
							.thenComparing(statement -> statement.object().lexicalForm())).toList();
			List<GraphStatement> page = all.stream().skip(offset).limit(limit).toList();
			Entity entity = new Entity(id(element), types(element), labels(element), List.of(), List.of(), List.of(), page);
			return new Result(page.isEmpty() ? Status.EMPTY : Status.FOUND, kind, List.of(entity), page,
					entityProvenance(element, Set.of(element), limit), offset, limit, all.size(),
					offset + page.size() < all.size(), page.isEmpty() ? "The element has no linked external records" : null);
		} catch (IllegalArgumentException e) {
			return Result.problem(Status.UNSUPPORTED, kind, offset, limit, e.getMessage());
		} catch (RuntimeException e) {
			return Result.problem(Status.ERROR, kind, offset, limit, safeMessage(e));
		}
	}

	private Entity summary(Resource resource) {
		List<GraphStatement> relationships = data.listStatements(null, null, resource).toList().stream()
				.filter(statement -> statement.getPredicate().getURI().startsWith(BOT.ns))
				.limit(10).map(statement -> statement(statement, "data")).toList();
		return new Entity(id(resource), types(resource), labels(resource), List.of(), List.of(), relationships,
				linkStatements(resource, 10));
	}

	private Traversal describe(Resource root, int limit) {
		List<GraphStatement> properties = new ArrayList<>(), materials = new ArrayList<>(), relationships = new ArrayList<>();
		Set<Resource> visited = new LinkedHashSet<>();
		ArrayDeque<NodeDepth> pending = new ArrayDeque<>(); pending.add(new NodeDepth(root, 0, Category.ROOT));
		long available = 0; boolean truncated = false;
		while (!pending.isEmpty()) {
			NodeDepth current = pending.removeFirst();
			if (!visited.add(current.resource())) continue;
			for (Statement value : data.listStatements(current.resource(), null, (RDFNode) null).toList()) {
				available++;
				Category category = category(value, current.category());
				if (properties.size() + materials.size() + relationships.size() >= limit) { truncated = true; continue; }
				GraphStatement converted = statement(value, "data");
				switch (category) {
				case PROPERTY -> properties.add(converted);
				case MATERIAL -> materials.add(converted);
				case RELATIONSHIP, ROOT -> relationships.add(converted);
				}
				if (value.getObject().isResource() && current.depth() < 3
						&& (category == Category.PROPERTY || category == Category.MATERIAL))
					pending.addLast(new NodeDepth(value.getResource(), current.depth() + 1, category));
			}
		}
		return new Traversal(List.copyOf(properties), List.copyOf(materials), List.copyOf(relationships),
				Set.copyOf(visited), available, truncated);
	}

	private Category category(Statement statement, Category inherited) {
		String predicate = statement.getPredicate().getURI();
		if (inherited == Category.MATERIAL || predicate.startsWith(BMO.NS) || MATERIAL_PREDICATES.contains(predicate))
			return Category.MATERIAL;
		if (inherited == Category.PROPERTY || predicate.startsWith(PROPS.ns) || predicate.startsWith(OPM.ns)
				|| predicate.startsWith(BSDD.meta_ns) || predicate.startsWith(QUDT) || predicate.startsWith(EVIDENCE)
				|| predicate.equals(SCHEMA + "value")) return Category.PROPERTY;
		return Category.RELATIONSHIP;
	}

	private Set<Resource> elementCandidates() {
		Set<Resource> result = new HashSet<>();
		result.addAll(data.listResourcesWithProperty(RDF.type, BOT.element).toList());
		for (Property property : List.of(BOT.containsElement, BOT.adjacentElement, BOT.hasSubElement))
			data.listObjectsOfProperty(property).toList().stream().filter(RDFNode::isURIResource)
					.map(RDFNode::asResource).forEach(result::add);
		data.listStatements(null, RDF.type, (RDFNode) null).toList().stream()
				.filter(statement -> statement.getObject().isURIResource())
				.filter(statement -> isProductType(statement.getResource().getURI()))
				.map(Statement::getSubject).filter(Resource::isURIResource).forEach(result::add);
		return result;
	}

	private Set<Resource> elementsBelow(Resource location) {
		Set<Resource> elements = new HashSet<>(), visited = new HashSet<>();
		ArrayDeque<Resource> pending = new ArrayDeque<>(); pending.add(location);
		while (!pending.isEmpty()) {
			Resource current = pending.removeFirst();
			if (!visited.add(current)) continue;
			for (Statement statement : data.listStatements(current, null, (RDFNode) null).toList()) {
				if (!SPATIAL_EDGES.contains(statement.getPredicate()) || !statement.getObject().isURIResource()) continue;
				Resource object = statement.getResource();
				if (isElement(object)) elements.add(object);
				pending.addLast(object);
			}
		}
		return elements;
	}

	private boolean isElement(Resource resource) {
		if (data.contains(resource, RDF.type, BOT.element)) return true;
		return data.listObjectsOfProperty(resource, RDF.type).toList().stream()
				.filter(RDFNode::isURIResource).anyMatch(node -> isProductType(node.asResource().getURI()));
	}

	private boolean hasType(Resource resource, String requested) {
		return data.listObjectsOfProperty(resource, RDF.type).toList().stream().filter(RDFNode::isURIResource)
				.map(node -> node.asResource().getURI()).anyMatch(uri -> uri.equals(requested)
						|| localName(uri).equalsIgnoreCase(requested));
	}

	private List<Resource> resolveRequired(String identifier) {
		if (clean(identifier) == null) throw new IllegalArgumentException("identifier is required");
		return resolve(identifier);
	}

	private List<Resource> resolve(String identifier) {
		String wanted = clean(identifier);
		if (wanted == null) return List.of();
		Set<Resource> matches = new LinkedHashSet<>();
		if (absoluteIri(wanted)) {
			Resource resource = data.createResource(wanted);
			if (data.containsResource(resource) || externalLinks.containsResource(resource)
					|| provenance.containsResource(resource)) matches.add(resource);
		}
		for (Model model : List.of(data, provenance))
			model.listSubjectsWithProperty(IFCtoLBDMapping.ifcGlobalId, wanted).toList().stream()
					.filter(Resource::isURIResource).forEach(resource -> matches.add(data.createResource(resource.getURI())));
		return matches.stream().sorted(Comparator.comparing(Resource::getURI)).toList();
	}

	private List<GraphStatement> linkStatements(Resource element, int limit) {
		return externalLinks.listStatements(externalLinks.createResource(id(element)), null, (RDFNode) null).toList().stream()
				.limit(limit).map(value -> statement(value, "external-links")).toList();
	}

	private List<Provenance> entityProvenance(Resource element, Set<Resource> visited, int limit) {
		List<Provenance> result = new ArrayList<>();
		Set<String> subjects = new HashSet<>(); subjects.add(id(element));
		visited.stream().filter(Resource::isURIResource).map(Resource::getURI).forEach(subjects::add);
		for (String subject : subjects) {
			for (Statement value : provenance.listStatements(provenance.createResource(subject), null, (RDFNode) null).toList()) {
				if (result.size() >= limit) return List.copyOf(result);
				result.add(new Provenance(subject.equals(id(element)) ? "element" : "property", statement(value, "provenance")));
			}
			for (Statement value : data.listStatements(data.createResource(subject), null, (RDFNode) null).toList()) {
				String predicate = value.getPredicate().getURI();
				if (!predicate.startsWith(PROV) && !predicate.startsWith(EVIDENCE)) continue;
				if (result.size() >= limit) return List.copyOf(result);
				result.add(new Provenance(subject.equals(id(element)) ? "element" : "property", statement(value, "data")));
			}
		}
		for (Resource row : externalLinkReport.listSubjectsWithProperty(
				externalLinkReport.createProperty(EXTERNAL_LINK + "resolvedElement"),
				externalLinkReport.createResource(id(element))).toList()) {
			for (Statement value : externalLinkReport.listStatements(row, null, (RDFNode) null).toList()) {
				if (result.size() >= limit) return List.copyOf(result);
				result.add(new Provenance("external-link", statement(value, "external-link-report")));
			}
		}
		if (result.isEmpty()) result.addAll(conversionProvenance(limit));
		return List.copyOf(result);
	}

	private List<Provenance> conversionProvenance(int limit) {
		return provenance.listStatements().toList().stream()
				.filter(value -> value.getSubject().getURI() != null && value.getSubject().getURI().startsWith("urn:ifctolbd:conversion:"))
				.limit(limit).map(value -> new Provenance("conversion", statement(value, "provenance"))).toList();
	}

	private GraphStatement statement(Statement statement, String graphRole) {
		RDFNode object = statement.getObject();
		Value value;
		if (object.isLiteral()) {
			Literal literal = object.asLiteral();
			value = new Value(literal.getLexicalForm(), "literal", empty(literal.getDatatypeURI()), empty(literal.getLanguage()));
		} else if (object.isAnon()) value = new Value(object.asResource().getId().getLabelString(), "blank-node", null, null);
		else value = new Value(object.asResource().getURI(), "iri", null, null);
		return new GraphStatement(id(statement.getSubject()), statement.getPredicate().getURI(), value,
				unit(statement.getSubject()).orElse(null), graphRole);
	}

	private Optional<String> unit(Resource owner) {
		for (String predicate : List.of(QUDT + "unit", "https://w3id.org/ifc2lbd/unit#resolvedUnit")) {
			RDFNode value = data.getProperty(owner, data.createProperty(predicate)) == null ? null
					: data.getProperty(owner, data.createProperty(predicate)).getObject();
			if (value != null) return Optional.of(value.isURIResource() ? value.asResource().getURI() : value.toString());
		}
		return Optional.empty();
	}

	private List<String> types(Resource resource) {
		return data.listObjectsOfProperty(resource, RDF.type).toList().stream().filter(RDFNode::isURIResource)
				.map(node -> node.asResource().getURI()).distinct().sorted().toList();
	}

	private List<String> labels(Resource resource) {
		return data.listObjectsOfProperty(resource, RDFS.label).toList().stream().filter(RDFNode::isLiteral)
				.map(node -> node.asLiteral().getString()).distinct().sorted().toList();
	}

	private static void checkPage(int offset, int limit) {
		if (offset < 0 || offset > MAX_OFFSET) throw new IllegalArgumentException("offset must be between 0 and " + MAX_OFFSET);
		if (limit < 1 || limit > MAX_LIMIT) throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
	}

	private static boolean isProductType(String uri) {
		return uri != null && (uri.startsWith(BEO) || uri.startsWith(MEP) || uri.startsWith(PRODUCT));
	}

	private static String id(Resource resource) {
		return resource.isURIResource() ? resource.getURI() : "_:" + resource.getId().getLabelString();
	}

	private static String localName(String uri) {
		int index = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
		return index < 0 ? uri : uri.substring(index + 1);
	}

	private static boolean absoluteIri(String value) {
		try { return java.net.URI.create(value).isAbsolute(); } catch (IllegalArgumentException e) { return false; }
	}

	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String empty(String value) { return value == null || value.isEmpty() ? null : value; }
	private static String safeMessage(RuntimeException error) {
		return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage();
	}

	private enum Category { ROOT, PROPERTY, MATERIAL, RELATIONSHIP }
	private record NodeDepth(Resource resource, int depth, Category category) { }
	private record Traversal(List<GraphStatement> properties, List<GraphStatement> materials,
			List<GraphStatement> relationships, Set<Resource> visited, long available, boolean truncated) { }
}
