package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public final class ATTRIBUTES extends abstract_NS{
    public static final String props_ns = "http://lbd.arch.rwth-aachen.de/attributes#";

    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("attr", props_ns);
    }
       
}
