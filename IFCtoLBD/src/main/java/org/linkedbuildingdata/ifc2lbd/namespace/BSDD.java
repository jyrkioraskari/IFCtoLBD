package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class BSDD extends abstract_NS {
	public static final String class_ns = "https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/class/";
	public static final String property_ns = "https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/prop/";
	public static final String meta_ns = "https://w3id.org/ifc2lbd/bsdd-meta#";

	public static final Resource propertySet = resource(meta_ns, "PropertySet");
	public static final Property hasPropertySet = property(meta_ns, "hasPropertySet");
	public static final Property containsProperty = property(meta_ns, "containsProperty");

	public static void addNameSpaces(Model model) {
		model.setNsPrefix("bsddc", class_ns);
		model.setNsPrefix("bsddp", property_ns);
		model.setNsPrefix("bsddm", meta_ns);
		model.setNsPrefix("opm", OPM.ns);
		model.setNsPrefix("prov", OPM.prov_ns);
		model.setNsPrefix("schema", OPM.schema_ns);
	}
}
