package org.lbd.ifc2lbd;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.lbd.ifc2lbd.ns.LBD_NS;
import org.lbd.ifc2lbd.ns.OPM;

import com.openifctools.guidcompressor.GuidCompressor;

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
	private String name;
	private boolean isWritten = false;
	private final String pset_uncompressed_guid;
	private final int props_level;
	private final Model model;
	private final List<PsetProperty> properties = new ArrayList<>();
	private final boolean hasBlank_nodes;
	private final String uriBase;
	private boolean is_attribute = false;

	public PropertySet(String uriBase, Model model, String name, String pset_uncompressed_guid, int props_level,
			boolean hasBlank_nodes) {
		this.uriBase = uriBase;
		this.model = model;
		this.name = name;
		if (name.contains("_"))
			this.name = name.split("_")[1];
		this.pset_uncompressed_guid = pset_uncompressed_guid;
		this.props_level = props_level;
		this.hasBlank_nodes = hasBlank_nodes;
	}

	public PropertySet(String uriBase, Model model, String name, String pset_uncompressed_guid, int props_level,
			boolean hasBlank_nodes, boolean is_attribute) {
		this.uriBase = uriBase;
		this.model = model;
		this.name = name;
		if (name.contains("_"))
			this.name = name.split("_")[1];
		this.pset_uncompressed_guid = pset_uncompressed_guid;
		this.props_level = props_level;
		this.hasBlank_nodes = hasBlank_nodes;
		this.is_attribute = is_attribute;
	}

	public void put(String key, RDFNode value) {
		map.put(toCamelCase(key), value);
	}

	private void writeOPM_Set() {
		Resource pset = model.createResource(this.uriBase + "pset_" + pset_uncompressed_guid);
		pset.addProperty(RDFS.label, name);
		if (is_attribute)
			pset.addProperty(RDF.type, LBD_NS.PROPS_NS.attribute_group);
		else
			pset.addProperty(RDF.type, LBD_NS.PROPS_NS.props);
		isWritten = true;
		for (String k : this.getMap().keySet()) {

			Resource property_resourse;
			if (this.hasBlank_nodes)
				property_resourse = pset.getModel().createResource();
			else
				property_resourse = pset.getModel().createResource(this.uriBase + k + "_" + pset_uncompressed_guid);
			if (is_attribute)
				property_resourse.addProperty(LBD_NS.PROPS_NS.partofAG, pset);
			else
				property_resourse.addProperty(LBD_NS.PROPS_NS.partofPset, pset);
			if (this.props_level == 3) {
				Resource state_resourse;
				if (this.hasBlank_nodes)
					state_resourse = pset.getModel().createResource();
				else
					state_resourse = pset.getModel().createResource(
							this.uriBase + "state_"+k +"_"+ pset_uncompressed_guid + "_" + System.currentTimeMillis());
				property_resourse.addProperty(OPM.hasState, state_resourse);

				LocalDateTime datetime = LocalDateTime.now();
				String time_string = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
				state_resourse.addProperty(RDF.type, OPM.currentState);
				state_resourse.addLiteral(OPM.generatedAtTime, time_string);
				state_resourse.addProperty(OPM.value, this.getMap().get(k));
			} else
				property_resourse.addProperty(OPM.value, this.getMap().get(k));

			Property p;
			if (name.equals("attributes"))
				p = pset.getModel().createProperty(LBD_NS.PROPS_NS.props_ns + toCamelCase(k) + "_attribute");
			else
				p = pset.getModel().createProperty(LBD_NS.PROPS_NS.props_ns + toCamelCase(name + " " + k));
			this.properties.add(new PsetProperty(p, property_resourse));
		}
	}

	public void connect(Resource r_org) {
		Resource r = this.model.createResource(r_org.getURI());
		if (this.props_level > 1) {
			if (!isWritten)
				writeOPM_Set();
			for (PsetProperty pp : this.properties) {
				r.addProperty(pp.p, pp.r);

			}
		} else {

			for (String k : this.getMap().keySet()) {
				Property property;
				if (name.equals("attributes"))
					property = r.getModel().createProperty(LBD_NS.PROPS_NS.props_ns + k + "_attribute");
				else
					property = r.getModel().createProperty(LBD_NS.PROPS_NS.props_ns + name + "_" + k + "_simple");
				r.addProperty(property, this.getMap().get(k));

			}

		}
	}

	public String toCamelCase(final String init) {
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

	public static void main(String[] args) {
		PropertySet pset = new PropertySet(null, null, "", null, 1, false);
		System.out.println(pset.toCamelCase("yksi kaksi"));
	}
}
