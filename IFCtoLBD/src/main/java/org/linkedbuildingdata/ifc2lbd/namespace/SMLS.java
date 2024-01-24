package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public final class SMLS extends abstract_NS{
    public static final String ns = "https://w3id.org/def/smls-owl#";
    public static final Property unit =property(ns,"unit");
    public static final Property accuracy =property(ns,"accuracy");
    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("smls", ns);
    }
}
