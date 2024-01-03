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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.linkedbuildingdata.ifc2lbd.core.utils.StringOperations;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;
import org.linkedbuildingdata.ifc2lbd.namespace.SMLS;
import org.linkedbuildingdata.ifc2lbd.namespace.UNIT;

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
    private final Map<String, String> unitmap;

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
    private final Map<String, RDFNode> mapPnameType = new HashMap<>();

    public AttributeSet(String uriBase, Model lbd_model, int props_level, boolean hasBlank_nodes, Map<String, String> unitmap) {
        this.unitmap = unitmap;
        this.uriBase = uriBase;
        this.lbd_model = lbd_model;
        this.props_level = props_level;
        this.hasBlank_nodes = hasBlank_nodes;
    }

    public void putAnameValue(String attribute_name, RDFNode value, Optional<Resource> atype) {
        mapPnameValue.put(StringOperations.toCamelCase(attribute_name), value);
        if (atype.isPresent()) {
            mapPnameType.put(StringOperations.toCamelCase(attribute_name), atype.get());
        }
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
                    property = this.lbd_model.createProperty(PROPS.props_ns + pname + "_attribute_simple");
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

    private List<PsetProperty> writeOPM_Set(String long_guid) {
        List<PsetProperty> properties = new ArrayList<>();
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
                    state_resourse = this.lbd_model.createResource(this.uriBase + "state_" + pname + "_" + long_guid + "_" + System.currentTimeMillis());
               // https://w3c-lbd-cg.github.io/opm/assets/states.svg
                property_resource.addProperty(OPM.hasPropertyState, state_resourse);

                LocalDateTime datetime = LocalDateTime.now();
                String time_string = datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                state_resourse.addProperty(RDF.type, OPM.currentPropertyState);
                state_resourse.addLiteral(OPM.generatedAtTime, time_string);
                state_resourse.addProperty(OPM.value, this.mapPnameValue.get(pname));
                addUnit(state_resourse, pname);

            } else {
                property_resource.addProperty(OPM.value, this.mapPnameValue.get(pname));
                addUnit(property_resource, pname);
            }

            Property p;
            p = this.lbd_model.createProperty(PROPS.props_ns + StringOperations.toCamelCase(pname));
            properties.add(new PsetProperty(p, property_resource));
        }
        return properties;
    }

    private void addUnit(Resource lbd_resource, String pname) {

        RDFNode ifc_measurement_type = this.mapPnameType.get(pname);
        if (ifc_measurement_type != null) {
            String unit = ifc_measurement_type.asResource().getLocalName().toLowerCase();
            if (unit.startsWith("ifc"))
                unit = unit.substring(3);
            if (unit.startsWith("positive"))
                unit = unit.substring("positive".length());
            if (unit.endsWith("measure"))
                unit = unit.substring(0, unit.length() - "measure".length());
            String si_unit = this.unitmap.get(unit);
            if (si_unit != null) {
                if (si_unit.equals("METRE")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.METER);
                } else if (si_unit.equals("SQUARE_METRE")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.SQUARE_METRE);
                } else if (si_unit.equals("CUBIC_METRE")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.CUBIC_METRE);
                } else if (si_unit.equals("MILLI METRE")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.MILLI_METER);
                } else if (si_unit.equals("MILLI SQUARE_METRE")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.SQUARE_MILLI_METRE);
                } else if (si_unit.equals("MILLI CUBIC_METRE")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.CUBIC_MILLI_METER);
                } else if (si_unit.equals("RADIAN")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.RADIAN);
                }
            } else {
                if (unit.equals("length")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.MILLI_METER); // Default
                                                                           // named
                                                                           // in:
                    // https://standards.buildingsmart.org/IFC/RELEASE/IFC2x3/TC1/HTML/ifcmeasureresource/lexical/ifclengthmeasure.htm
                } else if (unit.equals("area")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.SQUARE_METRE); // default
                                                                            // named
                                                                            // in:
                    // https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/schema/ifcmeasureresource/lexical/ifcareameasure.htm
                } else if (unit.equals("volume")) {
                    lbd_resource.addProperty(SMLS.unit, UNIT.CUBIC_METRE); // default
                                                                           // named
                                                                           // in:
                    // https://standards.buildingsmart.org/IFC/RELEASE/IFC2x3/TC1/HTML/ifcmeasureresource/lexical/ifcvolumemeasure.htm
                }

            }
        }

    }
    
    public Set<String> getPropertynames() {

    	return mapPnameType.keySet();
    }
}
