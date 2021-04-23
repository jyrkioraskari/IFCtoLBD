package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.core.utils.StringOperations;

public final class Product extends abstract_NS{
    public static final String beo_ns = "https://pi.pauwel.be/voc/buildingelement#"; 
    public static final String furnishing_ns = "http://pi.pauwel.be/voc/furniture#";
    public static final String mep_ns = "http://pi.pauwel.be/voc/distributionelement#";  

    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("beo", beo_ns);
        model.setNsPrefix("furn", furnishing_ns);
        model.setNsPrefix("mep", mep_ns);
    }
    
    public static Resource getProductType(Resource ifOwlClass)
    {
        String uri=ifOwlClass.getLocalName().substring(3);
        return resource(beo_ns, uri);
    }
    
    public static Property getProperty(String name) {
        String[] splitted=StringOperations.split(name, '_');
        return property(beo_ns,splitted[0]);
    }
}
