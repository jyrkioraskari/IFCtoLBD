package org.lbd.ifc2lbd;

import java.util.HashMap;
import java.util.Map;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;

public class PropertySet {
	final Map<String, RDFNode> map = new HashMap<>();
	final String name;

	public PropertySet(String name) {
		this.name=name;
	}

	public Map<String, RDFNode> getMap() {
		return map;
	}

	public void put(String key, RDFNode value) {
		map.put(key, value);
	}

}
