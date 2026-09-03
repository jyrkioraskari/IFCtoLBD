package org.linkedbuildingdata.ifc2lbd;

import java.util.Optional;

/** Storage boundary for detailed geometry kept outside the RDF graph. */
public interface GeometryArtifactStore extends AutoCloseable {
	String id();
	Optional<GeometryArtifact> store(byte[] content, String mediaType, String extension,
			String levelOfDetail, String coordinateReferenceSystem);
	@Override default void close() { }
}
