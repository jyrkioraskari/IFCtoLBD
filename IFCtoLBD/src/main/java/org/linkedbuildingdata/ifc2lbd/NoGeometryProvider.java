package org.linkedbuildingdata.ifc2lbd;

import java.nio.file.Path;

/** Deterministic provider for tests and deployments where geometry is disabled. */
public final class NoGeometryProvider implements GeometryProvider {
	public static final NoGeometryProvider INSTANCE = new NoGeometryProvider();
	private NoGeometryProvider() { }
	@Override public String id() { return "none"; }
	@Override public String version() { return "1"; }
	@Override public GeometryResult load(Path ifcFile) { return GeometryResult.unavailable(); }
}
