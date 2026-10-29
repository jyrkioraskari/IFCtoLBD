package org.linkedbuildingdata.ifc2lbd;

import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.linkedbuildingdata.ifc2lbd.core.utils.JsonLdExport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Small, strongly typed boundary used by the Node MCP adapter. */
public final class McpConversionBridge implements AutoCloseable {
	private static final ObjectMapper JSON = new ObjectMapper();
	private final ConversionSession session;
	private final IFCtoLBDConverter converter;

	public McpConversionBridge(String baseUri, boolean propertiesBlankNodes, int propertyLevel,
			String artifactDirectory, String artifactBaseUri) {
		GeometryArtifactStore artifacts = artifactDirectory == null || artifactDirectory.isBlank()
				? NoGeometryArtifactStore.INSTANCE
				: new FileSystemGeometryArtifactStore(Path.of(artifactDirectory), URI.create(artifactBaseUri));
		this.session = new ConversionSession(artifacts);
		this.converter = new IFCtoLBDConverter(session, baseUri, propertiesBlankNodes, propertyLevel);
	}

	/** Reads the version from the converter JAR manifest, never from MCP configuration. */
	public static String converterVersion() {
		String version = IFCtoLBDConverter.class.getPackage().getImplementationVersion();
		if (version == null || version.isBlank())
			throw new IllegalStateException("IFCtoLBD JAR has no Implementation-Version manifest entry");
		return version;
	}

	public ConversionResult convert(String ifcFile, String profileId, String modelScope,
			String[] validationPackIds) {
		return convert(ifcFile, profileId, modelScope, validationPackIds, null, null, null);
	}

	/** Additive integration-aware conversion entry point; the historical overload remains unchanged. */
	public ConversionResult convert(String ifcFile, String profileId, String modelScope,
			String[] validationPackIds, String identifierPolicy, String projectNamespace, String[] mappingFiles) {
		return convert(ifcFile, profileId, modelScope, validationPackIds, identifierPolicy, projectNamespace,
				mappingFiles, null);
	}

	/** Additive geometry-format entry point; older MCP adapters retain OBJ defaults. */
	public ConversionResult convert(String ifcFile, String profileId, String modelScope,
			String[] validationPackIds, String identifierPolicy, String projectNamespace, String[] mappingFiles,
			String[] geometryFormats) {
		ConversionRequest request = new ConversionRequest(ifcFile, profile(profileId));
		if (modelScope != null && !modelScope.isBlank()) request = request.withModelScope(modelScope);
		if (identifierPolicy != null && IdentifierPolicy.STABLE_PROJECT.name().equalsIgnoreCase(identifierPolicy))
			request = request.withStableProjectIdentity(projectNamespace);
		else if (projectNamespace != null && !projectNamespace.isBlank())
			throw new IllegalArgumentException("projectNamespace requires STABLE_PROJECT identifierPolicy");
		if (mappingFiles != null && mappingFiles.length > 0) {
			Path[] paths = new Path[mappingFiles.length];
			for (int i = 0; i < mappingFiles.length; i++) paths[i] = Path.of(mappingFiles[i]);
			request = request.withExternalLinkMappings(paths);
		}
		if (geometryFormats != null && geometryFormats.length > 0) {
			GeometryArtifactFormat[] formats = new GeometryArtifactFormat[geometryFormats.length];
			for (int i = 0; i < geometryFormats.length; i++)
				formats[i] = GeometryArtifactFormat.valueOf(geometryFormats[i].toUpperCase(java.util.Locale.ROOT));
			request = request.withGeometryArtifactFormats(formats);
		}
		if (validationPackIds != null && validationPackIds.length > 0) {
			List<ValidationShapePack> packs = new ArrayList<>();
			for (String id : validationPackIds) packs.add(validationPack(id));
			request = request.withValidation(packs.toArray(ValidationShapePack[]::new));
		}
		return converter.convert(request);
	}

	public ConversionDiff compare(ConversionResult previous, ConversionResult current) {
		return new RevisionComparator().compare(previous, current);
	}

	/** Combined query view; provenance and validation remain separate resources. */
	public Model dataModel(ConversionResult result) {
		return result.getModel();
	}

	/** Shared deterministic retrieval used by thin MCP and HTTP adapters. */
	public String findElementsJson(Model data, Model externalLinks, Model provenance, String identifier,
			String type, String spatialLocation, int offset, int limit) {
		return json(new GraphRetrievalService(data, externalLinks, provenance).findElements(
				new GraphRetrievalService.ElementQuery(identifier, type, spatialLocation), offset, limit));
	}

	public String inspectElementJson(Model data, Model externalLinks, Model provenance, String identifier,
			int statementLimit) {
		return json(new GraphRetrievalService(data, externalLinks, provenance).inspectElement(identifier, statementLimit));
	}

	public String linkedRecordsJson(Model data, Model externalLinks, Model provenance, String identifier,
			int offset, int limit) {
		return json(new GraphRetrievalService(data, externalLinks, provenance).linkedRecords(identifier, offset, limit));
	}

	public String findElementsWithReportJson(Model data, Model externalLinks, Model provenance, Model linkReport,
			String identifier, String type, String spatialLocation, int offset, int limit) {
		return json(new GraphRetrievalService(data, externalLinks, provenance, linkReport).findElements(
				new GraphRetrievalService.ElementQuery(identifier, type, spatialLocation), offset, limit));
	}

	public String inspectElementWithReportJson(Model data, Model externalLinks, Model provenance, Model linkReport,
			String identifier, int statementLimit) {
		return json(new GraphRetrievalService(data, externalLinks, provenance, linkReport)
				.inspectElement(identifier, statementLimit));
	}

	public String linkedRecordsWithReportJson(Model data, Model externalLinks, Model provenance, Model linkReport,
			String identifier, int offset, int limit) {
		return json(new GraphRetrievalService(data, externalLinks, provenance, linkReport)
				.linkedRecords(identifier, offset, limit));
	}

	public GraphValidationService.Report validateModel(Model data, String[] shapePackIds) {
		return GraphValidationService.validate(data, shapePackIds == null ? new String[0] : shapePackIds);
	}

	/** Validate a non-mutating union of converted data and optional companion graphs. */
	public GraphValidationService.Report validateGraphs(Model data, Model provenance, Model externalLinkReport,
			String[] shapePackIds) {
		Model union = ModelFactory.createDefaultModel();
		try {
			union.add(data);
			if (provenance != null) union.add(provenance);
			if (externalLinkReport != null) union.add(externalLinkReport);
			return GraphValidationService.validate(union, shapePackIds == null ? new String[0] : shapePackIds);
		} finally {
			union.close();
		}
	}

	private static String json(Object value) {
		try { return JSON.writeValueAsString(value); }
		catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize structured graph result", e); }
	}

	/** Builds a bounded outgoing description and serializes it with the bundled offline JSON-LD context. */
	public JsonLdDescription describeElement(Model source, String elementUri, String[] predicateFilters,
			int depth, int limit) {
		if (source == null) throw new IllegalArgumentException("source model is required");
		if (elementUri == null || elementUri.isBlank()) throw new IllegalArgumentException("element IRI is required");
		if (depth < 0 || depth > 3) throw new IllegalArgumentException("depth must be between 0 and 3");
		if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
		Set<String> filters = new HashSet<>();
		if (predicateFilters != null) for (String filter : predicateFilters)
			if (filter != null && !filter.isBlank()) filters.add(filter);

		Resource root = source.createResource(elementUri);
		boolean found = source.listStatements(root, null, (RDFNode) null).hasNext();
		if (!found) return new JsonLdDescription(false, 0, false, "");
		Model description = ModelFactory.createDefaultModel();
		description.setNsPrefixes(source.getNsPrefixMap());
		ArrayDeque<ResourceDepth> pending = new ArrayDeque<>();
		pending.add(new ResourceDepth(root, depth));
		Set<Resource> visited = new HashSet<>();
		boolean truncated = false;
		try {
			while (!pending.isEmpty() && !truncated) {
				ResourceDepth current = pending.removeFirst();
				if (!visited.add(current.resource())) continue;
				StmtIterator statements = source.listStatements(current.resource(), null, (RDFNode) null);
				try {
					while (statements.hasNext()) {
						Statement statement = statements.nextStatement();
						if (!filters.isEmpty() && !filters.contains(statement.getPredicate().getURI())) continue;
						if (description.size() >= limit) { truncated = true; break; }
						description.add(statement);
						if (statement.getObject().isResource()) {
							Resource object = statement.getObject().asResource();
							if (object.isAnon() || current.depth() > 0)
								pending.addLast(new ResourceDepth(object, object.isAnon() ? current.depth() : current.depth() - 1));
						}
					}
				} finally {
					statements.close();
				}
			}
			try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
				JsonLdExport.write(output, description, JsonLdExport.Mode.COMPACT);
				return new JsonLdDescription(true, (int) description.size(), truncated, output.toString(java.nio.charset.StandardCharsets.UTF_8));
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not compact JSON-LD description", e);
		} finally {
			description.close();
		}
	}

	public static final class JsonLdDescription {
		private final boolean found;
		private final int returnedStatementCount;
		private final boolean truncated;
		private final String jsonLd;
		JsonLdDescription(boolean found, int returnedStatementCount, boolean truncated, String jsonLd) {
			this.found = found; this.returnedStatementCount = returnedStatementCount;
			this.truncated = truncated; this.jsonLd = jsonLd;
		}
		public boolean isFound() { return found; }
		public int getReturnedStatementCount() { return returnedStatementCount; }
		public boolean isTruncated() { return truncated; }
		public String getJsonLd() { return jsonLd; }
	}

	private record ResourceDepth(Resource resource, int depth) { }

	private static ConversionProfile profile(String id) {
		return ConversionProfiles.named(id == null ? "properties-simple" : id);
	}

	private static ValidationShapePack validationPack(String id) {
		for (ValidationShapePack pack : ValidationShapePack.values()) if (pack.id().equals(id)) return pack;
		throw new IllegalArgumentException("Unknown validation shape pack: " + id);
	}

	@Override public void close() { converter.close(); }
}
