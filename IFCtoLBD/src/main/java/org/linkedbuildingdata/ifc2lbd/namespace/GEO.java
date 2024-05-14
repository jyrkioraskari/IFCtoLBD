package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

//http://linkedbuildingdata.net/ldac2019/files/LDAC2019_Joseph_ODonovan.pdf
public final class GEO extends abstract_NS{
    public static final String ns = "https://www.opengis.net/ont/geosparql#";
    public static final Resource Geometry =resource(ns,"Geometry");
    public static final Property hasGeometry =property(ns,"hasGeometry");
    public static final Property asWKT =property(ns,"asWKT");
    public static final String wktLiteral =ns+"wktLiteral";
    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("geo", ns);
    }
}