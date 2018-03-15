
package org.lbd.ifc2lbd.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class OPM extends abstract_NS{
	public static final String opm_ns = "https://w3id.org/opm#";
	public static final String prov_ns = "http://www.w3.org/ns/prov#";
	public static final String props_ns = "https://w3id.org/product/props#";
	
	public static final Property value =property(opm_ns,"value");
	public static final Property hasState =property(opm_ns,"hasState");
	public static final Resource currentState=resource(opm_ns,"CurrentState");
	
	public static final Property pset_property=property(props_ns, "partOfPset");
	public static final Resource pset=resource(opm_ns,"Pset");
	
	public static final Property generatedAtTime=property(prov_ns, "generatedAtTime");

	
	public static void addNameSpaces(Model model)
	{
		model.setNsPrefix("opm", opm_ns);
	}



}
