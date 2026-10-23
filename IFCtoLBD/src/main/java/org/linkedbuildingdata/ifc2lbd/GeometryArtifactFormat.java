package org.linkedbuildingdata.ifc2lbd;

/** Detailed mesh formats supported by the optional external-artifact workflow. */
public enum GeometryArtifactFormat {
	OBJ("model/obj", "obj"),
	GLB("model/gltf-binary", "glb"),
	GLTF("model/gltf+json", "gltf");

	private final String mediaType;
	private final String extension;

	GeometryArtifactFormat(String mediaType, String extension) {
		this.mediaType = mediaType;
		this.extension = extension;
	}

	public String mediaType() { return mediaType; }
	public String extension() { return extension; }
}
