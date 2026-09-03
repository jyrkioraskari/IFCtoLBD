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
		Resource crs = model.listResourcesWithProperty(RDF.type, projectedType).hasNext()
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
		if (model.listResourcesWithProperty(RDF.type, conversionType).hasNext()) {
			Resource conversion = model.listResourcesWithProperty(RDF.type, conversionType).next();
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
		return new IfcCoordinateReferenceSystemInfo(identifier, values);
	}

	private static void put(Model model, Resource subject, String predicateUri, String key, LinkedHashMap<String, String> values) {
		RDFNode value = subject.getProperty(model.createProperty(predicateUri)) == null ? null
				: subject.getProperty(model.createProperty(predicateUri)).getObject();
		if (value != null && value.isLiteral()) values.putIfAbsent(key, value.asLiteral().getLexicalForm());
	}
}
