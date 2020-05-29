
package org.lbd.ifc2lbd.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class LBD_NS extends abstract_NS{
	
	
	public static class BOT {
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

	public static class Product {
		public static final String beo_ns = "https://pi.pauwel.be/voc/buildingelement#"; 
		public static final String furnishing_ns = "https://pi.pauwel.be/voc/furniture#";
		public static final String mep_ns = "https://pi.pauwel.be/voc/distributionelement#";  

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
			String[] splitted=name.split("_");
			return property(beo_ns,splitted[0]);
		}
	}


	public static class PROPS_NS {
		public static final String props_ns = "https://w3id.org/props#";
		public static final String bsddprops_ns = "https://buildingsmart.org/bsddld#";
		public static final String psd_ns = "http://www.buildingsmart-tech.org/ifcOWL/IFC4-PSD#";

		public static void addNameSpace(Model model)
		{
			model.setNsPrefix("props", props_ns);
			model.setNsPrefix("bsddld", bsddprops_ns);
			model.setNsPrefix("IFC4-PSD", psd_ns);
		}
		
		//public static final Resource props=resource(props_ns,"Pset");
		//public static final Property partofPset=property(props_ns, "partOfPset");		
		public static final Property isBSDDProp=property(bsddprops_ns, "isBSDDProperty");	
		public static final Property namePset=property(psd_ns, "name");
		public static final Property ifdGuidProperty=property(psd_ns,"ifdguid");
		public static final Property propertyDef=property(psd_ns,"propertyDef");

		public static final Resource attribute_group=resource(props_ns,"AttributesGroup");
		public static final Property partofAG=property(props_ns, "partOfAttributesGroup");
		
		public static final Resource propertyset=resource(props_ns,"PropertySet");
		public static final Resource property=resource(props_ns,"Property");
		
		public static final Property hasPropertySet =property(props_ns,"hasPropertySet");
		
		public static final Property hasProperty =property(props_ns,"hasProperty");
		public static final Property hasValue =property(props_ns,"hasValue");
		public static final Property hasName =property(props_ns,"hasName");
		
	}

}
