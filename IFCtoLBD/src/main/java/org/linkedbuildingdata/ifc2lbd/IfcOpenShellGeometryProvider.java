package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import be.ugent.IfcSpfReader;
import de.rwth_aachen.dc.lbd.IFCGeometry;

/** Adapter for the existing IfcOpenShell iterator/IfcGeomServer implementation. */
public final class IfcOpenShellGeometryProvider implements GeometryProvider {
	public static final IfcOpenShellGeometryProvider INSTANCE = new IfcOpenShellGeometryProvider();
	private IfcOpenShellGeometryProvider() { }
	@Override public String id() { return "ifcopenshell"; }
	@Override public String version() { return "legacy-adapter-v1"; }
	@Override public GeometryResult load(Path ifcFile) {
		Path geometryInput = ifcFile;
		Path temporarySpf = null;
		try {
			if (IfcSpfReader.isStructuredInput(ifcFile)) {
				temporarySpf = Files.createTempFile("ifctolbd-geometry-", ".ifc");
				IfcSpfReader.materializeSpf(ifcFile, temporarySpf);
				geometryInput = temporarySpf;
			}
			IFCGeometry geometry = new IFCGeometry(geometryInput.toFile());
			IFCGeometry.CoordinateMetadata source = geometry.getCoordinateMetadata();
			GeometryResult.Metadata metadata = new GeometryResult.Metadata(
					coordinateSpace(source.coordinateSpace()), null, source.lengthUnit(),
					source.unitToMetres(), source.objectPlacementsApplied(), source.mapConversionApplied(),
					java.util.List.of());
			return GeometryResult.of(geometry, temporarySpf, metadata);
		} catch (IOException e) {
			if (temporarySpf != null) try { Files.deleteIfExists(temporarySpf); } catch (IOException ignored) { }
			throw new UncheckedIOException("Could not prepare IFC input for IfcOpenShell: " + ifcFile, e);
		} catch (RuntimeException e) {
			if (temporarySpf != null) try { Files.deleteIfExists(temporarySpf); } catch (IOException ignored) { }
			throw e;
		}
	}

	private static GeometryResult.CoordinateSpace coordinateSpace(String value) {
		return switch (value) {
		case "projected" -> GeometryResult.CoordinateSpace.PROJECTED;
		case "render-rebased" -> GeometryResult.CoordinateSpace.RENDER_REBASED;
		default -> GeometryResult.CoordinateSpace.LOCAL_ENGINEERING;
		};
	}
}
