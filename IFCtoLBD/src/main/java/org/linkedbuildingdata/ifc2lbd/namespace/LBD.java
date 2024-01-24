
package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public class LBD extends abstract_NS {
	//TODO
    public static final String ns = "https://linkebuildingdata.org/LBD#";

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
        
        
    }

}
