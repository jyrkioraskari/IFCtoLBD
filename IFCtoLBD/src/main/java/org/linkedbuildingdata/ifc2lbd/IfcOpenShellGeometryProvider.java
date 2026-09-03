package org.linkedbuildingdata.ifc2lbd;

import java.nio.file.Path;

import de.rwth_aachen.dc.lbd.IFCGeometry;

/** Adapter for the existing IfcOpenShell iterator/IfcGeomServer implementation. */
public final class IfcOpenShellGeometryProvider implements GeometryProvider {
	public static final IfcOpenShellGeometryProvider INSTANCE = new IfcOpenShellGeometryProvider();
	private IfcOpenShellGeometryProvider() { }
	@Override public String id() { return "ifcopenshell"; }
	@Override public String version() { return "legacy-adapter-v1"; }
	@Override public GeometryResult load(Path ifcFile) { return GeometryResult.of(new IFCGeometry(ifcFile.toFile())); }
}
