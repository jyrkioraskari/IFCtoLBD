package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public final class BOT extends abstract_NS{
    public static final String bot_ns = "https://w3id.org/bot#";
    

    public static final Property hasBuilding =property(bot_ns,"hasBuilding");
    public static final Property hasStorey =property(bot_ns,"hasStorey");
    public static final Property adjacentElement =property(bot_ns,"adjacentElement");
    public static final Property containsElement =property(bot_ns,"containsElement");

    public static final Property hasSubElement =property(bot_ns,"hasSubElement");
    //public static final Property aggregates =property(bot_ns,"aggregates"); DEPRECATED

    public static final Property hasSpace =property(bot_ns,"hasSpace");
    
    public static final Resource site=resource(bot_ns,"Site");
    public static final Resource building=resource(bot_ns,"Building");
    public static final Resource space =resource(bot_ns,"Space");
    public static final Resource storey =resource(bot_ns,"Storey");
    public static final Resource element  =resource(bot_ns,"Element");
    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("bot", bot_ns);
    }
    
}
