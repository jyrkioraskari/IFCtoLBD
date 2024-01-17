
package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class  abstract_NS {
	
	protected static final Property property(String base_uri, String tag) {
		return ResourceFactory.createProperty(base_uri, tag);
	}
	protected static final Resource resource(String base_uri,String name) {
		return ResourceFactory.createResource(base_uri+name);
	}

}
