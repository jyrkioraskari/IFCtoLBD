
package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public class LBD extends abstract_NS {
    public static final String lbd_ns = "https://linkebuildingdata.org/LBD#";

    public static final Property containsInBoundingBox = property(lbd_ns, "containsInBoundingBox"); // contains,
                                                                                                    // containsBoundingBox,
                                                                                                    // containsInVolume
    public static final Property hasBoundingBox = property(lbd_ns, "hasBoundingBox"); 
    public static final Property xmin = property(lbd_ns, "x-min"); 
    public static final Property xmax = property(lbd_ns, "x-max"); 
    public static final Property ymin = property(lbd_ns, "y-min"); 
    public static final Property ymax = property(lbd_ns, "y-max"); 

    public static void addNameSpace(Model model) {
        model.setNsPrefix("lbd", lbd_ns);
    }

}
