package org.lbd.ifc2lbd;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.lbd.ifc2lbd.ns.BOT;
import org.lbd.ifc2lbd.ns.IfcOWLNameSpace;
import org.lbd.ifc2lbd.ns.OPM;

public class PropertySet {
	private final Map<String, RDFNode> map = new HashMap<>();
	
	private String name;

	
	
	private boolean isWritten = false;
	private final Resource pset;
	private final int props_level;

	private class PsetProperty {
		final Property p;
		final Resource r;

		public PsetProperty(Property p, Resource r) {
			super();
			this.p = p;
			this.r = r;
		}

	}
	

	private List<PsetProperty> properties = new ArrayList<>();

	public PropertySet(String name, Resource pset, int props_level) {
		this.name = name;
		if (name.contains("_"))
			this.name = name.split("_")[1];
		this.pset = pset;
		this.props_level = props_level;
	}

	public void put(String key, RDFNode value) {
		map.put(toCamelCase(key), value);
	}

	private void writeOPM_Set() {
		pset.addProperty(RDFS.label, name);
		pset.addProperty(RDF.type, OPM.pset);
		isWritten = true;
		for (String k : this.getMap().keySet()) {

			//Resource property_resourse = pset.getModel().createResource();  // Blank node 
			Resource property_resourse = pset.getModel().createResource(pset.getURI()+"_"+k); 
			property_resourse.addProperty(OPM.pset_property, pset);
			Resource state_resourse = pset.getModel().createResource();
			property_resourse.addProperty(OPM.hasState, state_resourse);

			LocalDateTime datetime = LocalDateTime.now();
			String time_string = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			state_resourse.addProperty(RDF.type, OPM.currentState);
			state_resourse.addLiteral(OPM.generatedAtTime, time_string);
			state_resourse.addProperty(OPM.value, this.getMap().get(k));

			Property p = pset.getModel().createProperty(OPM.props_ns + toCamelCase(name + "_" + k));
			this.properties.add(new PsetProperty(p, property_resourse));
		}
	}

	public void connect(Resource r) {
		if (this.props_level > 1) {
			if (!isWritten)
				writeOPM_Set();
			for (PsetProperty pp : this.properties) {
				r.addProperty(pp.p, pp.r);

			}
		} else {
			//Property property = r.getModel().createProperty(BOT.PropertySet.pset_ns + name);
			//r.addProperty(property, pset);

			for (String k : this.getMap().keySet()) {
				Property property = r.getModel().createProperty(BOT.PropertySet.pset_ns + name+"_"+k);
				r.addProperty(property, this.getMap().get(k));

			}

		}
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
