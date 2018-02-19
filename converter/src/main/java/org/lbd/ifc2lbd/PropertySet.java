package org.lbd.ifc2lbd;

import java.util.HashMap;
import java.util.Map;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;

public class PropertySet {
	final Map<String, RDFNode> map = new HashMap<>();
	String name;

	public PropertySet(String name) {
		this.name=name;
		if(name.contains("_"))
			this.name=name.split("_")[1];
	}

	public Map<String, RDFNode> getMap() {
		return map;
	}

	public void put(String key, RDFNode value) {
		map.put(toCamelCase(name+" "+key), value);
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

}
