package org.linkedbuildingdata.ifc2lbd;

import java.net.URI;

/** Metadata for one content-addressed detailed-geometry artifact. */
public record GeometryArtifact(URI uri, String sha256, String mediaType, String levelOfDetail,
		String coordinateReferenceSystem) { }
