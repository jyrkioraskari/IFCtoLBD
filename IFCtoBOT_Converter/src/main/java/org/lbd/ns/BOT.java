
package org.lbd.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class BOT extends abstract_NS{
	public static final String bot_ns = "https://w3id.org/bot#";
	

	public static final Property hasBuilding =property(bot_ns,"hasBuilding");
	public static final Property hasStorey =property(bot_ns,"hasStorey");
	public static final Property adjacentElement =property(bot_ns,"adjacentElement");
	public static final Property containsElement =property(bot_ns,"containsElement");

	public static final Property hostsElement =property(bot_ns,"hostsElement");
	public static final Property aggregates =property(bot_ns,"aggregates");

	public static final Property hasSpace =property(bot_ns,"hasSpace");
	
	public static final Resource site=resource(bot_ns,"Site");
	public static final Resource building=resource(bot_ns,"Building");
	public static final Resource space =resource(bot_ns,"Space");
	public static final Resource storey =resource(bot_ns,"Storey");
	public static final Resource element  =resource(bot_ns,"Element");
	
	public static void addNameSpaces(Model model)
	{
		model.setNsPrefix("bot", bot_ns);
		Product.addNameSpace(model);
		PropertySet.addNameSpace(model);
		LocalProperty.addNameSpace(model);
	}


	public static class Product {
		public static final String product_ns = "https://w3id.org/product/BuildingElements#";

		public static void addNameSpace(Model model)
		{
			model.setNsPrefix("product", product_ns);
		}
		
		public static Resource getProductType(Resource ifOwlClass)
		{
			String uri=ifOwlClass.getLocalName().substring(3);
			return resource(product_ns, uri);
		}
		
		public static Property getProperty(String name) {
			String[] splitted=name.split("_");
			return property(product_ns,splitted[0]);
		}
	}


	public static class LocalProperty {
		public static final String local_ns = "https://w3id.org/product/props/";

		public static void addNameSpace(Model model)
		{
			model.setNsPrefix("property", local_ns);
		}
		public static Property getProperty(String name) {
			String[] splitted=name.split("_");
			return property(local_ns,splitted[0]);
		}
	}

	
	public static class PropertySet {
		public static final String pset_ns = "https://w3id.org/propertyset#";

		public static void addNameSpace(Model model)
		{
			model.setNsPrefix("pset", pset_ns);
		}
		
		public static final Resource propertyset=resource(pset_ns,"PropertySet");
		public static final Resource property=resource(pset_ns,"Property");
		
		public static final Property hasPropertySet =property(pset_ns,"hasPropertySet");
		public static final Property hasProperty =property(pset_ns,"hasProperty");
		public static final Property hasValue =property(pset_ns,"hasValue");
		public static final Property hasName =property(pset_ns,"hasName");
		
	}

}
