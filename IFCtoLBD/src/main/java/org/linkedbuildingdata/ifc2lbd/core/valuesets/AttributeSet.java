package org.linkedbuildingdata.ifc2lbd.core.valuesets;

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
import org.linkedbuildingdata.ifc2lbd.UnitResolver;
import org.linkedbuildingdata.ifc2lbd.core.utils.StringOperations;
import org.linkedbuildingdata.ifc2lbd.namespace.LBD;
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
 * A class where IFC attributes are collected from an IFC element
 * 
 *
 */
public class AttributeSet {
	private final UnitResolver unitResolver;
    private final Map<String, String> property_replace_map;  // allows users to replace default properties
    private String default_property_namespace;

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
    private final int props_level;
    private final boolean hasBlank_nodes;
    private final boolean hasSimplified_properties;

    private final Map<String, RDFNode> mapPnameValue = new HashMap<>();
    private final Map<String, RDFNode> mapPnameType = new HashMap<>();

    public AttributeSet(String uriBase, Model lbd_model, int props_level, boolean hasBlank_nodes, Map<String, String> unitmap,boolean hasSimplified_properties,Map<String, String> property_replace_map) {
		this(uriBase, lbd_model, props_level, hasBlank_nodes, UnitResolver.fromLegacyProjectUnits(unitmap),
				hasSimplified_properties, property_replace_map);
	}

	public AttributeSet(String uriBase, Model lbd_model, int props_level, boolean hasBlank_nodes,
			UnitResolver unitResolver, boolean hasSimplified_properties, Map<String, String> property_replace_map) {
		this.unitResolver = unitResolver;
        this.uriBase = uriBase;
        this.lbd_model = lbd_model;
        this.props_level = props_level;
        this.hasBlank_nodes = hasBlank_nodes;
        this.hasSimplified_properties = hasSimplified_properties;
        if(this.hasSimplified_properties)
            this.default_property_namespace=LBD.ns;
         else
        	this.default_property_namespace=PROPS.ns;
        this.property_replace_map=property_replace_map;
    }
    
    public AttributeSet(String uriBase, Model lbd_model, int props_level, boolean hasBlank_nodes, Map<String, String> unitmap,boolean hasSimplified_properties,Map<String, String> property_replace_map,String default_property_namebase) {
		this(uriBase, lbd_model, props_level, hasBlank_nodes, UnitResolver.fromLegacyProjectUnits(unitmap),
				hasSimplified_properties, property_replace_map, default_property_namebase);
	}

	public AttributeSet(String uriBase, Model lbd_model, int props_level, boolean hasBlank_nodes,
			UnitResolver unitResolver, boolean hasSimplified_properties, Map<String, String> property_replace_map,
			String default_property_namebase) {
		this.unitResolver = unitResolver;
        this.uriBase = uriBase;
        this.lbd_model = lbd_model;
        this.props_level = props_level;
        this.hasBlank_nodes = hasBlank_nodes;
        this.hasSimplified_properties = hasSimplified_properties;
        
        this.property_replace_map=property_replace_map;
        this.setDefault_property_namespace(default_property_namebase);
    }

	    public void putAnameValue(String attribute_name, RDFNode value, Optional<Resource> atype) {
	    	if (value.isLiteral()) {
				Literal literal_value = createLiteralPreservingMetadata(value.asLiteral());
				mapPnameValue.put(StringOperations.toCamelCase(attribute_name), literal_value);
			} else
	          mapPnameValue.put(StringOperations.toCamelCase(attribute_name), value);
	        if (atype.isPresent()) {
	            mapPnameType.put(StringOperations.toCamelCase(attribute_name), atype.get());
	        }
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

    /**
     * Adds property value property for an resource.
     * 
     * @param lbd_resource
     *            The Jena Resource in the model
     * @param long_guid
     *            The GUID of the elemet in the long form
     */
    Set<String> hashes = new HashSet<>();

    public void connect(Resource lbd_resource, String long_guid) {
        switch (this.props_level) {
            case 1:
            default:

            for (String  pname : this.mapPnameValue.keySet()) {
                Property property;
                if(pname.equals("nameIfcRoot"))
                	property = RDFS.label;
                else
                {
                	if(this.hasSimplified_properties)
                		property = this.lbd_model.createProperty(property_replace(this.default_property_namespace + StringOperations.toCamelCase(pname.split("Ifc")[0])));
                	else
                       property = this.lbd_model.createProperty(property_replace(this.default_property_namespace + StringOperations.toCamelCase(pname) + "_attribute_simple"));
                	this.lbd_model.add(property, RDF.type, OWL.DatatypeProperty);
                    this.lbd_model.add(property, RDFS.comment, "IFC standard attribute "+pname);
					addIfcDatatype(property, pname);

                }
                // No blank node etc is created, so no units expressed here
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
    static long state_resourse_counter = 0;
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

            if (this.props_level == 3) {
                Resource state_resourse;
                if (this.hasBlank_nodes)
                    state_resourse = this.lbd_model.createResource();
                else
                    state_resourse = this.lbd_model.createResource(this.uriBase + "state_" + pname + "_" + long_guid + "_a" + stableValueId(this.mapPnameValue.get(pname)));
               // https://w3c-lbd-cg.github.io/opm/assets/states.svg
                property_resource.addProperty(OPM.hasPropertyState, state_resourse);


                String time_string = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                state_resourse.addProperty(RDF.type, OPM.currentPropertyState);
                state_resourse.addLiteral(OPM.generatedAtTime, time_string);
                state_resourse.addProperty(OPM.value, this.mapPnameValue.get(pname));
				addIfcDatatype(state_resourse, pname);
                addUnit(state_resourse, pname);

                
            } else {
                property_resource.addProperty(OPM.value, this.mapPnameValue.get(pname));
				addIfcDatatype(property_resource, pname);
                addUnit(property_resource, pname);
            }

            Property property;
            if(this.hasSimplified_properties)
               property = this.lbd_model.createProperty(property_replace(this.default_property_namespace + StringOperations.toCamelCase(pname.split("Ifc")[0])));
            else
               property = this.lbd_model.createProperty(property_replace(this.default_property_namespace + StringOperations.toCamelCase(pname)));
            this.lbd_model.add(property, RDF.type, OWL.ObjectProperty);
            this.lbd_model.add(property, RDFS.comment, "IFC standard attribute "+pname);

            properties.add(new PsetProperty(property, property_resource));
        }
        return properties;
    }

    private static String stableValueId(RDFNode value) {
        return Integer.toUnsignedString(java.util.Objects.toString(value, "").hashCode(), 36);
    }

    private void addUnit(Resource lbd_resource, String pname) {
		UnitResolver.write(this.lbd_model, lbd_resource,
				this.unitResolver.resolve(null, this.mapPnameType.get(pname)));
    }

	private void addIfcDatatype(Resource owner, String pname) {
		RDFNode sourceType = this.mapPnameType.get(pname);
		if (sourceType != null)
			owner.addProperty(this.lbd_model.createProperty(UnitResolver.META + "sourceIFCType"), sourceType);
	}

    private String property_replace(String property)
    {    	
    	return this.property_replace_map.getOrDefault(property,property);
    }
    
    public Set<String> getPropertynames() {

    	return mapPnameType.keySet();
    }

	public void setDefault_property_namespace(String default_property_namespace) {
		this.default_property_namespace = default_property_namespace;
	}
    
    
}
