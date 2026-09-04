package org.linkedbuildingdata.ifc2lbd;

import java.util.LinkedHashMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Reads IFC4 coordinate-reference-system and map-conversion entities without changing geometry. */
public final class IfcCoordinateReferenceSystemExtractor {
	private IfcCoordinateReferenceSystemExtractor() { }

	public static IfcCoordinateReferenceSystemInfo extract(Model model, IfcOWL ifc) {
		String ns = ifc.getIfcURI();
		Resource projectedType = model.createResource(ns + "IfcProjectedCRS");
		Resource conversionType = model.createResource(ns + "IfcMapConversion");
		Resource conversion = model.listResourcesWithProperty(RDF.type, conversionType).hasNext()
				? model.listResourcesWithProperty(RDF.type, conversionType).next() : null;
		Resource crs = conversion == null ? null : objectResource(model, conversion, ns + "sourceCRS_IfcMapConversion");
		if (crs == null) crs = model.listResourcesWithProperty(RDF.type, projectedType).hasNext()
				? model.listResourcesWithProperty(RDF.type, projectedType).next() : null;
		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		if (crs != null) {
			put(model, crs, ns + "name_IfcCoordinateReferenceSystem", "name", values);
			put(model, crs, ns + "description_IfcCoordinateReferenceSystem", "description", values);
			put(model, crs, ns + "geodeticDatum_IfcCoordinateReferenceSystem", "geodeticDatum", values);
			put(model, crs, ns + "verticalDatum_IfcCoordinateReferenceSystem", "verticalDatum", values);
			put(model, crs, ns + "mapProjection_IfcProjectedCRS", "mapProjection", values);
			put(model, crs, ns + "mapZone_IfcProjectedCRS", "mapZone", values);
		}
		if (conversion != null) {
			put(model, conversion, ns + "eastings_IfcMapConversion", "eastings", values);
			put(model, conversion, ns + "northings_IfcMapConversion", "northings", values);
			put(model, conversion, ns + "orthogonalHeight_IfcMapConversion", "orthogonalHeight", values);
			put(model, conversion, ns + "xAxisAbscissa_IfcMapConversion", "xAxisAbscissa", values);
			put(model, conversion, ns + "xAxisOrdinate_IfcMapConversion", "xAxisOrdinate", values);
			put(model, conversion, ns + "scale_IfcMapConversion", "scale", values);
		}
		String identifier = values.get("mapProjection");
		if (identifier == null || identifier.isBlank()) identifier = values.get("geodeticDatum");
		if (identifier == null || identifier.isBlank()) identifier = values.get("mapZone");
		if (identifier != null) {
			java.util.regex.Matcher epsg = java.util.regex.Pattern.compile("(?i)(?:epsg[:/ ]*)?(\\d{4,5})").matcher(identifier);
			if (epsg.find()) identifier = "http://www.opengis.net/def/crs/EPSG/0/" + epsg.group(1);
			else {
				// IFC commonly stores the projection name (for example, "UTM") and
				// the zone (for example, "31N") in separate attributes. Use the
				// explicit map zone when available so ETRS89/UTM is unambiguous.
				String zoneSource = values.getOrDefault("mapZone", identifier);
				java.util.regex.Matcher zone = java.util.regex.Pattern.compile("(?i)(?:zone|utm)?\\s*([0-9]{1,2})").matcher(zoneSource.trim());
				if (zone.find() && values.getOrDefault("geodeticDatum", "").toUpperCase().contains("ETRS89"))
					identifier = "http://www.opengis.net/def/crs/EPSG/0/" + (25800 + Integer.parseInt(zone.group(1)));
			}
		}
		return new IfcCoordinateReferenceSystemInfo(identifier, values);
	}
	private static Resource objectResource(Model model, Resource subject, String predicateUri) {
		RDFNode value = subject.getProperty(model.createProperty(predicateUri)) == null ? null : subject.getProperty(model.createProperty(predicateUri)).getObject();
		return value != null && value.isResource() ? value.asResource() : null;
	}

	private static void put(Model model, Resource subject, String predicateUri, String key, LinkedHashMap<String, String> values) {
		RDFNode value = subject.getProperty(model.createProperty(predicateUri)) == null ? null
				: subject.getProperty(model.createProperty(predicateUri)).getObject();
		if (value != null && value.isLiteral()) values.putIfAbsent(key, value.asLiteral().getLexicalForm());
	}
}
