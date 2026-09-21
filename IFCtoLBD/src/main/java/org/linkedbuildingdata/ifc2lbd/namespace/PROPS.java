package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;

public final class PROPS extends abstract_NS{
    public static final String ns = "http://lbd.arch.rwth-aachen.de/props#";

    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("props", ns);
    }
}
