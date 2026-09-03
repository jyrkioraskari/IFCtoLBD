package org.linkedbuildingdata.ifc2lbd;

import java.util.Map;

/** CRS and map-conversion metadata extracted from an IFC-OWL staging model. */
public record IfcCoordinateReferenceSystemInfo(String identifier, Map<String, String> parameters) {
	public IfcCoordinateReferenceSystemInfo {
		identifier = identifier == null || identifier.isBlank() ? "urn:ogc:def:crs:OGC::EngineeringCRS" : identifier;
		parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
	}
	public static IfcCoordinateReferenceSystemInfo engineering() {
		return new IfcCoordinateReferenceSystemInfo("urn:ogc:def:crs:OGC::EngineeringCRS", Map.of());
	}
}
