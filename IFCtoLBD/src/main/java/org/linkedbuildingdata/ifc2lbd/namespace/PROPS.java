package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public final class PROPS extends abstract_NS{
    public static final String ns = "http://lbd.arch.rwth-aachen.de/props#";
    public static final String psd_ns = "https://www.linkedbuildingdata.net/IFC4-PSD#";

    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("props", ns);
        model.setNsPrefix("IFC4-PSD", psd_ns);
    }
    
    public static final Property namePset=property(psd_ns, "name");
    public static final Property ifdGuidProperty=property(psd_ns,"ifdguid");
    public static final Property propertyDef=property(psd_ns,"propertyDef");
    
}
