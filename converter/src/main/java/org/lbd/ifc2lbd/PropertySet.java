package org.lbd.ifc2lbd;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.thrift.wire.RDF_Literal;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.lbd.ifc2lbd.ns.LBD_NS;
import org.lbd.ifc2lbd.ns.OPM;

/**
 * A class where IFC PropertySet is collected from the IFC file
 * 
 *
 */
public class PropertySet {

	private class PsetProperty {
		final Property p;
		final Resource r;

		public PsetProperty(Property p, Resource r) {
			super();
			this.p = p;
			this.r = r;
		}

	}

	private final Map<String, RDFNode> map = new HashMap<>();
	private final Map<String, RDFNode> mapBSDD = new HashMap<>();
	private String name;
	private String label_name;
	private boolean isWritten = false;
	private final int props_level;
	private final Model model;
	private final List<PsetProperty> properties = new ArrayList<>();
	private final boolean hasBlank_nodes;
	private final String uriBase;
	private boolean is_attribute = false;
	private boolean is_bSDD_pset = false;
	private Resource psetDef = null;
	

	public PropertySet(String uriBase, Model model, Model ontology_model,String name, int props_level,
			boolean hasBlank_nodes) {
		this.uriBase = uriBase;
		this.model = model;
		this.name = name;
		this.props_level = props_level;
		this.hasBlank_nodes = hasBlank_nodes;
		StmtIterator iter = ontology_model.listStatements(null, LBD_NS.PROPS_NS.namePset, this.name);
		if(iter.hasNext()) {
			is_bSDD_pset=true;
			this.pset=model.createResource();
			psetDef=iter.next().getSubject();
		}
	}

	String attributegroup_uncompressed_guid;
	public PropertySet(String uriBase, Model model,String name, int props_level,
			boolean hasBlank_nodes, boolean is_attribute,String uncompressed_guid ) {
		this.uriBase = uriBase;
		this.model = model;
		this.name = name;
		if (name.contains("_"))
			this.name = name.split("_")[1];
		this.props_level = props_level;
		this.hasBlank_nodes = hasBlank_nodes;
		this.is_attribute = is_attribute;
		this.attributegroup_uncompressed_guid=uncompressed_guid;
	}

	public void put(String key, RDFNode value) {
		map.put(toCamelCase(key), value);
	}
	
	Resource pset=null;
	private void write_once()
	{
		isWritten = true;
		//if (!is_attribute)
		{
			if(!is_bSDD_pset) 
				this.pset = model.createResource(this.uriBase + "psetGroup_" + toCamelCase(name));
		}
	}

	private void writeOPM_Set(String extracted_guid) {
		properties.clear();
		if(!isWritten)
			write_once();
		System.out.println("this.pset==null:"+(this.pset==null));
		if(this.pset==null)
			return;
		for (String k : this.getMap().keySet()) {
			Resource property_resource;
			if (this.hasBlank_nodes)
				property_resource = pset.getModel().createResource();
			else
				property_resource = pset.getModel().createResource(this.uriBase + k + "_" + extracted_guid);
			
			//if (!is_attribute)
				if(mapBSDD.get(k)!=null) {
					property_resource.addProperty(LBD_NS.PROPS_NS.isBSDDProp, mapBSDD.get(k)); 		
					System.out.println("connected property: "+k+"\nnumber of triples: "+property_resource.listProperties().toList().size());
				}
			
			if (this.props_level == 3) {
				Resource state_resourse;
				if (this.hasBlank_nodes)
					state_resourse = pset.getModel().createResource();
				else
					state_resourse = pset.getModel().createResource(
							this.uriBase + "state_"+k +"_"+ extracted_guid + "_" + System.currentTimeMillis());
				property_resource.addProperty(OPM.hasState, state_resourse);

				LocalDateTime datetime = LocalDateTime.now();
				String time_string = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
				state_resourse.addProperty(RDF.type, OPM.currentState);
				state_resourse.addLiteral(OPM.generatedAtTime, time_string);
				state_resourse.addProperty(OPM.value, this.getMap().get(k));
			} else
				property_resource.addProperty(OPM.value, this.getMap().get(k));

			Property p;
			//if (!is_attribute) 
			{
				p = pset.getModel().createProperty(LBD_NS.PROPS_NS.props_ns + toCamelCase(k));
				this.properties.add(new PsetProperty(p, property_resource));
			}
		}
	}

	public void putPropertyRef(RDFNode property) {
		String pname = property.asLiteral().getString();
		if(is_bSDD_pset) {
			StmtIterator iter = psetDef.listProperties(LBD_NS.PROPS_NS.propertyDef);
			while(iter.hasNext()) {
				Resource prop = iter.next().getResource();
				StmtIterator iterProp = prop.listProperties(LBD_NS.PROPS_NS.namePset);
				while(iterProp.hasNext()) 
				{
					Literal psetPropName = iterProp.next().getLiteral();
					if(psetPropName.getString().equals(pname))
						mapBSDD.put(toCamelCase(property.toString()), prop);
				}
			}
		}
	}
	
	Set<String> hashes=new HashSet<>();
	
	/**
	 * Adds property value property for an resource.
	 * @param r_org          The Jena Resource in the model 
	 * @param extracted_guid The GUID of the elemet
	 */
	public void connect(Resource r_org,String extracted_guid) {
		Resource r = this.model.createResource(r_org.getURI());
		if (this.props_level > 1) {	
			System.out.println("props level 2-3!!");
			if(hashes.add(extracted_guid))
			{
			  System.out.println("hashes add");
			  writeOPM_Set(extracted_guid);
			}
			System.out.println("this.properties: "+this.properties.size());
			for (PsetProperty pp : this.properties) {
				if(!r.getModel().listStatements(r, pp.p, pp.r).hasNext()) {
				System.out.println("adding property "+pp.r+" - number of triples: "+pp.r.listProperties().toList().size());
				System.out.println("is defined: "+pp.r.listProperties(LBD_NS.PROPS_NS.isBSDDProp).toList().size());
				r.addProperty(pp.p, pp.r);
				}
			}
		} else {

			for (String k : this.getMap().keySet()) {
				Property property;
				if (name.equals("attributes"))
					property = r.getModel().createProperty(LBD_NS.PROPS_NS.props_ns + k + "_attribute_simple");
				else
					property = r.getModel().createProperty(LBD_NS.PROPS_NS.props_ns + k + "_simple");
				r.addProperty(property, this.getMap().get(k));
			}
		}
	}

	/**
	 * Converts a string into the CamelCase notation described in:
	 * https://en.wikipedia.org/wiki/Camel_case
	 *  
	 * @param txt
	 * @return
	 */
	public String toCamelCase(final String txt) {
		if (txt == null)
			return null;

		StringBuilder ret = new StringBuilder();

		boolean first = true;
		for (final String word : txt.split(" ")) {
			if (!word.isEmpty()) {
				if (first) {
					ret.append(filterCharaters(word.substring(0, 1).toLowerCase()));
					first = false;
				} else
					ret.append(filterCharaters(word.substring(0, 1).toUpperCase()));
				ret.append(filterCharaters(word.substring(1)));
			}
		}

		return ret.toString();
	}

	/**
	 * Converts a CamelCase string into space separate words.
	 * https://en.wikipedia.org/wiki/Camel_case
	 *  
	 * @param txt
	 * @return
	 */
	public String toUnCamelCase(final String txt) {
		if (txt == null)
			return null;

		StringBuilder ret = new StringBuilder();
		for(int i=0;i<txt.length();i++)
		{
			char c=txt.charAt(i);
			if(i>0 && Character.isUpperCase(c))
			{
				ret.append(" ");
				ret.append(Character.toLowerCase(c));
			}
			else
			if(c=='_')
				ret.append(" ");
			else
			  ret.append(c);
		}
		return ret.toString();	
	}
	
	
	/**
	 * Removes all characters other than letters from a string
	 * 
	 * @param txt A text string
	 * @return
	 */
	private String filterCharaters(String txt) {
		StringBuilder ret = new StringBuilder();
		for (byte cb : txt.getBytes()) {
			char c = (char) cb;
			if (Character.isAlphabetic(c))
				ret.append(c);
		}
		return ret.toString();
	}

	private Map<String, RDFNode> getMap() {
		return map;
	}

	
}
