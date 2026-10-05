package org.linkedbuildingdata.ifc2lbd;

import java.util.Optional;

/** Default store: detailed geometry continues to be embedded in RDF. */
public final class NoGeometryArtifactStore implements GeometryArtifactStore {
	public static final NoGeometryArtifactStore INSTANCE = new NoGeometryArtifactStore();
	private NoGeometryArtifactStore() { }
	@Override public String id() { return "none"; }
	@Override public Optional<GeometryArtifact> store(byte[] content, String mediaType, String extension,
			String levelOfDetail, String coordinateReferenceSystem) { return Optional.empty(); }
}
