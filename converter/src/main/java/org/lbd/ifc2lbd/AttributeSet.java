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
import org.apache.jena.vocabulary.RDF;
import org.lbd.ifc2lbd.ns.LBD_NS;
import org.lbd.ifc2lbd.ns.OPM;
import org.lbd.ifc2lbd.utils.StringOperations;

/*
 *  Copyright (c) 2017,2018,2019.2020 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * A class where IFC attributes are collected from an IFC element
 * 
 *
 */
public class AttributeSet {
	private class PsetProperty {
		final Property p; // Jena RDF property
		final Resource r; // Jena RDF resource object

		public PsetProperty(Property p, Resource r) {
			super();
			this.p = p;
			this.r = r;
		}
	}

	private final String uriBase;
	private final Model lbd_model;
	private final int props_level;
	private final boolean hasBlank_nodes;

	private final Map<String, RDFNode> mapPnameValue = new HashMap<>();

	public AttributeSet(String uriBase, Model lbd_model, int props_level, boolean hasBlank_nodes) {
		this.uriBase = uriBase;
		this.lbd_model = lbd_model;
		this.props_level = props_level;
		this.hasBlank_nodes = hasBlank_nodes;
	}

	public void putAnameValue(String attribute_name, RDFNode value) {
		mapPnameValue.put(StringOperations.toCamelCase(attribute_name), value);
	}

	/**
	 * Adds property value property for an resource.
	 * 
	 * @param lbd_resource The Jena Resource in the model
	 * @param long_guid    The GUID of the elemet in the long form
	 */
	Set<String> hashes = new HashSet<>();

	public void connect(Resource lbd_resource, String long_guid) {
		switch (this.props_level) {
		case 1:
		default:
			for (String pname : this.mapPnameValue.keySet()) {
				Property property;
				property = this.lbd_model.createProperty(LBD_NS.PROPS_NS.props_ns + pname + "_attribute_simple");
				lbd_resource.addProperty(property, this.mapPnameValue.get(pname));
			}
			break;
		case 2:
		case 3:
			if (hashes.add(long_guid)) {
				List<PsetProperty> properties = writeOPM_Set(long_guid);
				for (PsetProperty pp : properties) {
					if (!this.lbd_model.listStatements(lbd_resource, pp.p, pp.r).hasNext())
						lbd_resource.addProperty(pp.p, pp.r);
				}
			}
			break;
		}
	}

	private List<PsetProperty> writeOPM_Set(String long_guid) {
		List<PsetProperty> properties = new ArrayList<>();
		for (String k : this.mapPnameValue.keySet()) {
			Resource property_resource;
			if (this.hasBlank_nodes)
				property_resource =this.lbd_model.createResource();
			else
				property_resource = this.lbd_model.createResource(this.uriBase + k + "_" + long_guid);

			if (this.props_level == 3) {
				Resource state_resourse;
				if (this.hasBlank_nodes)
					state_resourse = this.lbd_model.createResource();
				else
					state_resourse = this.lbd_model.createResource(
							this.uriBase + "state_" + k + "_" + long_guid + "_" + System.currentTimeMillis());
				property_resource.addProperty(OPM.hasState, state_resourse);

				LocalDateTime datetime = LocalDateTime.now();
				String time_string = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
				state_resourse.addProperty(RDF.type, OPM.currentState);
				state_resourse.addLiteral(OPM.generatedAtTime, time_string);
				state_resourse.addProperty(OPM.value, this.mapPnameValue.get(k));
			} else
				property_resource.addProperty(OPM.value, this.mapPnameValue.get(k));

			Property p;
			p = this.lbd_model.createProperty(LBD_NS.PROPS_NS.props_ns + StringOperations.toCamelCase(k));
			properties.add(new PsetProperty(p, property_resource));
		}
		return properties;
	}

}
