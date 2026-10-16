package org.linkedbuildingdata.ifc2lbd;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;

/** Small, strongly typed boundary used by the Node MCP adapter. */
public final class McpConversionBridge implements AutoCloseable {
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
		ConversionRequest request = new ConversionRequest(ifcFile, profile(profileId));
		if (modelScope != null && !modelScope.isBlank()) request = request.withModelScope(modelScope);
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

	private static ConversionProfile profile(String id) {
		return ConversionProfiles.named(id == null ? "properties-simple" : id);
	}

	private static ValidationShapePack validationPack(String id) {
		for (ValidationShapePack pack : ValidationShapePack.values()) if (pack.id().equals(id)) return pack;
		throw new IllegalArgumentException("Unknown validation shape pack: " + id);
	}

	@Override public void close() { converter.close(); }
}
