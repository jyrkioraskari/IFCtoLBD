package org.linkedbuildingdata.ifc2lbd;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Strategy for assigning output identities to IFC resources. */
public interface UriPolicy {
	String id();
	default String configurationId() { return id(); }
	Resource createResource(Resource source, Model output, String productType, IfcOWL ifcOWL,
			String baseUri, boolean exportIfcOWL);
}
