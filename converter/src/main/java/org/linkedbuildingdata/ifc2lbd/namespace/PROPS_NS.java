package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public final class PROPS_NS extends abstract_NS{
    public static final String props_ns = "http://lbd.arch.rwth-aachen.de/props#";
    public static final String bsddprops_ns = "https://buildingsmart.org/bsddld#";
    public static final String psd_ns = "http://lbd.arch.rwth-aachen.de/ifcOWL/IFC4-PSD#";

    public static void addNameSpace(Model model)
    {
        model.setNsPrefix("props", props_ns);
        model.setNsPrefix("bsddld", bsddprops_ns);
        model.setNsPrefix("IFC4-PSD", psd_ns);
    }
    
    public static final Property isBSDDProp=property(bsddprops_ns, "isBSDDProperty");   
    public static final Property namePset=property(psd_ns, "name");
    public static final Property ifdGuidProperty=property(psd_ns,"ifdguid");
    public static final Property propertyDef=property(psd_ns,"propertyDef");
    
}
