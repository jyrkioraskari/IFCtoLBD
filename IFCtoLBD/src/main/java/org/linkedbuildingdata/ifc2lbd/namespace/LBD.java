
package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

public class LBD extends abstract_NS {
	//TODO
    public static final String ns = "https://linkedbuildingdata.org/LBD#";

    public static final Property containsInBoundingBox = property(ns, "containsInBoundingBox"); // contains,

                                                                                                    // containsBoundingBox,
                                                                                                    // containsInVolume
    public static final Property hasBoundingBox = property(ns, "hasBoundingBox"); 
    public static final Property xmin = property(ns, "x-min"); 
    public static final Property xmax = property(ns, "x-max"); 
    public static final Property ymin = property(ns, "y-min"); 
    public static final Property ymax = property(ns, "y-max"); 
    public static final Property zmin = property(ns, "z-min"); 
    public static final Property zmax = property(ns, "z-max"); 
    public static void addNameSpace(Model model) {
        model.setNsPrefix("lbd", ns);
        
        model.add(hasBoundingBox, RDF.type, OWL.ObjectProperty);
        model.add(xmin, RDF.type, OWL.DatatypeProperty);
        model.add(xmax, RDF.type, OWL.DatatypeProperty);
        model.add(ymin, RDF.type, OWL.DatatypeProperty);
        model.add(ymax, RDF.type, OWL.DatatypeProperty);
        model.add(zmin, RDF.type, OWL.DatatypeProperty);
        model.add(zmax, RDF.type, OWL.DatatypeProperty);    	
    }

}
