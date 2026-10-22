package org.linkedbuildingdata.ifc2lbd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import de.rwth_aachen.dc.lbd.BoundingBox;

/** CRS metadata and the validated IFC engineering-to-map transformation. */
public final class IfcCoordinateReferenceSystemInfo {
	public static final String ENGINEERING_CRS = "urn:ogc:def:crs:OGC::EngineeringCRS";

	private final String identifier;
	private final Map<String, String> parameters;
	private final List<String> warnings;
	private final String operationType;
	private final boolean transformable;
	private final double sourceUnitToMetres;
	private final Transform transform;

	/** Compatibility constructor for callers that supply an already-associated operation. */
	public IfcCoordinateReferenceSystemInfo(String identifier, Map<String, String> parameters) {
		this(identifier, parameters, List.of(), parameters != null && parameters.containsKey("factorX")
				? "IfcMapConversionScaled" : "IfcMapConversion", 1.0, true);
	}

	IfcCoordinateReferenceSystemInfo(String identifier, Map<String, String> parameters, List<String> warnings,
			String operationType, double sourceUnitToMetres, boolean associationSafe) {
		this.identifier = identifier == null || identifier.isBlank() ? ENGINEERING_CRS : identifier;
		this.parameters = Map.copyOf(parameters == null ? Map.of() : new LinkedHashMap<>(parameters));
		this.operationType = operationType;
		this.sourceUnitToMetres = sourceUnitToMetres;
		List<String> diagnostics = new ArrayList<>(warnings == null ? List.of() : warnings);
		Transform candidate = validateTransform(this.parameters, operationType, sourceUnitToMetres, diagnostics);
		this.transformable = associationSafe && !ENGINEERING_CRS.equals(this.identifier) && candidate != null;
		this.transform = this.transformable ? candidate : null;
		this.warnings = List.copyOf(diagnostics);
	}

	public static IfcCoordinateReferenceSystemInfo engineering() {
		return new IfcCoordinateReferenceSystemInfo(ENGINEERING_CRS, Map.of(), List.of(), null, 1.0, true);
	}

	public String identifier() { return identifier; }
	public Map<String, String> parameters() { return parameters; }
	public List<String> warnings() { return warnings; }
	public String operationType() { return operationType; }
	public boolean hasMapConversion() { return transformable; }
	public boolean canTransform() { return transformable; }
	public double sourceUnitToMetres() { return sourceUnitToMetres; }

	/** Transforms one provider coordinate. Provider and operation source units are explicit. */
	public Point transform(double x, double y, double z, double geometryUnitToMetres) {
		if (!transformable) throw new IllegalStateException("No safe, complete IFC map conversion is available");
		if (!Double.isFinite(geometryUnitToMetres) || geometryUnitToMetres <= 0)
			throw new IllegalArgumentException("Geometry unit-to-metre factor must be finite and positive");
		double toSource = geometryUnitToMetres / sourceUnitToMetres;
		return transform.apply(x * toSource, y * toSource, z * toSource);
	}

	public String transform(BoundingBox box) { return transform(box, sourceUnitToMetres); }

	/** Transforms all eight corners before deriving the projected axis-aligned envelope. */
	public String transform(BoundingBox box, double geometryUnitToMetres) {
		double loX = Double.POSITIVE_INFINITY, loY = Double.POSITIVE_INFINITY, loZ = Double.POSITIVE_INFINITY;
		double hiX = Double.NEGATIVE_INFINITY, hiY = Double.NEGATIVE_INFINITY, hiZ = Double.NEGATIVE_INFINITY;
		for (double x : new double[] { box.getMin().x, box.getMax().x })
			for (double y : new double[] { box.getMin().y, box.getMax().y })
				for (double z : new double[] { box.getMin().z, box.getMax().z }) {
					Point point = transform(x, y, z, geometryUnitToMetres);
					loX = Math.min(loX, point.x); hiX = Math.max(hiX, point.x);
					loY = Math.min(loY, point.y); hiY = Math.max(hiY, point.y);
					loZ = Math.min(loZ, point.z); hiZ = Math.max(hiZ, point.z);
				}
		return "MULTIPOINT Z((" + loX + " " + loY + " " + loZ + "), ("
				+ hiX + " " + hiY + " " + hiZ + "))";
	}

	public String transformWkt(String wkt) { return transformWkt(wkt, sourceUnitToMetres); }

	/** Parses WKT structurally so every coordinate, including collection members, is transformed once. */
	public String transformWkt(String wkt, double geometryUnitToMetres) {
		final Geometry geometry;
		try {
			geometry = new WKTReader().read(wkt);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Invalid WKT geometry", e);
		}
		geometry.apply(new CoordinateSequenceFilter() {
			@Override public void filter(CoordinateSequence sequence, int index) {
				double z = sequence.getDimension() > 2 ? sequence.getOrdinate(index, 2) : 0.0;
				if (!Double.isFinite(z)) z = 0.0;
				Point point = transform(sequence.getX(index), sequence.getY(index), z, geometryUnitToMetres);
				sequence.setOrdinate(index, 0, point.x);
				sequence.setOrdinate(index, 1, point.y);
				if (sequence.getDimension() > 2) sequence.setOrdinate(index, 2, point.z);
			}
			@Override public boolean isDone() { return false; }
			@Override public boolean isGeometryChanged() { return true; }
		});
		geometry.geometryChanged();
		boolean declared3d = geometry.getCoordinate() != null && Double.isFinite(geometry.getCoordinate().getZ());
		if (geometry.isEmpty()) {
			String upper = wkt.stripLeading().toUpperCase(java.util.Locale.ROOT);
			int opening = upper.indexOf('(');
			String header = opening < 0 ? upper : upper.substring(0, opening);
			declared3d = header.contains(" Z") || header.endsWith("Z EMPTY");
			if (declared3d) return geometry.getGeometryType().toUpperCase(java.util.Locale.ROOT) + " Z EMPTY";
		}
		WKTWriter writer = new WKTWriter(declared3d ? 3 : 2);
		return writer.write(geometry);
	}

	public record Point(double x, double y, double z) { }

	private static Transform validateTransform(Map<String, String> values, String type, double sourceUnitToMetres,
			List<String> warnings) {
		if (type == null) return null;
		if (!"IfcMapConversion".equals(type) && !"IfcMapConversionScaled".equals(type)) {
			warnings.add("Unsupported IFC coordinate operation " + type + "; geometry remains local.");
			return null;
		}
		if (!Double.isFinite(sourceUnitToMetres) || sourceUnitToMetres <= 0) {
			warnings.add("The operation source length unit is missing or invalid; geometry remains local.");
			return null;
		}
		Double east = required(values, "eastings", warnings);
		Double north = required(values, "northings", warnings);
		Double height = required(values, "orthogonalHeight", warnings);
		Double scale = optional(values, "scale", 1.0, warnings);
		Double aValue = optionalNullable(values, "xAxisAbscissa", warnings);
		Double bValue = optionalNullable(values, "xAxisOrdinate", warnings);
		if ((values.containsKey("xAxisAbscissa") && aValue == null)
				|| (values.containsKey("xAxisOrdinate") && bValue == null)) return null;
		double a = aValue == null ? (bValue == null ? 1.0 : 0.0) : aValue;
		double b = bValue == null ? 0.0 : bValue;
		double length = Math.hypot(a, b);
		if (!Double.isFinite(length) || length == 0.0) {
			warnings.add("The map-conversion x-axis direction is zero or non-finite; geometry remains local.");
			return null;
		}
		if (scale == null || scale <= 0.0) {
			warnings.add("The map-conversion scale must be finite and positive; geometry remains local.");
			return null;
		}
		double factorX = 1.0, factorY = 1.0, factorZ = 1.0;
		if ("IfcMapConversionScaled".equals(type)) {
			Double x = required(values, "factorX", warnings);
			Double y = required(values, "factorY", warnings);
			Double z = required(values, "factorZ", warnings);
			if (x == null || y == null || z == null || x == 0 || y == 0 || z == 0) {
				warnings.add("Scaled map-conversion factors must be finite and non-zero; geometry remains local.");
				return null;
			}
			factorX = x; factorY = y; factorZ = z;
		}
		if (east == null || north == null || height == null) return null;
		return new Transform(east, north, height, a / length, b / length, scale, factorX, factorY, factorZ);
	}

	private static Double required(Map<String, String> values, String key, List<String> warnings) {
		if (!values.containsKey(key)) {
			warnings.add("Required map-conversion parameter " + key + " is missing; geometry remains local.");
			return null;
		}
		return optionalNullable(values, key, warnings);
	}

	private static Double optional(Map<String, String> values, String key, double fallback, List<String> warnings) {
		return values.containsKey(key) ? optionalNullable(values, key, warnings) : fallback;
	}

	private static Double optionalNullable(Map<String, String> values, String key, List<String> warnings) {
		String value = values.get(key);
		if (value == null) return null;
		try {
			double parsed = Double.parseDouble(value);
			if (Double.isFinite(parsed)) return parsed;
		} catch (NumberFormatException ignored) { }
		warnings.add("Map-conversion parameter " + key + " is not a finite number; geometry remains local.");
		return null;
	}

	private record Transform(double east, double north, double height, double cos, double sin, double scale,
			double factorX, double factorY, double factorZ) {
		Point apply(double x, double y, double z) {
			// IFC4.3 ordering: uniform scale, per-axis factors, rotation, translation.
			double scaledX = scale * factorX * x;
			double scaledY = scale * factorY * y;
			return new Point(east + cos * scaledX - sin * scaledY,
					north + sin * scaledX + cos * scaledY,
					height + scale * factorZ * z);
		}
	}
}
