package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

public class UNIT extends abstract_NS{
	public static final String ns = "http://qudt.org/vocab/unit/";
	public static final Resource METER =resource(ns,"M");
	public static final Resource MILLI_METER =resource(ns,"MilliM");
	public static final Resource SQUARE_METRE =resource(ns,"M2");
	public static final Resource SQUARE_MILLI_METRE =resource(ns,"MilliM2");
	public static final Resource CUBIC_METRE =resource(ns,"M3");
	public static final Resource CUBIC_MILLI_METER =resource(ns,"MilliM3");
	public static final Resource RADIAN =resource(ns,"RAD");
	public static void addNameSpace(Model model)
	{
		model.setNsPrefix("unit", ns);
	}
	
}