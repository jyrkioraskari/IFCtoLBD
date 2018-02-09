
package org.lbd.ifc2lbd.ns;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class abstract_NS {
	public static final String owl_ns = "http://www.w3.org/2002/07/owl#";
	public static final String xsd_ns = "http://www.w3.org/2001/XMLSchema#";
	public static final String dce_ns = "http://purl.org/dc/elements/1.1/";
	public static final String vann_ns = "http://purl.org/vocab/vann/";
	public static final String cc_ns = "http://creativecommons.org/ns#";
	public static final String list_ns = "https://w3id.org/list#";

	
	protected static final Property property(String base_uri, String tag) {
		return ResourceFactory.createProperty(base_uri, tag);
	}
	protected static final Resource resource(String base_uri,String name) {
		return ResourceFactory.createResource(base_uri+name);
	}

}
