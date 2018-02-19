package org.lbd.ifc2lbd;

import java.util.HashMap;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

public class PropertySet {
	private final Map<String, RDFNode> map = new HashMap<>();
	private String name;
	private final String props_base;
	private boolean isWritten=false;
	private final Resource pset;

	public PropertySet(String name, String props_base, Resource pset) {
		this.name=toCamelCase(name);
		if(name.contains("_"))
			this.name=name.split("_")[1];
		this.props_base=props_base;
		this.pset=pset;
	}

	public void put(String key, RDFNode value) {
		map.put(toCamelCase(key), value);
	}

	private void write()
	{
		isWritten=true;
		for (String k : this.getMap().keySet()) {
			Property property = pset.getModel().createProperty(this.props_base + k);
			pset.addProperty(property, this.getMap().get(k));
		}
	}
	
	
    public void connect(Resource r)
    {
    	if(!isWritten)
    		write();
		Property property = r.getModel().createProperty(this.props_base + name);
		r.addProperty(property, pset);
    	
    	/*for (String k : this.getMap().keySet()) {
			Property property = r.getModel().createProperty(this.props_base + k);
			r.addProperty(property, this.getMap().get(k));

		}*/
    }

	private String toCamelCase(final String init) {
		if (init == null)
			return null;

		StringBuilder ret = new StringBuilder();

		boolean first = true;
		for (final String word : init.split(" ")) {
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
