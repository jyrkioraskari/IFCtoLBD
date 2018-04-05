
package org.lbd.ifc2lbd.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class OPM extends abstract_NS{
	public static final String opm_ns = "https://w3id.org/opm#";
	public static final String prov_ns = "http://www.w3.org/ns/prov#";
	
	
	public static final String schema_ns = "http://schema.org/";
	public static final String seas_ns = "https://w3id.org/seas/";
	
	
	
	public static final Property value =property(schema_ns,"value");
	public static final Property hasState =property(seas_ns,"evaluation");
	public static final Resource currentState=resource(opm_ns,"CurrentState");


	
	public static final Property generatedAtTime=property(prov_ns, "generatedAtTime");

	public static void addNameSpacesL2(Model model)
	{
		model.setNsPrefix("schema", schema_ns);
	}
	
	public static void addNameSpacesL3(Model model)
	{
		model.setNsPrefix("opm", opm_ns);
		model.setNsPrefix("schema", schema_ns);
		model.setNsPrefix("seas", seas_ns);
	}



}
