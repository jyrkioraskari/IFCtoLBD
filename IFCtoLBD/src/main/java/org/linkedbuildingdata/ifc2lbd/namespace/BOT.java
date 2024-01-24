package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public final class BOT extends abstract_NS{
    public static final String ns = "https://w3id.org/bot#";
    

    public static final Property hasBuilding =property(ns,"hasBuilding");
    public static final Property hasStorey =property(ns,"hasStorey");
    public static final Property adjacentElement =property(ns,"adjacentElement");
    public static final Property containsElement =property(ns,"containsElement");

    public static final Property hasSubElement =property(ns,"hasSubElement");
    public static final Property hasSpace =property(ns,"hasSpace");
    
    public static final Resource site=resource(ns,"Site");
    public static final Resource building=resource(ns,"Building");
    public static final Resource space =resource(ns,"Space");
    public static final Resource storey =resource(ns,"Storey");
    public static final Resource element  =resource(ns,"Element");
    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("bot", ns);
    }
    
}
