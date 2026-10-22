package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.rwth_aachen.dc.lbd.BoundingBox;
import de.rwth_aachen.dc.lbd.IFCGeometry;
import de.rwth_aachen.dc.lbd.MTLDescription;
import de.rwth_aachen.dc.lbd.ObjDescription;
import de.rwth_aachen.dc.lbd.TessellatedMesh;

/** Geometry loaded by a provider, including an explicit unavailable result. */
public final class GeometryResult implements AutoCloseable {
	public enum CoordinateSpace { LOCAL_ENGINEERING, PROJECTED, RENDER_REBASED }

	/** Contract for provider output. A finite unit factor is required before map conversion. */
	public record Metadata(CoordinateSpace coordinateSpace, String coordinateReferenceSystem, String lengthUnit, double unitToMetres,
			boolean objectPlacementsApplied, boolean mapConversionApplied, List<String> warnings) {
		public Metadata {
			coordinateSpace = coordinateSpace == null ? CoordinateSpace.LOCAL_ENGINEERING : coordinateSpace;
			warnings = List.copyOf(warnings == null ? List.of() : warnings);
		}
		public static Metadata unknownLocal() {
			return new Metadata(CoordinateSpace.LOCAL_ENGINEERING, null, null, Double.NaN, false, false,
					List.of("Geometry provider did not declare output units or placement semantics."));
		}
		public static Metadata ifcOpenShellWorldMetres() {
			return new Metadata(CoordinateSpace.LOCAL_ENGINEERING, null, "metre", 1.0, true, false, List.of());
		}
	}

	private final IFCGeometry geometry;
	private final Path temporaryInput;
	private final Metadata metadata;

	private GeometryResult(IFCGeometry geometry, Path temporaryInput, Metadata metadata) {
		this.geometry = geometry;
		this.temporaryInput = temporaryInput;
		this.metadata = metadata;
	}
	public static GeometryResult of(IFCGeometry geometry) {
		return new GeometryResult(java.util.Objects.requireNonNull(geometry, "geometry"), null, Metadata.unknownLocal());
	}
	public static GeometryResult of(IFCGeometry geometry, Metadata metadata) {
		return new GeometryResult(java.util.Objects.requireNonNull(geometry, "geometry"), null,
				java.util.Objects.requireNonNull(metadata, "metadata"));
	}
	static GeometryResult of(IFCGeometry geometry, Path temporaryInput, Metadata metadata) {
		return new GeometryResult(java.util.Objects.requireNonNull(geometry, "geometry"), temporaryInput,
				java.util.Objects.requireNonNull(metadata, "metadata"));
	}
	public static GeometryResult unavailable() { return new GeometryResult(null, null, Metadata.unknownLocal()); }
	public boolean isAvailable() { return geometry != null; }
	public Metadata metadata() { return metadata; }
	public BoundingBox getBoundingBox(String guid) { return geometry == null ? null : geometry.getBoundingBox(guid); }
	public ObjDescription getOBJ(String guid) { return geometry == null ? null : geometry.getOBJ(guid); }
	public MTLDescription getMTL(String guid) { return geometry == null ? null : geometry.getMTL(guid); }
	public String getWireframeWKT(String guid) { return geometry == null ? null : geometry.getWireframeWKT(guid); }
	/** Returns backend-neutral triangle data for the IFC product, when available. */
	public TessellatedMesh getTessellatedMesh(String guid) {
		return geometry == null ? null : geometry.getTessellatedMesh(guid);
	}
	IFCGeometry unwrap() { return geometry; }
	@Override public void close() {
		try {
			if (geometry != null) geometry.close();
		} finally {
			if (temporaryInput != null) try {
				Files.deleteIfExists(temporaryInput);
			} catch (IOException e) {
				throw new UncheckedIOException("Could not delete temporary geometry input " + temporaryInput, e);
			}
		}
	}
}
