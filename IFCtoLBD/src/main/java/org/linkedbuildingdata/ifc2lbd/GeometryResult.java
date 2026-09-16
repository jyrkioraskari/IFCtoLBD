package org.linkedbuildingdata.ifc2lbd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.rwth_aachen.dc.lbd.BoundingBox;
import de.rwth_aachen.dc.lbd.IFCGeometry;
import de.rwth_aachen.dc.lbd.MTLDescription;
import de.rwth_aachen.dc.lbd.ObjDescription;

/** Geometry loaded by a provider, including an explicit unavailable result. */
public final class GeometryResult implements AutoCloseable {
	private final IFCGeometry geometry;
	private final Path temporaryInput;

	private GeometryResult(IFCGeometry geometry, Path temporaryInput) {
		this.geometry = geometry;
		this.temporaryInput = temporaryInput;
	}
	public static GeometryResult of(IFCGeometry geometry) {
		return new GeometryResult(java.util.Objects.requireNonNull(geometry, "geometry"), null);
	}
	static GeometryResult of(IFCGeometry geometry, Path temporaryInput) {
		return new GeometryResult(java.util.Objects.requireNonNull(geometry, "geometry"), temporaryInput);
	}
	public static GeometryResult unavailable() { return new GeometryResult(null, null); }
	public boolean isAvailable() { return geometry != null; }
	public BoundingBox getBoundingBox(String guid) { return geometry == null ? null : geometry.getBoundingBox(guid); }
	public ObjDescription getOBJ(String guid) { return geometry == null ? null : geometry.getOBJ(guid); }
	public MTLDescription getMTL(String guid) { return geometry == null ? null : geometry.getMTL(guid); }
	public String getWireframeWKT(String guid) { return geometry == null ? null : geometry.getWireframeWKT(guid); }
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
