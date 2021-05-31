package com.buildingsmart.tech.ifcowl;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.buildingsmart.tech.ifcowl.vo.AttributeVO;
import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.NamedIndividualVO;
import com.buildingsmart.tech.ifcowl.vo.PrimaryTypeVO;
import com.buildingsmart.tech.ifcowl.vo.PropertyVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;

import fi.ni.rdf.Namespace;

/*
 * OWLWriter writes .ttl files representing OWL ontologies, thereby relying on the in-memory EXPRESS model that is parsed by the ExpressReader class.
 * 
 * The usage:
 * OWLWriter ow = new OWLWriter(expressSchemaName, entities, types, siblings);
 * 
 *  - outputOWL() - writes the OWL ontology in TTL files in appropriate 'schema' package
 */

/*
 * Copyright 2016 Pieter Pauwels, Ghent University; Jyrki Oraskari, Aalto University; Lewis John McGibbney, Apache
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

public class OWLWriter {

    private String expressSchemaName;

    private Map<String, EntityVO> entities = new HashMap<>();
    private Map<String, PropertyVO> properties = new HashMap<>();
    private Map<String, TypeVO> types = new HashMap<>();
    private Map<String, Set<String>> siblings = new HashMap<>();
    private List<NamedIndividualVO> enumIndividuals = new ArrayList<>();
    private List<String> listPropertiesOutput = new ArrayList<>();

    public OWLWriter() {
        // default constructor
    }

    public OWLWriter(String expressSchemaName, Map<String, EntityVO> entities, Map<String, TypeVO> types, Map<String, Set<String>> siblings, List<NamedIndividualVO> enumIndividuals,
                    Map<String, PropertyVO> properties) {
        this.expressSchemaName = expressSchemaName;
        this.entities = entities;
        this.types = types;
        this.siblings = siblings;
        this.enumIndividuals = enumIndividuals;
        this.properties = properties;
    }

    public void outputOWL(String filePath) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(filePath)); //includes .ttl extension
            out.write("@base <" + Namespace.IFC + "> .\r\n");
            out.write("@prefix : <" + Namespace.IFC + "#> .\r\n");
            out.write("@prefix ifc: <" + Namespace.IFC + "#> .\r\n");
            out.write(getOwl_header());

            writeNamedIndividuals(out);

            Iterator<Entry<String, TypeVO>> itType = types.entrySet().iterator();
            writeTypesToOWL(itType, out);

            Iterator<Entry<String, EntityVO>> it = entities.entrySet().iterator();
            writeEntitiesToOWL(it, out);

            for (Map.Entry<String, PropertyVO> entry : properties.entrySet()) {
                PropertyVO property = entry.getValue();
                outputOWLproperty(out, property);
            }

            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void outputExpressOWL(String filePath) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(filePath)); //includes .ttl
            out.write("@base <" + Namespace.EXPRESS + "> .\r\n");
            out.write("@prefix : <" + Namespace.EXPRESS + "#> .\r\n");
            out.write("@prefix expr: <" + Namespace.EXPRESS + "#> .\r\n");
            out.write(getExpressOwlHeader());

            writePrimaryTypes(out);
            writeHelperClasses(out);

            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void outputOWLproperty(BufferedWriter out, PropertyVO property) {
        try {
            if (property.isList() || property.isArray()) {
                out.write("ifc:" + property.getLowerCaseName() + "\r\n");
                out.write("\trdfs:label \"" + property.getOriginalName()// getOriginalNameLowerCase()
                                + "\" ;\r\n");
                out.write("\trdfs:domain ifc:" + property.getDomain().getName() + " ;\r\n");

                // range
                if (property.isListOfList())
                    out.write("\trdfs:range " + property.getRangeNS() + ":" + property.getRange() + "_List_List ;\r\n");
                else if (property.isSet())
                    out.write("\trdfs:range " + property.getRangeNS() + ":" + property.getRange() + " ;\r\n");
                else
                    out.write("\trdfs:range " + property.getRangeNS() + ":" + property.getRange() + "_List ;\r\n");

                // inverse
                if (property.getInverseProperty() != null)
                    out.write("\towl:inverseOf ifc:" + property.getInverseProperty().getLowerCaseName() + " ;\r\n");

                // typesetting
                if (!property.isSet())
                    out.write("\trdf:type owl:FunctionalProperty, owl:ObjectProperty .\r\n\r\n");
                else
                    out.write("\trdf:type owl:ObjectProperty .\r\n\r\n");

                if (!property.getRangeNS().equalsIgnoreCase("expr")) {
                    // write List range if necessary
                    if (!property.isSet()) {
                        if (property.isListOfList()) {
                            if (!listPropertiesOutput.contains(property.getRange() + "_List")) {
                                // property not already contained in resulting
                                // OWL file
                                // (.TTL) -> no need to write additional
                                // property

                                listPropertiesOutput.add(property.getRange() + "_List");

                                out.write(property.getRangeNS() + ":" + property.getRange() + "_List_EmptyList" + "\r\n");
                                out.write("\trdf:type owl:Class ;" + "\r\n");
                                out.write("\trdfs:subClassOf list:EmptyList, " + property.getRangeNS() + ":" + property.getRange() + "_List_List" + " ." + "\r\n" + "\r\n");

                                out.write(property.getRangeNS() + ":" + property.getRange() + "_List_List" + "\r\n");
                                out.write("\trdf:type owl:Class ;" + "\r\n");
                                out.write("\trdfs:subClassOf list:OWLList ;" + "\r\n");

                                out.write("\trdfs:subClassOf" + "\r\n");
                                out.write("\t\t[" + "\r\n");
                                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                                out.write("\t\t\towl:onProperty list:hasContents ;" + "\r\n");
                                out.write("\t\t\towl:allValuesFrom " + property.getRangeNS() + ":" + property.getRange() + "_List" + "\r\n");
                                out.write("\t\t] ;" + "\r\n");

                                // out.write("\trdfs:subClassOf" + "\r\n");
                                // out.write("\t\t[" + "\r\n");
                                // out.write("\t\t\trdf:type owl:Restriction ;"
                                // + "\r\n");
                                // out.write("\t\t\towl:onProperty list:hasContents ;"
                                // + "\r\n");
                                // out.write("\t\t\towl:someValuesFrom "+property.getRangeNS()+":"
                                // + property.getRange() + "_List" + "\r\n");
                                // out.write("\t\t] ;" + "\r\n");

                                out.write("\trdfs:subClassOf" + "\r\n");
                                out.write("\t\t[" + "\r\n");
                                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                                out.write("\t\t\towl:onProperty list:isFollowedBy ;" + "\r\n");
                                out.write("\t\t\towl:allValuesFrom " + property.getRangeNS() + ":" + property.getRange() + "_List_List\r\n");
                                out.write("\t\t] ;" + "\r\n");

                                out.write("\trdfs:subClassOf" + "\r\n");
                                out.write("\t\t[" + "\r\n");
                                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                                out.write("\t\t\towl:onProperty list:hasNext ;" + "\r\n");
                                out.write("\t\t\towl:allValuesFrom " + property.getRangeNS() + ":" + property.getRange() + "_List_List\r\n");
                                out.write("\t\t] ." + "\r\n\r\n");
                            }
                        }

                        if (!listPropertiesOutput.contains(property.getRange())) {
                            // property not already contained in resulting OWL
                            // file
                            // (.TTL)
                            // -> no need to write additional property

                            listPropertiesOutput.add(property.getRange());

                            String ns = "ifc";
                            // check namespace of range
                            if (property.getRange().equalsIgnoreCase("NUMBER") || property.getRange().equalsIgnoreCase("REAL") || property.getRange().equalsIgnoreCase("INTEGER")
                                            || property.getRange().equalsIgnoreCase("LOGICAL") || property.getRange().equalsIgnoreCase("BOOLEAN") || property.getRange().equalsIgnoreCase("STRING")
                                            || property.getRange().equalsIgnoreCase("BINARY"))
                                ns = "expr";

                            out.write(property.getRangeNS() + ":" + property.getRange() + "_EmptyList" + "\r\n");
                            out.write("\trdf:type owl:Class ;" + "\r\n");
                            out.write("\trdfs:subClassOf list:EmptyList, " + property.getRangeNS() + ":" + property.getRange() + "_List" + " ." + "\r\n" + "\r\n");

                            out.write(property.getRangeNS() + ":" + property.getRange() + "_List" + "\r\n");
                            out.write("\trdf:type owl:Class ;" + "\r\n");
                            out.write("\trdfs:subClassOf list:OWLList ;" + "\r\n");

                            // out.write("\trdfs:subClassOf" + "\r\n");
                            // out.write("\t\t[" + "\r\n");
                            // out.write("\t\t\trdf:type owl:Restriction ;" +
                            // "\r\n");
                            // out.write("\t\t\towl:onProperty list:hasContents ;"
                            // + "\r\n");
                            // out.write("\t\t\towl:someValuesFrom "
                            // + ns + ":"
                            // + property.getRange() + "\r\n");
                            // out.write("\t\t] ;" + "\r\n");

                            out.write("\trdfs:subClassOf" + "\r\n");
                            out.write("\t\t[" + "\r\n");
                            out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                            out.write("\t\t\towl:onProperty list:hasContents ;" + "\r\n");
                            out.write("\t\t\towl:allValuesFrom " + ns + ":" + property.getRange() + "\r\n");
                            out.write("\t\t] ;" + "\r\n");

                            out.write("\trdfs:subClassOf" + "\r\n");
                            out.write("\t\t[" + "\r\n");
                            out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                            out.write("\t\t\towl:onProperty list:isFollowedBy ;" + "\r\n");
                            out.write("\t\t\towl:allValuesFrom " + property.getRangeNS() + ":" + property.getRange() + "_List\r\n");
                            out.write("\t\t] ;" + "\r\n");

                            out.write("\trdfs:subClassOf" + "\r\n");
                            out.write("\t\t[" + "\r\n");
                            out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                            out.write("\t\t\towl:onProperty list:hasNext ;" + "\r\n");
                            out.write("\t\t\towl:allValuesFrom " + property.getRangeNS() + ":" + property.getRange() + "_List\r\n");
                            out.write("\t\t] ." + "\r\n\r\n");
                        }
                    } else {
                        // do nothing additional for a set
                    }

                    return;
                }

            } else {
                out.write("ifc:" + property.getLowerCaseName() + "\r\n");
                out.write("\trdfs:label \"" + property.getOriginalName()// getOriginalNameLowerCase()
                                + "\" ;\r\n");
                out.write("\trdfs:domain ifc:" + property.getDomain().getName() + " ;\r\n");
                out.write("\trdfs:range " + property.getRangeNS() + ":" + property.getRange() + " ;\r\n");
                if (property.getInverseProperty() != null)
                    out.write("\towl:inverseOf ifc:" + property.getInverseProperty().getLowerCaseName() + " ;\r\n");
                if (property.isSet() && property.getMaxCardinality() != 1) {
                    // System.out.println("Set Prop found : " +
                    // property.getName()
                    // + " - " + property.getMinCardinality() + " - "
                    // + property.getMaxCardinality());
                    out.write("\trdf:type owl:ObjectProperty .\r\n\r\n");
                } else
                    out.write("\trdf:type owl:FunctionalProperty, owl:ObjectProperty .\r\n\r\n");

                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writePrimaryTypes(BufferedWriter out) throws IOException {
        HashMap<String, String> hm = new HashMap<String, String>();
        for (PrimaryTypeVO pt : PrimaryTypeVO.getListOfPrimaryTypes()) {
            // if (pt.getPTypeName().equalsIgnoreCase("BOOLEAN")) {
            // out.write("express:" + pt.getPTypeName() + "\r\n");
            // out.write("\trdf:type owl:Class ;" + "\r\n");
            // out.write("\trdfs:subClassOf express:LOGICAL ." + "\r\n" +
            // "\r\n");
            //
            // out.write("express:TRUE" + "\r\n");
            // out.write("\trdf:type express:BOOLEAN ;" + "\r\n");
            // out.write("\trdf:type owl:NamedIndividual ." + "\r\n" + "\r\n");
            //
            // out.write("express:FALSE" + "\r\n");
            // out.write("\trdf:type express:BOOLEAN ;" + "\r\n");
            // out.write("\trdf:type owl:NamedIndividual ." + "\r\n" + "\r\n");
            //
            // } else
            if (pt.getPTypeName().equalsIgnoreCase("LOGICAL")) {

                out.write("expr:" + pt.getPTypeName() + "\r\n");
                out.write("\trdf:type owl:Class ." + "\r\n" + "\r\n");
                out.write("\trdfs:subClassOf " + "\r\n");
                out.write("\t\t[ " + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty expr:hasLogical ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom expr:LogicalEnum" + "\r\n");
                out.write("\t\t] ." + "\r\n" + "\r\n");

                out.write("expr:LogicalEnum " + "\r\n");
                out.write("\trdf:type owl:Class ;" + "\r\n");
                out.write("\trdfs:subClassOf expr:ENUMERATION ." + "\r\n" + "\r\n");

                out.write("expr:TRUE" + "\r\n");
                out.write("\trdfs:label \"TRUE\" ;");
                out.write("\trdf:type expr:LogicalEnum ;" + "\r\n");
                out.write("\trdf:type owl:NamedIndividual ." + "\r\n" + "\r\n");

                out.write("expr:FALSE" + "\r\n");
                out.write("\trdfs:label \"FALSE\" ;");
                out.write("\trdf:type expr:LogicalEnum ;" + "\r\n");
                out.write("\trdf:type owl:NamedIndividual ." + "\r\n" + "\r\n");

                out.write("expr:UNKNOWN" + "\r\n");
                out.write("\trdfs:label \"UNKNOWN\" ;");
                out.write("\trdf:type expr:LogicalEnum ;" + "\r\n");
                out.write("\trdf:type owl:NamedIndividual ." + "\r\n" + "\r\n");
            } else {
                out.write("expr:" + pt.getPTypeName() + "\r\n");
                out.write("\trdf:type owl:Class ;" + "\r\n");

                out.write("\trdfs:subClassOf " + "\r\n");
                out.write("\t\t[ " + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom xsd:" + pt.getXSDType() + " ;" + "\r\n");
                out.write("\t\t\towl:onProperty expr:has" + pt.getXSDType().substring(0, 1).toUpperCase() + pt.getXSDType().substring(1) + "\r\n");
                out.write("\t\t] ." + "\r\n" + "\r\n");

                // Added to allow writing multiple primary types that refer to
                // the same XSD type
                String key = pt.getXSDType();
                String val = hm.get(pt.getXSDType());
                if (hm.containsKey(key)) {
                    hm.remove(key);
                    hm.put(key, val + " expr:" + pt.getPTypeName());
                } else {
                    hm.put(key, "expr:" + pt.getPTypeName());
                }
            }
        }
        Iterator<Map.Entry<String, String>> it = hm.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pairs = (Map.Entry<String, String>) it.next();
            out.write("expr:has" + pairs.getKey().substring(0, 1).toUpperCase() + pairs.getKey().substring(1) + "\r\n");
            out.write("\trdf:type owl:DatatypeProperty, owl:FunctionalProperty ;" + "\r\n");
            out.write("\trdfs:label \"has" + pairs.getKey().substring(0, 1).toUpperCase() + pairs.getKey().substring(1) + "\" ;" + "\r\n");
            out.write("\trdfs:range xsd:" + pairs.getKey() + " ;" + "\r\n");
            out.write("\trdfs:domain " + "\r\n");
            out.write("\t\t[ " + "\r\n");
            out.write("\t\t\trdf:type owl:Class ;" + "\r\n");
            out.write("\t\t\towl:unionOf ( " + pairs.getValue() + " )" + "\r\n");
            out.write("\t\t] ." + "\r\n" + "\r\n");
            it.remove();
        }
    }

    private void writeNamedIndividuals(BufferedWriter out) throws IOException {
        for (NamedIndividualVO ni : enumIndividuals) {
            out.write("ifc:" + ni.getNamedIndividual() + "\r\n");
            out.write("\trdf:type owl:NamedIndividual ;" + "\r\n");
            out.write("\trdf:type ifc:" + ni.getEnumName() + " ;" + "\r\n");
            out.write("\trdfs:label \"" + ni.getOriginalNameOfIndividual() + "\" ." + "\r\n" + "\r\n");
        }
    }

    private void writeHelperClasses(BufferedWriter out) throws IOException {
        // enumeration class
        out.write("expr:ENUMERATION" + "\r\n");
        out.write("\trdf:type owl:Class ." + "\r\n\r\n");

        // select class
        out.write("expr:SELECT" + "\r\n");
        out.write("\trdf:type owl:Class ." + "\r\n\r\n");

        // hasSet object property
        out.write("expr:hasSet" + "\r\n");
        out.write("\trdf:type owl:ObjectProperty ;" + "\r\n");
        out.write("\trdfs:label \"hasSet" + "\" ." + "\r\n" + "\r\n");
    }

    private void writeEntitiesToOWL(Iterator<Entry<String, EntityVO>> it, BufferedWriter out) throws IOException {
        // out.write("# start writing entity classes\r\n");
        while (it.hasNext()) {

            Entry<String, EntityVO> pairs = it.next();
            EntityVO evo = pairs.getValue();

            // Generate the disjoint set:
            StringBuffer sibtxt = new StringBuffer();
            Set<String> sibling_set = this.siblings.get(evo.getName());
            if (sibling_set != null) {
                Iterator<String> sib_it = sibling_set.iterator();
                int ii = 0;
                while (sib_it.hasNext()) {
                    String sib = sib_it.next().toString();
                    if (!sib.equalsIgnoreCase(evo.getName())) {
                        if (ii > 0)
                            sibtxt.append(",");
                        sibtxt.append(" ifc:");
                        sibtxt.append(sib);
                        ii++;
                    }
                }
            }

            // Write classes
            out.write("ifc:" + evo.getName() + "\r\n");
            out.write("\trdf:type owl:Class");
            if (evo.getSuperclass() != null) {
                out.write(" ;\r\n");
                out.write("\trdfs:subClassOf ifc:" + evo.getSuperclass());
            }

            // Write select supertypes
            if (evo.getParentSelectTypes() != null) {
                out.write(" ;\r\n");
                out.write("\trdfs:subClassOf");
                for (int i = 0; i < evo.getParentSelectTypes().size(); i++) {
                    if (i != evo.getParentSelectTypes().size() - 1)
                        out.write(" ifc:" + evo.getParentSelectTypes().get(i).getName() + ",");
                    else
                        out.write(" ifc:" + evo.getParentSelectTypes().get(i).getName());
                }
            }

            // Writing abstractness
            if (evo.isAbstractSuperclass()) {
                out.write(" ;\r\n");
                out.write("\trdfs:subClassOf " + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Class ;" + "\r\n");
                out.write("\t\t\towl:unionOf" + "\r\n");
                out.write("\t\t\t\t(" + "\r\n");
                Set<String> l = evo.getSubClassList();
                for (Iterator<String> lit = l.iterator(); lit.hasNext();) {
                    String x = lit.next();
                    out.write("\t\t\t\t\tifc:" + x + "\r\n");
                }
                out.write("\t\t\t\t)" + "\r\n");
                out.write("\t\t]");
            }

            // Writing disjointness
            if (sibtxt.length() > 0) {
                out.write(" ;\r\n");
                out.write("\towl:disjointWith " + sibtxt.toString());
            }

            // Writing properties
            for (int n = 0; n < evo.getAttributes().size(); n++) {
                AttributeVO attr = evo.getAttributes().get(n);
                if (properties.containsKey(attr.getName()))// ||
                                                           // properties.containsKey(prop.getOriginalName())
                    writeRegularProperty(attr, out);
            }

            // write inverse properties
            for (int n = 0; n < evo.getInverses().size(); n++) {
                PropertyVO prop = evo.getInverses().get(n).getAssociatedProperty();
                if (properties.containsKey(prop.getName()))// ||
                                                           // properties.containsKey(prop.getOriginalName())
                    writeInverseProperty(prop, out);
            }

            out.write(" .\r\n");
            out.write("\r\n");
        }
    }

    private void writeRegularProperty(AttributeVO attr, BufferedWriter out) throws IOException {
        // write property range
        out.write(" ;\r\n");
        out.write("\trdfs:subClassOf" + "\r\n");//
        out.write("\t\t[" + "\r\n");
        out.write("\t\t\trdf:type owl:Restriction ; " + "\r\n");
        if (attr.isSet()) {
            out.write("\t\t\towl:allValuesFrom " + attr.getRangeNS() + ":" + attr.getType().getName() + " ; \r\n");
        } else if (attr.isListOfList()) {
            out.write("\t\t\towl:allValuesFrom " + attr.getRangeNS() + ":" + attr.getType().getName() + "_List_List" + " ; \r\n");
        } else if (attr.isList() || attr.isArray()) {
            out.write("\t\t\towl:allValuesFrom " + attr.getRangeNS() + ":" + attr.getType().getName() + "_List" + " ; \r\n");
        } else {
            out.write("\t\t\towl:allValuesFrom " + attr.getRangeNS() + ":" + attr.getType().getName() + " ; \r\n");
        }
        out.write("\t\t\towl:onProperty ifc:" + attr.getLowerCaseName() + "\r\n");
        out.write("\t\t]");

        if (attr.isUnique()) {
            // this is ignored
        }

        // cardinality restrictions for lists
        if (attr.isArray())
            writeCardinalityRestrictionsForArray(attr.getMinCard(), attr.getMaxCard(), attr.getRangeNS() + ":" + attr.getType().getName(), attr.getLowerCaseName(), out, true);
        else if (attr.isListOfList() && !attr.isSet())
            writeCardinalityRestrictionsForListOfList(attr.getMinCard(), attr.getMaxCard(), attr.getRangeNS() + ":" + attr.getType().getName(), attr.getLowerCaseName(), out, true);
        else if (attr.isList() && !attr.isSet())
            writeCardinalityRestrictionsForList(attr.getMinCard(), attr.getMaxCard(), attr.getRangeNS() + ":" + attr.getType().getName(), attr.getLowerCaseName(), out, true);
        // else
        // System.out.println("not a set, not a list, not a list of list: " +
        // attr.getLowerCaseName());

        // cardinality restriction for property (depends on optional /
        // set / list etc.)
        if (attr.isSet() && attr.getMaxCard() == -1 && attr.getMinCard() == 0) {
            // no cardinality restrictions needed
        } else {
            if (!attr.isSet()) {
                out.write(" ;\r\n");
                out.write("\t" + "rdfs:subClassOf " + "\r\n");
                out.write("\t\t" + "[" + "\r\n");
                out.write("\t\t\t" + "rdf:type owl:Restriction ;" + "\r\n");
                if (!attr.isOptional()) {
                    out.write("\t\t\t" + "owl:qualifiedCardinality \"1\"^^xsd:nonNegativeInteger ;" + "\r\n");
                } else {
                    out.write("\t\t\t" + "owl:maxQualifiedCardinality \"1\"^^xsd:nonNegativeInteger ;" + "\r\n");
                }
                out.write("\t\t\t" + "owl:onProperty ifc:" + attr.getLowerCaseName() + " ;\r\n");

                if (attr.isListOfList()) {
                    out.write("\t\t\t" + "owl:onClass " + attr.getRangeNS() + ":" + attr.getType().getName() + "_List_List" + "\r\n");
                } else if (attr.isList() || attr.isArray()) {
                    out.write("\t\t\t" + "owl:onClass " + attr.getRangeNS() + ":" + attr.getType().getName() + "_List" + "\r\n");
                } else {
                    out.write("\t\t\t" + "owl:onClass " + attr.getRangeNS() + ":" + attr.getType().getName() + "\r\n");
                }
                out.write("\t\t" + "]");
            } else {
                if (attr.isOptional() && attr.getMaxCard() == -1) {
                    // do nothing
                } else {
                    if (attr.getMinCard() == 1 && attr.getMaxCard() == 1 && !attr.isOptional()) {
                        // UNICUM CASE:
                        // RelatedObjects_of_IfcRelDefinesByProperties Property
                        // in IFC4 SET[1:1]
                        out.write(" ;\r\n");
                        out.write("\t" + "rdfs:subClassOf " + "\r\n");
                        out.write("\t\t" + "[" + "\r\n");
                        out.write("\t\t\t" + "rdf:type owl:Restriction ;" + "\r\n");
                        out.write("\t\t\t" + "owl:qualifiedCardinality \"" + attr.getMinCard() + "\"^^xsd:nonNegativeInteger ;" + "\r\n");
                        out.write("\t\t\t" + "owl:onProperty ifc:" + attr.getLowerCaseName() + " ;\r\n");
                        out.write("\t\t\t" + "owl:onClass " + attr.getRangeNS() + ":" + attr.getType().getName() + "\r\n");
                        out.write("\t\t" + "]");
                    } else {

                        if (attr.getMinCard() > 0 && !attr.isOptional()) {
                            out.write(" ;\r\n");
                            out.write("\t" + "rdfs:subClassOf " + "\r\n");
                            out.write("\t\t" + "[" + "\r\n");
                            out.write("\t\t\t" + "rdf:type owl:Restriction ;" + "\r\n");
                            out.write("\t\t\t" + "owl:minQualifiedCardinality \"" + attr.getMinCard() + "\"^^xsd:nonNegativeInteger ;" + "\r\n");
                            out.write("\t\t\t" + "owl:onProperty ifc:" + attr.getLowerCaseName() + " ;\r\n");
                            out.write("\t\t\t" + "owl:onClass " + attr.getRangeNS() + ":" + attr.getType().getName() + "\r\n");
                            out.write("\t\t" + "]");
                        }

                        if (attr.getMaxCard() != -1) {
                            out.write(" ;\r\n");
                            out.write("\t" + "rdfs:subClassOf " + "\r\n");
                            out.write("\t\t" + "[" + "\r\n");
                            out.write("\t\t\t" + "rdf:type owl:Restriction ;" + "\r\n");
                            out.write("\t\t\t" + "owl:maxQualifiedCardinality \"" + attr.getMaxCard() + "\"^^xsd:nonNegativeInteger ;" + "\r\n");
                            out.write("\t\t\t" + "owl:onProperty ifc:" + attr.getLowerCaseName() + " ;\r\n");
                            out.write("\t\t\t" + "owl:onClass " + attr.getRangeNS() + ":" + attr.getType().getName() + "\r\n");
                            out.write("\t\t" + "]");
                        }
                    }
                }
            }
        }
    }

    private void writeInverseProperty(PropertyVO prop, BufferedWriter out) throws IOException {
        out.write(" ;\r\n");
        out.write("\trdfs:subClassOf" + "\r\n");
        out.write("\t\t[" + "\r\n");
        out.write("\t\t\trdf:type owl:Restriction ; " + "\r\n");
        out.write("\t\t\towl:allValuesFrom ifc:" + prop.getRange() + " ; \r\n");
        out.write("\t\t\towl:onProperty ifc:" + prop.getLowerCaseName() + "\r\n");
        out.write("\t\t]");

        if (prop.getMinCardinality() == -1 && prop.getMaxCardinality() == -1) {
            System.out.println("This should be impossible");
            // [?:?]
            // no cardinality restrictions explicitly stated (but default is (0,
            // -1))
            // however, as there is no OPTIONAL statement listed for any INVERSE
            // property, this property is considered to be required
            // qualifiedCadinality = 1
        } else if (prop.getMinCardinality() == -1 && prop.getMaxCardinality() != -1) {
            // [?:2]
            // This is not supposed to happen
            System.out.println("WARNING - IMPOSSIBLE: found 'unlimited' mincardinality restriction combined with a bounded maxcardinality restriction for :" + prop.getLowerCaseName());
        } else if (prop.getMinCardinality() != -1 && prop.getMaxCardinality() == -1) {
            int start = prop.getMinCardinality();
            // [2:?]
            if (start != 0) {
                out.write(" ;" + "\r\n");
                out.write("\trdfs:subClassOf" + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty ifc:" + prop.getLowerCaseName() + " ;" + "\r\n");
                out.write("\t\t\towl:onClass ifc:" + prop.getRange() + " ;" + "\r\n");
                out.write("\t\t\towl:minQualifiedCardinality \"" + start + "\"^^xsd:nonNegativeInteger" + "\r\n");
                out.write("\t\t]");
            } else {
                // this is the regular option / default in EXPRESS
                if (!prop.isSet()) {
                    out.write(" ;" + "\r\n");
                    out.write("\trdfs:subClassOf" + "\r\n");
                    out.write("\t\t[" + "\r\n");
                    out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                    out.write("\t\t\towl:onProperty ifc:" + prop.getLowerCaseName() + " ;" + "\r\n");
                    out.write("\t\t\towl:onClass ifc:" + prop.getRange() + " ;" + "\r\n");
                    out.write("\t\t\towl:qualifiedCardinality \"1\"^^xsd:nonNegativeInteger" + "\r\n");
                    out.write("\t\t]");
                }
            }
        } else {
            int start = prop.getMinCardinality();
            int end = prop.getMaxCardinality();
            if (start == end) {
                // [3:3]
                // explicitly qualified cardinality
                if (end != 0) {
                    out.write(" ;" + "\r\n");
                    out.write("\trdfs:subClassOf" + "\r\n");
                    out.write("\t\t[" + "\r\n");
                    out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                    out.write("\t\t\towl:onProperty ifc:" + prop.getLowerCaseName() + " ;" + "\r\n");
                    out.write("\t\t\towl:onClass ifc:" + prop.getRange() + " ;" + "\r\n");
                    out.write("\t\t\towl:qualifiedCardinality \"" + end + "\"^^xsd:nonNegativeInteger" + "\r\n");
                    out.write("\t\t]");
                }
            } else if (start < end) {
                // [1:2]
                // min-max qualified cardinality

                if (end != 0) {
                    out.write(" ;" + "\r\n");
                    out.write("\trdfs:subClassOf" + "\r\n");
                    out.write("\t\t[" + "\r\n");
                    out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                    out.write("\t\t\towl:onProperty ifc:" + prop.getLowerCaseName() + " ;" + "\r\n");
                    out.write("\t\t\towl:onClass ifc:" + prop.getRange() + " ;" + "\r\n");
                    out.write("\t\t\towl:maxQualifiedCardinality \"" + end + "\"^^xsd:nonNegativeInteger" + "\r\n");
                    out.write("\t\t]");
                }
                if (start != 0) {
                    out.write(" ;" + "\r\n");
                    out.write("\trdfs:subClassOf" + "\r\n");
                    out.write("\t\t[" + "\r\n");
                    out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                    out.write("\t\t\towl:onProperty ifc:" + prop.getLowerCaseName() + " ;" + "\r\n");
                    out.write("\t\t\towl:onClass ifc:" + prop.getRange() + " ;" + "\r\n");
                    out.write("\t\t\towl:minQualifiedCardinality \"" + start + "\"^^xsd:nonNegativeInteger" + "\r\n");
                    out.write("\t\t]");
                }
            } else {
                // This is not supposed to happen
                System.out.println("WARNING - IMPOSSIBLE: found mincardinality restriction that is greater than maxcardinality restriction for :" + prop.getLowerCaseName());
            }
        }
    }

    private void writeTypesToOWL(Iterator<Entry<String, TypeVO>> it, BufferedWriter out) throws IOException {
        while (it.hasNext()) {
            Entry<String, TypeVO> pairs = it.next();
            TypeVO tvo = pairs.getValue();

            if (tvo.getPrimarytype().equalsIgnoreCase("ENUMERATION"))
                writeEnumerations(tvo, out);
            else if (tvo.getPrimarytype().equalsIgnoreCase("SELECT"))
                writeSelects(tvo, out);
            else {
                String type = tvo.getPrimarytype();
                if (type.startsWith("LIST"))
                    writeListTypeVO(tvo, out);
                else if (type.startsWith("ARRAY"))
                    writeArrayTypeVO(tvo, out);
                else if (type.startsWith("SET"))
                    writeSetTypeVO(tvo, out);
                else
                    writeRegularTypeVO(tvo, out);
            }
        }
    }

    private void writeEnumerations(TypeVO tvo, BufferedWriter out) throws IOException {
        out.write("ifc:" + tvo.getName() + "\r\n");
        out.write("\trdf:type owl:Class ;" + "\r\n");
        if (tvo.getParentSelectTypes() != null) {
            out.write("\trdfs:subClassOf");
            for (int i = 0; i < tvo.getParentSelectTypes().size(); i++) {
                if (i != tvo.getParentSelectTypes().size() - 1)
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + ",");
                else
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + " ;" + "\r\n");
            }
        }

        boolean writingEnumsAsOneOfs = false;
        if (writingEnumsAsOneOfs) {
            out.write("\towl:equivalentClass " + "\r\n");
            out.write("\t\t[" + "\r\n");
            out.write("\t\t\trdf:type owl:Class ;" + "\r\n");
            out.write("\t\t\towl:oneOf " + "\r\n");
            out.write("\t\t\t\t( " + "\r\n");
            for (int i = 0; i < tvo.getEnumEntities().size(); i++) {
                if (i != tvo.getEnumEntities().size() - 1)
                    out.write("\t\t\t\t\tifc:" + getNamedIndividual(tvo.getEnumEntities().get(i), tvo.getName()).getNamedIndividual() + " " + "\r\n");
                else
                    out.write("\t\t\t\t\tifc:" + getNamedIndividual(tvo.getEnumEntities().get(i), tvo.getName()).getNamedIndividual() + "\r\n");
            }
            out.write("\t\t\t\t) " + "\r\n");
            out.write("\t\t] ; " + "\r\n");
        }

        out.write("\trdfs:subClassOf expr:ENUMERATION ." + "\r\n\r\n");
    }

    private void writeSelects(TypeVO tvo, BufferedWriter out) throws IOException {
        out.write("ifc:" + tvo.getName() + "\r\n");
        out.write("\trdf:type owl:Class ;" + "\r\n");
        if (tvo.getParentSelectTypes() != null) {
            out.write("\trdfs:subClassOf");
            for (int i = 0; i < tvo.getParentSelectTypes().size(); i++) {
                if (i != tvo.getParentSelectTypes().size() - 1)
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + ",");
                else
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + " ;" + "\r\n");
            }
        }
        boolean writingSelectsAsUnionOfs = false;
        if (writingSelectsAsUnionOfs) {
            out.write("\towl:equivalentClass" + "\r\n");
            out.write("\t\t[" + "\r\n");
            out.write("\t\t\trdf:type owl:Class ;" + "\r\n");
            out.write("\t\t\towl:unionOf " + "\r\n");
            out.write("\t\t\t\t( " + "\r\n");
            List<String> selects = tvo.getSelectEntities();
            for (int i = 0; i < selects.size(); i++) {
                out.write("\t\t\t\t\tifc:" + selects.get(i) + " " + "\r\n");
            }
            out.write("\t\t\t\t) " + "\r\n");
            out.write("\t\t] ; " + "\r\n");
        }

        out.write("\trdfs:subClassOf expr:SELECT ." + "\r\n\r\n");
    }

    private void writeListTypeVO(TypeVO tvo, BufferedWriter out) throws IOException {

        String[] cList = tvo.getPrimarytype().split(" ");
        String content = cList[cList.length - 1];

        out.write("ifc:" + tvo.getName() + "\r\n");
        out.write("\trdf:type owl:Class ;" + "\r\n");

        if (content.endsWith(";"))
            content = content.substring(0, content.length() - 1);

        String ns = "ifc";
        if (content.equalsIgnoreCase("NUMBER") || content.equalsIgnoreCase("REAL") || content.equalsIgnoreCase("INTEGER") || content.equalsIgnoreCase("LOGICAL") || content.equalsIgnoreCase("BOOLEAN")
                        || content.equalsIgnoreCase("STRING") || content.equalsIgnoreCase("BINARY"))
            ns = "expr";

        out.write("\trdfs:subClassOf " + ns + ":" + content + "_List ");

        if (tvo.getParentSelectTypes() != null) {
            out.write(" ;\r\n");
            out.write("\trdfs:subClassOf");
            for (int i = 0; i < tvo.getParentSelectTypes().size(); i++) {
                if (i != tvo.getParentSelectTypes().size() - 1)
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + ",");
                else
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName());
            }
            out.write(" ;\r\n");
        }

        // check for cardinality restrictions and add if available

        String type = tvo.getPrimarytype();
        String startIndex = type.substring(type.indexOf('[') + 1, type.indexOf('[') + 2);
        String endIndex = type.substring(type.indexOf(']') - 1, type.indexOf(']'));

        int start = -1;
        int end = -1;
        try {
            start = Integer.parseInt(startIndex);
        } catch (NumberFormatException e) {
        }

        try {
            end = Integer.parseInt(endIndex);
        } catch (NumberFormatException e) {
        }

        writeCardinalityRestrictionsForList(start, end, ns + ":" + content, "hasNext", out, false);

        out.write("." + "\r\n\r\n");

        if ("ifc".equalsIgnoreCase(ns)) {
            if (!listPropertiesOutput.contains(content)) {
                // property already contained in resulting OWL file
                // (.TTL) -> no need to write additional property
                listPropertiesOutput.add(content);

                out.write(ns + ":" + content + "_EmptyList" + "\r\n");
                out.write("\trdf:type owl:Class ;" + "\r\n");
                out.write("\trdfs:subClassOf list:EmptyList, " + ns + ":" + content + "_List" + " ." + "\r\n" + "\r\n");

                out.write(ns + ":" + content + "_List" + "\r\n");
                out.write("\trdf:type owl:Class ;" + "\r\n");

                out.write("\trdfs:subClassOf list:OWLList ;" + "\r\n");

                out.write("\trdfs:subClassOf" + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty list:hasContents ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom " + ns + ":" + content + "\r\n");
                out.write("\t\t] ;" + "\r\n");
                out.write("\trdfs:subClassOf" + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty list:isFollowedBy ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom " + ns + ":" + content + "_List\r\n");
                out.write("\t\t] ;" + "\r\n");
                out.write("\trdfs:subClassOf" + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty list:hasNext ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom " + ns + ":" + content + "_List\r\n");
                out.write("\t\t] ." + "\r\n\r\n");
            }
        }
    }

    private void writeArrayTypeVO(TypeVO tvo, BufferedWriter out) throws IOException {
        String[] cList = tvo.getPrimarytype().split(" ");
        String content = cList[cList.length - 1];

        out.write("ifc:" + tvo.getName() + "\r\n");
        out.write("\trdf:type owl:Class ;" + "\r\n");

        if (content.endsWith(";"))
            content = content.substring(0, content.length() - 1);

        String ns = "ifc";
        if (content.equalsIgnoreCase("NUMBER") || content.equalsIgnoreCase("REAL") || content.equalsIgnoreCase("INTEGER") || content.equalsIgnoreCase("LOGICAL") || content.equalsIgnoreCase("BOOLEAN")
                        || content.equalsIgnoreCase("STRING") || content.equalsIgnoreCase("BINARY"))
            ns = "expr";

        out.write("\trdfs:subClassOf " + ns + ":" + content + "_List ");

        if (tvo.getParentSelectTypes() != null) {
            out.write(" ;\r\n");
            out.write("\trdfs:subClassOf");
            for (int i = 0; i < tvo.getParentSelectTypes().size(); i++) {
                if (i != tvo.getParentSelectTypes().size() - 1)
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + ",");
                else
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName());
            }
            out.write(" ;\r\n");
        }

        // check for cardinality restrictions and add if available
        String type = tvo.getPrimarytype();
        String startIndex = type.substring(type.indexOf('[') + 1, type.indexOf('[') + 2);
        String endIndex = type.substring(type.indexOf(']') - 1, type.indexOf(']'));
        int start = -1;
        int end = -1;
        try {
            start = Integer.parseInt(startIndex);
        } catch (NumberFormatException e) {
        }

        try {
            end = Integer.parseInt(endIndex);
        } catch (NumberFormatException e) {
        }

        // cardinality restrictions
        writeCardinalityRestrictionsForArray(start, end, ns + ":" + content, "hasNext", out, false);

        out.write("." + "\r\n\r\n");

        if ("ifc".equalsIgnoreCase(ns)) {

            if (!listPropertiesOutput.contains(content)) {
                // property already contained in resulting OWL file
                // (.TTL) -> no need to write additional property
                listPropertiesOutput.add(content);

                out.write(ns + ":" + content + "_EmptyList" + "\r\n");
                out.write("\trdf:type owl:Class ;" + "\r\n");
                out.write("\trdfs:subClassOf list:EmptyList, " + ns + ":" + content + "_List" + " ." + "\r\n" + "\r\n");

                out.write(ns + ":" + content + "_List" + "\r\n");
                out.write("\trdf:type owl:Class ;" + "\r\n");
                out.write("\trdfs:subClassOf list:OWLList ;" + "\r\n");

                out.write("\trdfs:subClassOf" + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty list:hasContents ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom " + ns + ":" + content + "\r\n");
                out.write("\t\t] ;" + "\r\n");

                out.write("\trdfs:subClassOf" + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty list:isFollowedBy ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom " + ns + ":" + content + "_List\r\n");
                out.write("\t\t] ;" + "\r\n");
                out.write("\trdfs:subClassOf" + "\r\n");
                out.write("\t\t[" + "\r\n");
                out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                out.write("\t\t\towl:onProperty list:hasNext ;" + "\r\n");
                out.write("\t\t\towl:allValuesFrom " + ns + ":" + content + "_List\r\n");
                out.write("\t\t] ." + "\r\n\r\n");
            }
        }
    }

    private void writeSetTypeVO(TypeVO tvo, BufferedWriter out) throws IOException {
        String[] cList = tvo.getPrimarytype().split(" ");
        String content = cList[cList.length - 1];
        if (content.endsWith(";"))
            content = content.substring(0, content.length() - 1);

        String ns = "ifc";
        if (content.equalsIgnoreCase("NUMBER") || content.equalsIgnoreCase("REAL") || content.equalsIgnoreCase("INTEGER") || content.equalsIgnoreCase("LOGICAL") || content.equalsIgnoreCase("BOOLEAN")
                        || content.equalsIgnoreCase("STRING") || content.equalsIgnoreCase("BINARY"))
            ns = "expr";

        out.write("ifc:" + tvo.getName() + "\r\n");
        out.write("\trdf:type owl:Class ;" + "\r\n");

        if (tvo.getParentSelectTypes() != null) {
            out.write(" ;\r\n");
            out.write("\trdfs:subClassOf");
            for (int i = 0; i < tvo.getParentSelectTypes().size(); i++) {
                if (i != tvo.getParentSelectTypes().size() - 1)
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + ",");
                else
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName());
            }
            out.write(" ;\r\n");
        }

        out.write("\trdfs:subClassOf " + "\r\n");
        out.write("\t\t[ " + "\r\n");
        out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
        out.write("\t\t\towl:allValuesFrom " + ns + ":" + content + " ;" + "\r\n");
        out.write("\t\t\towl:onProperty expr:hasSet" + "\r\n");
        out.write("\t\t] ;" + "\r\n");
        out.write("\t" + "rdfs:subClassOf " + "\r\n");
        out.write("\t\t" + "[" + "\r\n");
        out.write("\t\t\t" + "rdf:type owl:Restriction ;" + "\r\n");
        out.write("\t\t\t" + "owl:minQualifiedCardinality \"" + 1 + "\"^^xsd:nonNegativeInteger ;" + "\r\n");
        out.write("\t\t\towl:onProperty expr:hasSet ;" + "\r\n");
        out.write("\t\t\t" + "owl:onClass " + ns + ":" + content + "\r\n");
        out.write("\t\t] ." + "\r\n" + "\r\n");
    }

    private void writeRegularTypeVO(TypeVO tvo, BufferedWriter out) throws IOException {
        out.write("ifc:" + tvo.getName() + "\r\n");
        out.write("\trdf:type owl:Class ;" + "\r\n");

        // parent selects
        if (tvo.getParentSelectTypes() != null) {
            out.write("\trdfs:subClassOf");
            for (int i = 0; i < tvo.getParentSelectTypes().size(); i++) {
                if (i != tvo.getParentSelectTypes().size() - 1)
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + ",");
                else
                    out.write(" ifc:" + tvo.getParentSelectTypes().get(i).getName() + " ;" + "\r\n");
            }
        }

        String ptype = tvo.getPrimarytype();
        if (PrimaryTypeVO.checkIfPType(ptype)) {
            String pType = tvo.getPrimarytype();
            if (pType.equalsIgnoreCase("LOGICAL")) {
                out.write("\trdfs:subClassOf expr:LOGICAL ." + "\r\n" + "\r\n");
                // out.write("\trdfs:subClassOf " + "\r\n");
                // out.write("\t\t[ " + "\r\n");
                // out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                // out.write("\t\t\towl:allValuesFrom expr:LOGICAL" + " ;" +
                // "\r\n");
                // out.write("\t\t\towl:onProperty expr:hasLogical" + "\r\n");
                // out.write("\t\t] ." + "\r\n" + "\r\n");
            } else if (pType.equalsIgnoreCase("BOOLEAN")) {
                out.write("\trdfs:subClassOf expr:BOOLEAN ." + "\r\n" + "\r\n");
                // out.write("\trdfs:subClassOf " + "\r\n");
                // out.write("\t\t[ " + "\r\n");
                // out.write("\t\t\trdf:type owl:Restriction ;" + "\r\n");
                // out.write("\t\t\towl:allValuesFrom expr:BOOLEAN" + " ;" +
                // "\r\n");
                // out.write("\t\t\towl:onProperty expr:hasBoolean" + "\r\n");
                // out.write("\t\t] ." + "\r\n" + "\r\n");
            } else
                out.write("\trdfs:subClassOf expr:" + tvo.getPrimarytype() + " .\r\n\r\n");
        } else {
            if (TypeVO.checkIfType(ptype)) {
                out.write("\trdfs:subClassOf ifc:" + tvo.getPrimarytype() + " .\r\n\r\n");
            } else {
                PrimaryTypeVO t = PrimaryTypeVO.getClosestResemblance(ptype);
                if (t == null)
                    System.out.println("OWLWriter::writeTypesToOWL - Did not find useful primarytype: " + ptype);
                out.write("\trdfs:subClassOf expr:" + t.getPTypeName() + " .\r\n\r\n");
            }
        }
    }

    private NamedIndividualVO getNamedIndividual(String originalIndividualName, String enumName) {
        for (NamedIndividualVO ni : enumIndividuals) {
            if (ni.getOriginalNameOfIndividual() == originalIndividualName && ni.getEnumName() == enumName) {
                return ni;
            }
        }
        return null;
    }

    private String getOwl_header() {
        String s = "";
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date date = new Date();

        s += "@prefix xsd: <"
                        + Namespace.XSD
                        + "> .\r\n"
                        + "@prefix owl: <"
                        + Namespace.OWL
                        + "> .\r\n"
                        + "@prefix rdfs: <"
                        + Namespace.RDFS
                        + "> .\r\n"
                        + "@prefix dce: <"
                        + Namespace.DCE
                        + "> .\r\n"
                        + "@prefix vann: <"
                        + Namespace.VANN
                        + "> .\r\n"
                        + "@prefix list: <"
                        + Namespace.LIST
                        + "> .\r\n"
                        + "@prefix expr: <"
                        + Namespace.EXPRESS
                        + "> .\r\n"
                        + "@prefix cc: <"
                        + Namespace.CC
                        + "> .\r\n"
                        + "@prefix rdf: <"
                        + Namespace.RDF
                        + "> .\r\n"
                        + "\r\n"
                        + "<"
                        + Namespace.IFC
                        + ">\r\n"
                        + "\trdf:type owl:Ontology ;\r\n"
                        + "\trdfs:comment \"Ontology automatically generated from the EXPRESS schema '"
                        + expressSchemaName
                        + "' using the 'IFC-to-RDF' converter developed by Pieter Pauwels (pipauwel.pauwels@ugent.be), based on the earlier versions from Jyrki Oraskari (jyrki.oraskari@aalto.fi) and Davy Van Deursen (davy.vandeursen@ugent.be)\" ;"
                        + "\r\n" + "\tdce:creator \"Pieter Pauwels (pipauwel.pauwels@ugent.be)\" ;\r\n" + "\tdce:creator \"Walter Terkaj  (walter.terkaj@itia.cnr.it)\" ;\r\n" + "\tdce:date \""
                        + dateFormat.format(date) + "\" ;\r\n" + "\tdce:contributor \"Aleksandra Sojic (aleksandra.sojic@itia.cnr.it)\" ;\r\n"
                        + "\tdce:contributor \"Maria Poveda Villalon (mpoveda@fi.upm.es)\" ;\r\n" + "\tdce:contributor \"Jakob Beetz (j.beetz@tue.nl)\" ;\r\n" + "\tdce:title \"" + expressSchemaName
                        + "\" ;\r\n" + "\tdce:description \"OWL ontology for the IFC conceptual data schema and exchange file format for Building Information Model (BIM) data\" ;\r\n"
                        + "\tdce:identifier \"" + expressSchemaName + "\" ;\r\n" + "\tdce:language \"en\" ; \r\n" + "\tvann:preferredNamespacePrefix \"ifc\" ; \r\n"
                        + "\tvann:preferredNamespaceUri \"" + Namespace.IFC + "\" ; \r\n" + "\towl:imports <https://w3id.org/express> ; \r\n"
                        + "\tcc:license <http://creativecommons.org/licenses/by/3.0/> . \r\n\r\n";

        s += "dce:creator \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:description \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:date \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:contributor \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:title \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:identifier \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:language \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        return s;
    }

    private String getExpressOwlHeader() {
        String s = "";
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date date = new Date();

        s += "@prefix xsd: <"
                        + Namespace.XSD
                        + "> .\r\n"
                        + "@prefix owl: <"
                        + Namespace.OWL
                        + "> .\r\n"
                        + "@prefix rdfs: <"
                        + Namespace.RDFS
                        + "> .\r\n"
                        + "@prefix dce: <"
                        + Namespace.DCE
                        + "> .\r\n"
                        + "@prefix vann: <"
                        + Namespace.VANN
                        + "> .\r\n"
                        + "@prefix list: <"
                        + Namespace.LIST
                        + "> .\r\n"
                        + "@prefix cc: <"
                        + Namespace.CC
                        + "> .\r\n"
                        + "@prefix rdf: <"
                        + Namespace.RDF
                        + "> .\r\n"
                        + "\r\n"
                        + "<"
                        + Namespace.EXPRESS
                        + ">\r\n"
                        + "\trdf:type owl:Ontology ;\r\n"
                        + "\trdfs:comment \"Ontology automatically generated from the EXPRESS schema '"
                        + expressSchemaName
                        + "' using the 'IFC-to-RDF' converter developed by Pieter Pauwels (pipauwel.pauwels@ugent.be), based on the earlier versions from Jyrki Oraskari (jyrki.oraskari@aalto.fi) and Davy Van Deursen (davy.vandeursen@ugent.be)\" ;"
                        + "\r\n" + "\tdce:creator \"Pieter Pauwels (pipauwel.pauwels@ugent.be)\" ;\r\n" + "\tdce:creator \"Walter Terkaj  (walter.terkaj@itia.cnr.it)\" ;\r\n"
                        + "\tdce:creator \"Nam Vu Hoang  (nam.vuhoang@gmail.com)\" ;\r\n" + "\tdce:date \"" + dateFormat.format(date) + "\" ;\r\n"
                        + "\tdce:contributor \"Aleksandra Sojic (aleksandra.sojic@itia.cnr.it)\" ;\r\n" + "\tdce:contributor \"Maria Poveda Villalon (mpoveda@fi.upm.es)\" ;\r\n"
                        + "\tdce:contributor \"Jakob Beetz (j.beetz@tue.nl)\" ;\r\n" + "\tdce:title \"" + expressSchemaName + "\" ;\r\n"
                        + "\tdce:description \"OWL ontology for the EXPRESS concepts\" ;\r\n" + "\tdce:language \"en\" ; \r\n" + "\tvann:preferredNamespacePrefix \"expr\" ; \r\n"
                        + "\tvann:preferredNamespaceUri \"" + Namespace.EXPRESS + "\" ; \r\n" + "\towl:imports <http://owl.cs.manchester.ac.uk/wp-content/uploads/2015/07/list.owl_.txt> ; \r\n"
                        + "\tcc:license <http://creativecommons.org/licenses/by/3.0/> . \r\n\r\n";

        s += "dce:creator \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:description \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:date \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:contributor \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:title \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:format \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:identifier \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        s += "dce:language \r\n\trdf:type owl:AnnotationProperty .\r\n\r\n";
        return s;
    }

    private void writeCardinalityRestrictionsForArray(int minCard, int maxCard, String className, String attrName, BufferedWriter out, boolean asEntity) throws IOException {
        // write cardinality restrictions for the referenced array
        if ((minCard == -1 && maxCard == -1) || (minCard != -1 && maxCard == -1) || (minCard == -1 && maxCard != -1)) {
            System.out.println("WARNING - IMPOSSIBLE: did not find required cardinality restrictions for ARRAY property : " + attrName);
        } else {
            // [3:3]
            // explicitly qualified cardinality
            // writeQualCardRestr(attr.getType().getName() + "_List",
            // attr.getName(), out, (end - start + 1));
            if (minCard >= 1)
                writeMinCardRestr(className + "_List", attrName, out, minCard + 1, asEntity);
            if (maxCard > 1)
                writeMaxCardRestr(className + "_EmptyList", attrName, out, maxCard, asEntity);
        }
    }

    private void writeCardinalityRestrictionsForList(int minCard, int maxCard, String className, String attrName, BufferedWriter out, boolean asEntity) throws IOException {
        if (minCard == -1 && maxCard == -1) {
            System.out.println("WARNING: [?,?] found for : " + attrName + " - " + className);
        } else if (minCard == -1 && maxCard != -1) {
            // [?:2]
            // This is not supposed to happen
            System.out.println("WARNING - IMPOSSIBLE: found 'unlimited' mincardinality restriction combined with a bounded maxcardinality restriction for :" + attrName);
        } else if (minCard != -1 && maxCard == -1) {
            // [2:?]
            if (minCard >= 1)
                writeMinCardRestr(className + "_List", attrName, out, minCard, asEntity);
            else {
                // [1:?]
                // no cardinality restriction can be set on the hasNext
                // property, as the minimum card restr says that it is allowed
                // to set only one element.
            }
        } else {
            if (minCard == maxCard && maxCard >= 1) {
                // [3:3]
                // explicitly qualified cardinality
                // writeQualCardRestr(attr.getType().getName() + "_List",
                // attr.getName(),out,start);
                writeMinCardRestr(className + "_List", attrName, out, minCard, asEntity);
                writeMaxCardRestr(className + "_EmptyList", attrName, out, maxCard, asEntity);
            } else if (minCard < maxCard) {
                // [1:2]
                // min-max qualified cardinality

                if (maxCard > 1)
                    writeMaxCardRestr(className + "_EmptyList", attrName, out, maxCard, asEntity);
                if (minCard >= 1)
                    writeMinCardRestr(className + "_List", attrName, out, minCard, asEntity);
            } else {
                // This is not supposed to happen
                System.out.println("WARNING - IMPOSSIBLE: found mincardinality restriction that is greater than maxcardinality restriction for :" + attrName);
            }
        }
    }

    private void writeCardinalityRestrictionsForListOfList(int minCard, int maxCard, String className, String attrName, BufferedWriter out, boolean asEntity) throws IOException {
        // write cardinality restrictions for the referenced list

        if (minCard == -1 && maxCard == -1) {
            System.out.println("WARNING: [?,?] found for : " + attrName);
            return;
        } else if (minCard == -1 && maxCard != -1) {
            // [?:2]
            // This is not supposed to happen
            System.out.println("WARNING - IMPOSSIBLE: found 'unlimited' mincardinality restriction combined with a bounded maxcardinality restriction for :" + attrName);
            return;
        } else if (minCard != -1 && maxCard == -1) {
            int start = minCard;
            // [2:?]
            if (start >= 1)
                writeMinCardRestr(className + "_List_List", attrName, out, start, asEntity);

            // extra cardinality restrictions (for the second LIST in a LIST OF
            // LIST) are not set, they cannot be set in the current conversion
            // procedure.
            // writeExtraCardinalityRestrictionsForListOfList(attr, out);
            return;
        } else {
            if (minCard == maxCard && maxCard >= 1) {
                // [3:3]
                // explicitly qualified cardinality
                // writeQualCardRestr(attr.getType().getName() + "_List_List",
                // attr.getName(), out, start);
                writeMinCardRestr(className + "_List_List", attrName, out, minCard, asEntity);
                writeMaxCardRestr(className + "_List_EmptyList", attrName, out, maxCard, asEntity);
            } else if (minCard < maxCard) {
                // [1:2]
                // min-max qualified cardinality
                if (maxCard > 1)
                    writeMaxCardRestr(className + "_List_EmptyList", attrName, out, maxCard, asEntity);
                if (minCard >= 1)
                    writeMinCardRestr(className + "_List_List", attrName, out, minCard, asEntity);
            } else {
                // This is not supposed to happen
                System.out.println("WARNING - IMPOSSIBLE: found mincardinality restriction that is greater than maxcardinality restriction for :" + attrName);
            }
            // extra cardinality restrictions (for the second LIST in a LIST OF
            // LIST) are not set, they cannot be set in the current conversion
            // procedure.
            // writeExtraCardinalityRestrictionsForListOfList(attr, out);
            return;
        }
    }

    @SuppressWarnings("unused")
    private void writeQualCardRestr(String className, String attrName, BufferedWriter out, int qualCard) throws IOException {
        out.write(" ;\r\n");
        out.write("\trdfs:subClassOf" + "\r\n");
        out.write("\t\t[" + "\r\n");
        out.write("\t\t\trdf:type owl:Restriction ; " + "\r\n");
        out.write("\t\t\towl:onProperty ifc:" + attrName + " ;" + "\r\n");
        out.write("\t\t\towl:allValuesFrom" + "\r\n");
        String tab = "\t\t\t";
        for (int i = 0; i < qualCard - 1; i++) {
            tab += "\t";
            out.write(tab + "[" + "\r\n");
            out.write(tab + "\trdf:type owl:Restriction ; " + "\r\n");
            out.write(tab + "\towl:onProperty list:hasNext ; " + "\r\n");
            out.write(tab + "\towl:someValuesFrom " + "\r\n");
        }

        tab += "\t";
        out.write(tab + "[" + "\r\n");
        out.write(tab + "\trdf:type owl:Restriction ; " + "\r\n");
        out.write(tab + "\towl:onProperty list:hasNext ; " + "\r\n");
        out.write(tab + "\towl:onClass " + className + " ;" + "\r\n");
        out.write(tab + "\towl:qualifiedCardinality \"0\"^^xsd:nonNegativeInteger " + "\r\n");

        tab = tab.substring(1);
        out.write(tab + "\t]" + "\r\n");

        for (int i = 0; i < qualCard - 1; i++) {
            tab = tab.substring(1);
            out.write(tab + "\t]" + "\r\n");
        }
        out.write("\t\t]");
    }

    private void writeMinCardRestr(String className, String attrName, BufferedWriter out, int minCard, boolean asEntity) throws IOException {
        out.write(" ;\r\n");
        out.write("\trdfs:subClassOf" + "\r\n");
        String tab = "\t";
        if (asEntity == true) {
            out.write("\t\t[" + "\r\n");
            out.write("\t\t\trdf:type owl:Restriction ; " + "\r\n");
            out.write("\t\t\towl:onProperty ifc:" + attrName + " ;\r\n");
            out.write("\t\t\towl:allValuesFrom" + "\r\n");
            tab += "\t\t";
        }
        for (int i = 0; i <= minCard - 1; i++) {
            tab += "\t";
            out.write(tab + "[" + "\r\n");
            out.write(tab + "\trdf:type owl:Restriction ; " + "\r\n");
            out.write(tab + "\towl:onProperty list:hasNext ; " + "\r\n");
            out.write(tab + "\towl:someValuesFrom ");
        }
        out.write(className + "\r\n");
        for (int i = 0; i <= minCard - 1; i++) {
            tab = tab.substring(1);
            out.write(tab + "\t]" + "\r\n");
        }
        if (asEntity == true) {
            out.write("\t\t]");
        }
    }

    private void writeMaxCardRestr(String className, String attrName, BufferedWriter out, int maxCard, boolean asEntity) throws IOException {
        out.write(" ;\r\n");
        out.write("\trdfs:subClassOf" + "\r\n");
        String tab = "\t";
        if (asEntity == true) {
            out.write("\t\t[" + "\r\n");
            out.write("\t\t\trdf:type owl:Restriction ; " + "\r\n");
            out.write("\t\t\towl:onProperty ifc:" + attrName + " ;" + "\r\n");
            out.write("\t\t\towl:allValuesFrom" + "\r\n");
            tab += "\t\t";
        }
        for (int i = 0; i < maxCard - 1; i++) {
            tab += "\t";
            out.write(tab + "[" + "\r\n");
            out.write(tab + "\trdf:type owl:Restriction ; " + "\r\n");
            out.write(tab + "\towl:onProperty list:hasNext ; " + "\r\n");
            out.write(tab + "\towl:allValuesFrom " + "\r\n");
        }

        tab += "\t";
        out.write(tab + "[" + "\r\n");
        out.write(tab + "\trdf:type owl:Restriction ; " + "\r\n");
        out.write(tab + "\towl:onProperty list:hasNext ; " + "\r\n");
        out.write(tab + "\towl:onClass " + className + " ;" + "\r\n");
        out.write(tab + "\towl:qualifiedCardinality \"1\"^^xsd:nonNegativeInteger " + "\r\n");

        tab = tab.substring(1);
        out.write(tab + "\t]" + "\r\n");

        for (int i = 0; i < maxCard - 1; i++) {
            tab = tab.substring(1);
            out.write(tab + "\t]" + "\r\n");
        }
        if (asEntity == true)
            out.write("\t\t]");
    }

    @SuppressWarnings("unused")
    private void writeExtraCardinalityRestrictionsForListOfList(AttributeVO attr, BufferedWriter out) throws IOException {
        if (attr.getMinCardListOfList() == -1 && attr.getMaxCardListOfList() == -1) {
            System.out.println("WARNING: [?,?] found for : " + attr.getLowerCaseName() + " - " + attr.getType().getName());
        } else if (attr.getMinCardListOfList() == -1 && attr.getMaxCardListOfList() != -1) {
            // [?:2]
            // This is not supposed to happen
            System.out.println("WARNING - IMPOSSIBLE: found 'unlimited' mincardinality restriction combined with a bounded maxcardinality restriction for :" + attr.getLowerCaseName());
        } else if (attr.getMinCardListOfList() != -1 && attr.getMaxCardListOfList() == -1) {
            int start = attr.getMinCardListOfList();
            // [2:?]
            if (start > 1) {
                // do nothing
            }
        } else {
            int start = attr.getMinCardListOfList();
            int end = attr.getMaxCardListOfList();
            if (start == end && end > 1) {
                // do nothing
            } else if (start < end) {
                // [1:2]
                // min-max qualified cardinality

                if (end > 1) {
                    // do nothing
                }
                if (start > 1) {
                    // do nothing
                }
            } else {
                // This is not supposed to happen
                System.out.println("WARNING - IMPOSSIBLE: found mincardinality restriction that is greater than maxcardinality restriction for :" + attr.getName());
            }
        }
    }
}