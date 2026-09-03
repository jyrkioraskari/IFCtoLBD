package org.linkedbuildingdata.ifc2lbd;

import de.rwth_aachen.dc.lbd.BoundingBox;
import de.rwth_aachen.dc.lbd.IFCGeometry;
import de.rwth_aachen.dc.lbd.MTLDescription;
import de.rwth_aachen.dc.lbd.ObjDescription;

/** Geometry loaded by a provider, including an explicit unavailable result. */
public final class GeometryResult implements AutoCloseable {
	private final IFCGeometry geometry;

	private GeometryResult(IFCGeometry geometry) { this.geometry = geometry; }
	public static GeometryResult of(IFCGeometry geometry) {
		return new GeometryResult(java.util.Objects.requireNonNull(geometry, "geometry"));
	}
	public static GeometryResult unavailable() { return new GeometryResult(null); }
	public boolean isAvailable() { return geometry != null; }
	public BoundingBox getBoundingBox(String guid) { return geometry == null ? null : geometry.getBoundingBox(guid); }
	public ObjDescription getOBJ(String guid) { return geometry == null ? null : geometry.getOBJ(guid); }
	public MTLDescription getMTL(String guid) { return geometry == null ? null : geometry.getMTL(guid); }
	public String getWireframeWKT(String guid) { return geometry == null ? null : geometry.getWireframeWKT(guid); }
	IFCGeometry unwrap() { return geometry; }
	@Override public void close() { if (geometry != null) geometry.close(); }
}
