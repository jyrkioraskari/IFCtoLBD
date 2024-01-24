package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

//http://linkedbuildingdata.net/ldac2019/files/LDAC2019_Joseph_ODonovan.pdf
public final class GEO extends abstract_NS{
    public static final String ns = "https://www.opengis.net/ont/geosparql#";
    public static final Property hasGeometry =property(ns,"hasGeometry");
    public static final Property asWKT =property(ns,"asWKT");
    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("geo", ns);
    }
}