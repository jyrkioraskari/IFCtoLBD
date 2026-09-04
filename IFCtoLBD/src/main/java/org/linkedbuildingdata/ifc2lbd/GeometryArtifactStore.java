package org.linkedbuildingdata.ifc2lbd;

import java.util.Optional;

/** Storage boundary for detailed geometry kept outside the RDF graph. */
public interface GeometryArtifactStore extends AutoCloseable {
	String id();
	default String version() { return "1"; }
	default String configurationId() { return id() + "@" + version(); }
	Optional<GeometryArtifact> store(byte[] content, String mediaType, String extension,
			String levelOfDetail, String coordinateReferenceSystem);
	@Override default void close() { }
}
