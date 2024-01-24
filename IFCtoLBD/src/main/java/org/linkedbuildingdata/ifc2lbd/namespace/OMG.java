package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public final class OMG extends abstract_NS{
    public static final String ns = "https://w3id.org/omg#";
    public static final Property hasGeometry =property(ns,"hasGeometry");
    public static final Property asWKT =property(ns,"asWKT");
    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("omg", ns);
    }
}