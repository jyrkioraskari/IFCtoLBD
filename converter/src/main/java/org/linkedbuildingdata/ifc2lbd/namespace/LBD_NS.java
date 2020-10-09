
package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class LBD_NS extends abstract_NS{
    // http://linkedbuildingdata.net/ldac2019/files/LDAC2019_Joseph_ODonovan.pdf
	public static class GEO {
		public static final String GEO_ns = "http://www.opengis.net/ont/geosparql#";
		public static final Property hasGeometry =property(GEO_ns,"hasGeometry");
		public static final Property asWKT =property(GEO_ns,"asWKT");
		public static void addNameSpace(Model model)
		{
			model.setNsPrefix("geo", GEO_ns);
		}
	}

	public static class SMLS {
		public static final String SMLS_ns = "https://w3id.org/def/smls-owl#";
		public static final Property unit =property(SMLS_ns,"unit");
		public static final Property accuracy =property(SMLS_ns,"accuracy");
		public static void addNameSpace(Model model)
		{
			model.setNsPrefix("smls", SMLS_ns);
		}
	}
	
	public static class UNIT {
		public static final String UNIT_ns = "http://qudt.org/vocab/unit/";
		public static final Resource METER =resource(UNIT_ns,"M");
		public static final Resource SQUARE_METRE =resource(UNIT_ns,"M2");
		public static final Resource CUBIC_METRE =resource(UNIT_ns,"M3");
		public static final Resource RADIAN =resource(UNIT_ns,"RAD");
		public static void addNameSpace(Model model)
		{
			model.setNsPrefix("unit", UNIT_ns);
		}
		
	}
	
	

    
    public static class LBD {
        public static final String lbd_ns = "https://linkebuildingdata.org/LBD#";
        

        public static final Property containsInBoundingBox =property(lbd_ns,"containsInBoundingBox"); //contains, containsBoundingBox, containsInVolume
        public static void addNameSpace(Model model)
        {
            model.setNsPrefix("lbd", lbd_ns);
        }
        
    }
	
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
			String[] splitted=name.split("_");
			return property(beo_ns,splitted[0]);
		}
	}

	
	public static class PROPS_NS {
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

}
