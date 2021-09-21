
package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public class LBD extends abstract_NS {
    public static final String lbd_ns = "https://linkebuildingdata.org/LBD#";

    public static final Property containsInBoundingBox = property(lbd_ns, "containsInBoundingBox"); // contains,
                                                                                                    // containsBoundingBox,
                                                                                                    // containsInVolume

    public static void addNameSpace(Model model) {
        model.setNsPrefix("lbd", lbd_ns);
    }

}
