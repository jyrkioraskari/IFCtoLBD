	package org.linkedbuildingdata.ifc2lbd.core.valuesets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.core.utils.StringOperations;
import org.linkedbuildingdata.ifc2lbd.UnitResolver;
import org.linkedbuildingdata.ifc2lbd.namespace.LBD;
import org.linkedbuildingdata.ifc2lbd.namespace.BSDD;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

/*
 *  Copyright (c) 2017,2018,2019.2020, 2024 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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
 * A class where IFC PropertySet is collected from the IFC file
 * 
 *
 */
public class PropertySet {
	private boolean isActive = true;
	private final UnitResolver unitResolver;
	private Map<String, String> property_replace_map; // allows users to replace default properties

	private static class PsetProperty {
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
	private String propertyset_name;

	private final int props_level;
	private final boolean hasBlank_nodes;
	private boolean hasSimplified_properties;
	private boolean propertiesAsPropertySets;

	private final Map<String, RDFNode> mapPnameValue = new HashMap<>();
	private final Map<String, String> originalPropertyNames = new HashMap<>();
	private final Map<String, RDFNode> mapPnameType = new HashMap<>();
	private final Map<String, RDFNode> mapPnameIfcDataType = new HashMap<>();
	private final Map<String, RDFNode> mapPnameUnit = new HashMap<>();
	private final boolean hasUnits;
	private static long pset_counter = 0;
	private long pset_inx = 0;
	private boolean done = false;

	public PropertySet(String uriBase, Model lbd_model, Model ontology_model, String propertyset_name, int props_level,
			boolean hasBlank_nodes, Map<String, String> unitmap, boolean hasUnits) {
		this(uriBase, lbd_model, ontology_model, propertyset_name, props_level, hasBlank_nodes,
				UnitResolver.fromLegacyProjectUnits(unitmap), hasUnits);
	}

	public PropertySet(String uriBase, Model lbd_model, Model ontology_model, String propertyset_name, int props_level,
			boolean hasBlank_nodes, UnitResolver unitResolver, boolean hasUnits) {
		this.unitResolver = unitResolver;
		this.uriBase = uriBase;
		this.lbd_model = lbd_model;
		this.propertyset_name = propertyset_name;
		this.props_level = props_level;
		this.hasBlank_nodes = hasBlank_nodes;
		this.hasUnits = hasUnits;
		this.hasSimplified_properties = false;
		this.propertiesAsPropertySets = false;
		PropertySet.pset_counter++;
		this.pset_inx = PropertySet.pset_counter;
	}

	public void putPnameValue(String property_name, RDFNode value) {
		String normalizedName = StringOperations.toCamelCase(property_name);
		originalPropertyNames.put(normalizedName, property_name);
		if (value.isLiteral()) {
			Literal literal_value = createLiteralPreservingMetadata(value.asLiteral());
			mapPnameValue.put(normalizedName, literal_value);
		} else
			mapPnameValue.put(normalizedName, value);
	}

	private Literal createLiteralPreservingMetadata(Literal original) {
		String lexicalForm = StringOperations.handleUnicode(original.getLexicalForm());
		String language = original.getLanguage();
		if (language != null && !language.isEmpty())
			return this.lbd_model.createLiteral(lexicalForm, language);
		if (original.getDatatype() != null)
			return this.lbd_model.createTypedLiteral(lexicalForm, original.getDatatype());
		return this.lbd_model.createLiteral(lexicalForm);
	}

	public void putPnameType(String property_name, RDFNode type) {
		mapPnameType.put(StringOperations.toCamelCase(property_name), type);
	}

	public void putPnameUnit(String property_name, RDFNode unit) {
		mapPnameUnit.put(StringOperations.toCamelCase(property_name), unit);
	}

	/** Stores a future IfcDataType annotation without replacing the source RDF/IFC type. */
	public void putPnameIfcDataType(String property_name, RDFNode ifcDataType) {
		mapPnameIfcDataType.put(StringOperations.toCamelCase(property_name), ifcDataType);
	}

	/**
	 * Adds property value property for an resource.
	 * 
	 * @param lbd_resource   The Jena Resource in the model
	 * @param extracted_guid The GUID of the elemet in the long form
	 */
	Set<String> hashes = new HashSet<>();
	private boolean pksetclasses = false;

	public void connect(Resource lbd_resource, String long_guid) {
		// System.out.println("connect: "+this.getPropertyset_name()+" -
		// "+lbd_resource.getLocalName());
		Resource to_connect = lbd_resource;
		if (pksetclasses) {
			to_connect = this.lbd_model
					.createResource(this.uriBase + "pset_" + this.propertyset_name + "_" + this.pset_inx);
			Resource bsddClass = BSDD.propertySet(this.lbd_model, this.propertyset_name).orElse(null);
			if (bsddClass != null)
				to_connect.addProperty(RDF.type, bsddClass);
			Property property = this.lbd_model.createProperty(LBD.ns + "has" + this.propertyset_name.replace(" ", "_"));
			lbd_resource.addProperty(property, to_connect);
			if (this.done) {
				// Already done pset, just connect
				return;
			}
			this.done = true;
		}

		if (!this.isActive)
			return;
		if (this.propertiesAsPropertySets) {
			writePropertySet(lbd_resource, long_guid);
			return;
		}
		if (this.mapPnameValue.keySet().size() > 0)
			switch (this.props_level) {
			case 1:
			default:
				for (String pname : this.mapPnameValue.keySet()) {
					Property property;
					if (this.hasSimplified_properties)
						property = this.lbd_model.createProperty(
								property_replace(PROPS.ns + StringOperations.toCamelCase(pname.split(" ")[0])));
					else
						property = this.lbd_model.createProperty(
								property_replace(PROPS.ns + StringOperations.toCamelCase(pname) + "_property_simple"));
					this.lbd_model.add(property, RDF.type, OWL.DatatypeProperty);
					this.lbd_model.add(property, RDFS.comment,
							"IFC property set " + this.propertyset_name + " property " + pname);
					addIfcDatatype(property, pname);

					if (!this.mapPnameValue.get(pname).toString().contains("IfcPropertySingleValue"))
						to_connect.addProperty(property, this.mapPnameValue.get(pname));
					else
						System.err.println("Odd value: " + this.mapPnameValue.get(pname));
				}
				break;
			case 2:
			case 3:
				if (hashes.add(long_guid)) {
					List<PsetProperty> properties = writeOPM_Set(long_guid);
					for (PsetProperty pp : properties) {
						if (!this.lbd_model.listStatements(to_connect, pp.p, pp.r).hasNext()) {
							lbd_resource.addProperty(pp.p, pp.r);
						}
					}
				}
				break;
			}
	}

	static private long state_resourse_counter = 0;

	private void writePropertySet(Resource lbdResource, String longGuid) {
		if (this.mapPnameValue.isEmpty())
			return;

		BSDD.addNameSpaces(this.lbd_model);
		String psetId = stableId(longGuid + "\u0000" + this.propertyset_name);
		Resource psetResource = this.lbd_model.createResource(this.uriBase + "cps_" + psetId);
		lbdResource.addProperty(BSDD.hasPropertySet, psetResource);
		if (!this.hashes.add(longGuid))
			return;
		psetResource.addProperty(RDF.type, BSDD.propertySet);
		BSDD.propertySet(this.lbd_model, this.propertyset_name)
				.ifPresent(bsddClass -> psetResource.addProperty(RDF.type, bsddClass));
		psetResource.addProperty(RDFS.label, this.propertyset_name);

		Instant generatedAt = Instant.now();
		for (String pname : this.mapPnameValue.keySet()) {
			String originalPname = this.originalPropertyNames.getOrDefault(pname, pname);
			String propertyId = stableId(longGuid + "\u0000" + this.propertyset_name + "\u0000" + pname);
			Resource propertyResource = this.lbd_model.createResource(this.uriBase + "cp_" + propertyId);
			Resource stateResource = this.lbd_model.createResource(this.uriBase + "cs_" + propertyId);

			psetResource.addProperty(BSDD.containsProperty, propertyResource);
			BSDD.property(this.lbd_model, this.propertyset_name, originalPname)
					.ifPresent(bsddProperty -> propertyResource.addProperty(RDF.type, bsddProperty));
			propertyResource.addProperty(RDF.type, OPM.property);
			propertyResource.addProperty(RDFS.label, this.propertyset_name + ":" + pname);
			propertyResource.addProperty(OPM.hasPropertyState, stateResource);

			stateResource.addProperty(RDF.type, OPM.currentPropertyState);
			stateResource.addLiteral(OPM.generatedAtTime,
					this.lbd_model.createTypedLiteral(generatedAt.toString(),
							"http://www.w3.org/2001/XMLSchema#dateTime"));
			stateResource.addProperty(OPM.value, this.mapPnameValue.get(pname));
			addIfcDatatype(stateResource, pname);
			if (this.hasUnits)
				addUnit(stateResource, pname);
		}
	}

	private static String stableId(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder id = new StringBuilder(16);
			for (int i = 0; i < 8; i++)
				id.append(String.format("%02x", digest[i]));
			return id.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	private List<PsetProperty> writeOPM_Set(String long_guid) {
		List<PsetProperty> properties = new ArrayList<>();
		LocalDateTime datetime = LocalDateTime.now();
		for (String pname : this.mapPnameValue.keySet()) {
			Resource property_resource;
			if (this.hasBlank_nodes)
				property_resource = this.lbd_model.createResource();
			else {
				property_resource = this.lbd_model.createResource(this.uriBase + pname + "_" + long_guid);
				property_resource.addProperty(RDF.type, OPM.property);
			}

			String originalPname = this.originalPropertyNames.getOrDefault(pname, pname);
			BSDD.property(this.lbd_model, this.propertyset_name, originalPname)
					.ifPresent(reference -> property_resource.addProperty(RDFS.seeAlso, reference));

			// Just the complete name
			property_resource.addProperty(RDFS.label, this.propertyset_name + ":" + pname);

			if (this.props_level == 3) {
				Resource state_resourse;
				if (this.hasBlank_nodes)
					state_resourse = this.lbd_model.createResource();
				else
					state_resourse = this.lbd_model.createResource(this.uriBase + "state_" + pname + "_" + long_guid
							+ "_p" + stableValueId(this.mapPnameValue.get(pname)));
				// https://w3c-lbd-cg.github.io/opm/assets/states.svg
				property_resource.addProperty(OPM.hasPropertyState, state_resourse);

				String time_string = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
				state_resourse.addProperty(RDF.type, OPM.currentPropertyState);
				state_resourse.addLiteral(OPM.generatedAtTime, time_string);
				state_resourse.addProperty(OPM.value, this.mapPnameValue.get(pname));
				addIfcDatatype(state_resourse, pname);
				if (this.hasUnits)
					addUnit(state_resourse, pname);

			} else {
				property_resource.addProperty(OPM.value, this.mapPnameValue.get(pname));
				addIfcDatatype(property_resource, pname);
				if (this.hasUnits)
					addUnit(property_resource, pname);
			}

			Property property;
			if (this.hasSimplified_properties)
				property = this.lbd_model
						.createProperty(property_replace(PROPS.ns + StringOperations.toCamelCase(pname.split(" ")[0])));
			else
				property = this.lbd_model
						.createProperty(property_replace(PROPS.ns + StringOperations.toCamelCase(pname)));
			properties.add(new PsetProperty(property, property_resource));

			this.lbd_model.add(property, RDF.type, OWL.ObjectProperty);
			this.lbd_model.add(property, RDFS.comment,
					"IFC property set " + this.propertyset_name + " property " + pname);

		}
		return properties;
	}

	private void addUnit(Resource lbd_resource, String pname) {
		RDFNode explicit = this.mapPnameUnit.get(pname);
		Resource explicitResource = explicit != null && explicit.isResource() ? explicit.asResource() : null;
		UnitResolver.write(this.lbd_model, lbd_resource,
				this.unitResolver.resolve(explicitResource, this.mapPnameType.get(pname)));
	}

	private void addIfcDatatype(Resource owner, String pname) {
		RDFNode sourceType = this.mapPnameType.get(pname);
		if (sourceType != null)
			owner.addProperty(this.lbd_model.createProperty(UnitResolver.META + "sourceIFCType"), sourceType);
		RDFNode futureType = this.mapPnameIfcDataType.get(pname);
		if (futureType != null)
			owner.addProperty(this.lbd_model.createProperty(UnitResolver.META + "ifcDataType"), futureType);
	}


	public Optional<Boolean> isExternal() {

		RDFNode val = this.mapPnameValue.get("isExternal");

		if (val == null)
			return Optional.empty();
		if (!val.isLiteral())
			return Optional.empty();
		if (val.asLiteral().getValue().equals(true))
			return Optional.of(true);
		return Optional.of(false);
	}

	public Set<String> getPropertynames() {

		return mapPnameType.keySet();
	}

	public String getPropertyset_name() {
		return propertyset_name;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public void setHasSimplified_properties(boolean hasSimplified_properties) {
		this.hasSimplified_properties = hasSimplified_properties;
	}

	private static String stableValueId(RDFNode value) {
		return Integer.toUnsignedString(java.util.Objects.toString(value, "").hashCode(), 36);
	}

	public void setPropertiesAsPropertySets(boolean propertiesAsPropertySets) {
		this.propertiesAsPropertySets = propertiesAsPropertySets;
	}

	public void resetConversionState() {
		this.hashes.clear();
		this.done = false;
	}

	public void setProperty_replace_map(Map<String, String> property_replace_map) {
		this.property_replace_map = property_replace_map;
	}

	private String property_replace(String property) {
		if (property_replace_map == null)
			return property;

		return this.property_replace_map.getOrDefault(property, property);
	}
}
