/*
 * Copyright 2016, 2022, 2023 Pieter Pauwels, Ghent University; Jyrki Oraskari, Aalto University/ RWTH Aachen; Lewis John McGibbney, Apache
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License atf
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.ugent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.buildingsmart.tech.ifcowl.ExpressReader;
import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;

import fi.ni.rdf.Namespace;

public class RDFWriter {
    private static final Logger LOG = LoggerFactory.getLogger(RDFWriter.class);
    
    // EXPRESS basis
    private final Map<String, EntityVO> ent;
    private final Map<String, TypeVO> typ;

    private final OntModel ontModel;
    private final String baseURI;
    private final String ontNS;

    // Taking care of avoiding duplicate resources
    private final Map<String, Resource> propertyResourceMap = new HashMap<>();
    private final Map<String, Resource> resourceMap = new HashMap<>();

    private boolean removeDuplicates = false;
    private boolean hasPerformanceBoost;

    private int idCounter = 0;
    private Map<Long, IFCVO> linemap = null;

    public RDFWriter(OntModel ontModel, InputStream inputStream, String baseURI, Map<String, EntityVO> ent, Map<String, TypeVO> typ, String ontURI, boolean hasPerformanceBoost) {
        this.ontModel = ontModel;
        this.baseURI = baseURI;
        this.ent = ent;
        this.typ = typ;
        this.ontNS = ontURI + "#";
        this.hasPerformanceBoost = hasPerformanceBoost;
        IfcSpfParser parser = new IfcSpfParser(inputStream);
        parser.readModel();
        LOG.info("Model parsed");

        if (removeDuplicates) {
            try {
                parser.resolveDuplicates();
                boolean parsedSuccessfully = parser.mapEntries();

                if (!parsedSuccessfully)
                    return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.idCounter = parser.getIdCounter();
        this.linemap = parser.getLinemap();

    }

    public void parseModel2Stream(OutputStream out) {
        StreamRDF ttlWriter = StreamRDFWriter.getWriterStream(out, RDFFormat.TURTLE_BLOCKS, Context.emptyContext());
        ttlWriter.base(baseURI);
        ttlWriter.prefix("ifc", ontNS);
        ttlWriter.prefix("inst", baseURI);
        ttlWriter.prefix("list", Namespace.LIST);
        ttlWriter.prefix("express", Namespace.EXPRESS);
        ttlWriter.prefix("rdf", Namespace.RDF);
        ttlWriter.prefix("xsd", Namespace.XSD);
        ttlWriter.prefix("owl", Namespace.OWL);
        ttlWriter.start();

        ttlWriter.triple(Triple.create(NodeFactory.createURI(baseURI), RDF.type.asNode(), OWL.Ontology.asNode()));
        ttlWriter.triple(Triple.create(NodeFactory.createURI(baseURI), OWL.imports.asNode(), NodeFactory.createURI(ontNS)));

        
       

        LOG.info("Creating instances");
        createInstances(ttlWriter);

        // Save memory
        linemap.clear();
        linemap = null;

        ttlWriter.finish();
    }

    private void createInstances(StreamRDF ttlWriter) {
        for (Map.Entry<Long, IFCVO> entry : this.linemap.entrySet()) {
            IFCVO ifcLineEntry = entry.getValue();
            String typeName = null;
            if (this.ent.containsKey(ifcLineEntry.getName()))
                typeName = this.ent.get(ifcLineEntry.getName()).getName();
            else if (this.typ.containsKey(ifcLineEntry.getName()))
                typeName = this.typ.get(ifcLineEntry.getName()).getName();
            
            if(typeName==null)
            {
                System.err.print("Type not found");
                continue;
            }

            // removes only first level triples
            if (filter4Perfoemance(typeName))
                continue;

            OntClass cl = ontModel.getOntClass(ontNS + typeName);
            Resource r = getResource(ttlWriter,baseURI + typeName + "_" + ifcLineEntry.getLineNum(), cl);
            if (r == null) {
                continue;  
            }
            extractProperties(ttlWriter, ifcLineEntry, r);
        }
        // The map is used only to avoid duplicates.
        // So, it can be cleared here
        this.propertyResourceMap.clear();
    }

    TypeVO typeRemembrance = null;

    private void extractProperties(StreamRDF ttlWriter, IFCVO ifcLineEntry, Resource r) {

        EntityVO evo = ent.get(ExpressReader.formatClassName(ifcLineEntry.getName()));
        TypeVO tvo = typ.get(ExpressReader.formatClassName(ifcLineEntry.getName()));

        if (evo == null && tvo != null) {
            // working with the EXPRESS typee

            this.typeRemembrance = null;
            for (Object o : ifcLineEntry.getObjectList()) {

                if (Character.class.isInstance(o)) {
                    if ((Character) o != ',') {
                        LOG.error("*ERROR 17*: We found a character that is not a comma. That should not be possible!");
                    }
                } else if (String.class.isInstance(o)) {
                    LOG.warn("*WARNING 1*: extractProperties 1: unhandled type property found.");
                } else if (IFCVO.class.isInstance(o)) {
                    LOG.warn("*WARNING 2*: extractProperties 2: unhandled type property found.");
                } else if (LinkedList.class.isInstance(o)) {
                    LOG.info("extractProperties 3 - fillPropertiesHandleListObject(tvo)");
                    extractPropertiesHandleListObject(ttlWriter, r, tvo, o);
                }
                else 
                    LOG.info("extractProperties 4 - unhandled type property found");
            }
        }
        else
        if (evo != null) {
            // working with an ENTITY
            final String subject = evo.getName() + "_" + ifcLineEntry.getLineNum();

            this.typeRemembrance = null;
            int attributePointer = 0;
            for (Object o : ifcLineEntry.getObjectList()) {

                if (Character.class.isInstance(o)) {
                    if ((Character) o != ',') {
                        LOG.error("*ERROR 18*: We found a character that is not a comma. That should not be possible!");
                    }
                } else if (String.class.isInstance(o)) {
                    attributePointer = extractPropertiesHandleStringObject(ttlWriter, r, evo, subject, attributePointer, o);
                } else if (IFCVO.class.isInstance(o)) {
                    attributePointer = extractPropertiesHandleIfcObject(ttlWriter, r, evo, attributePointer, o);
                } else if (LinkedList.class.isInstance(o)) {
                    attributePointer = extractPropertiesHandleListObject(ttlWriter, r, evo, attributePointer, o);
                }
            }
        }
    }

    // --------------------------------------
    // 6 MAIN Extract PROPERTIES METHODS
    // --------------------------------------

    private int extractPropertiesHandleStringObject(StreamRDF ttlWriter, Resource r, EntityVO evo, String subject, int attributePointer, Object o) {
        if (!((String) o).equals("$") && !((String) o).equals("*")) {

            if (typ.get(ExpressReader.formatClassName((String) o)) == null) {
                if ((evo != null) && (evo.getDerivedAttributeList() != null)) {
                    if (evo.getDerivedAttributeList().size() <= attributePointer) {
                        LOG.error("*ERROR 4*: Entity in IFC files has more attributes than it is allowed have: " + subject);
                        attributePointer++;
                        return attributePointer;
                    }

                    final String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                    final String literalString = IfcOWLUtils.filterExtras((String) o);

                    OntProperty p = ontModel.getOntProperty(propURI);
                    OntResource range = p.getRange();
                    if (range.isClass()) {
                        if (range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "ENUMERATION"))) {
                            addEnumProperty(ttlWriter, r, p, range, literalString);
                        } else if (range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "SELECT"))) {
                            createLiteralProperty(ttlWriter,r, p, range, literalString);
                        } else if (range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                            LOG.warn("*WARNING 5*: found LIST property (but doing nothing with it): " + subject + " -- " + p + " - " + range.getLocalName() + " - " + literalString);
                        } else {
                            createLiteralProperty(ttlWriter,r, p, range, literalString);
                        }
                    } else {
                        LOG.warn("*WARNING 7*: found other kind of property: " + p + " - " + range.getLocalName());
                    }
                } else {
                    LOG.warn("*WARNING 8*: Nothing happened. Not sure if this is good or bad, possible or not.");
                }
                attributePointer++;
            } else {
                typeRemembrance = typ.get(ExpressReader.formatClassName((String) o));
            }
        } else
            attributePointer++;
        return attributePointer;
    }

    private int extractPropertiesHandleIfcObject(StreamRDF ttlWriter,Resource r, EntityVO evo, int attributePointer, Object o) {
        if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

            final String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
            EntityVO evorange = ent.get(ExpressReader.formatClassName(((IFCVO) o).getName()));

            OntProperty p = ontModel.getOntProperty(propURI);
            OntResource rclass = ontModel.getOntResource(ontNS + evorange.getName());

            Resource r1 = getResource(ttlWriter,baseURI + evorange.getName() + "_" + ((IFCVO) o).getLineNum(), rclass);
            ttlWriter.triple(Triple.create(r.asNode(), p.asNode(), r1.asNode()));
        } else {
            LOG.warn("*WARNING 3*: Nothing happened. Not sure if this is good or bad, possible or not.");
        }
        attributePointer++;
        return attributePointer;
    }

    private int extractPropertiesHandleListObject(StreamRDF ttlWriter,Resource r, EntityVO evo, int attributePointer, Object o) {

        @SuppressWarnings("unchecked")
        final LinkedList<Object> tmpList = (LinkedList<Object>) o;
        LinkedList<String> literals = new LinkedList<>();
        LinkedList<Resource> listRemembranceResources = new LinkedList<>();
        LinkedList<IFCVO> ifcVOs = new LinkedList<>();

        // process list
        int tmpList_size = tmpList.size();
        for (int j = 0; j < tmpList_size; j++) {
            Object o1 = tmpList.get(j);
            if (Character.class.isInstance(o1)) {
                Character c = (Character) o1;
                if (c != ',') {
                    LOG.error("*ERROR 12*: We found a character that is not a comma. That is odd. Check!");
                }
            } else if (String.class.isInstance(o1)) {
                TypeVO t = typ.get(ExpressReader.formatClassName((String) o1));
                if (typeRemembrance == null) {
                    if (t != null) {
                        typeRemembrance = t;
                    } else {
                        literals.add(IfcOWLUtils.filterExtras((String) o1));
                    }
                } else {
                    if (t != null) {
                        if (t == typeRemembrance) {
                            // Ignore and continue with life
                        } else {
                            // Panic
                            LOG.warn("*WARNING 37*: Found two different types in one list. This is worth checking.");
                        }
                    } else {
                        literals.add(IfcOWLUtils.filterExtras((String) o1));
                    }
                }
            } else if (IFCVO.class.isInstance(o1)) {
                if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

                    String propURI = evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                    OntProperty p = ontModel.getOntProperty(ontNS + propURI);
                    OntResource typerange = p.getRange();

                    if (typerange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                        // EXPRESS LISTs
                        String listvaluepropURI = ontNS + typerange.getLocalName().substring(0, typerange.getLocalName().length() - 5);
                        OntResource listrange = ontModel.getOntResource(listvaluepropURI);

                        if (listrange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                            LOG.error("*ERROR 22*: Found supposedly unhandled ListOfList, but this should not be possible.");
                        } else {
                            fillClassInstanceList(ttlWriter,tmpList, typerange, p, r);
                            j = tmpList.size() - 1;
                        }
                    } else {
                        EntityVO evorange = ent.get(ExpressReader.formatClassName(((IFCVO) o1).getName()));
                        OntResource rclass = ontModel.getOntResource(ontNS + evorange.getName());

                        Resource r1 = getResource(ttlWriter,baseURI + evorange.getName() + "_" + ((IFCVO) o1).getLineNum(), rclass);
                        ttlWriter.triple(Triple.create(r.asNode(), p.asNode(), r1.asNode()));
                        LOG.info("*OK 5*: added property: " + r.getLocalName() + " - " + p.getLocalName() + " - " + r1.getLocalName());
                    }
                } else {
                    LOG.warn("*WARNING 13*: Nothing happened. Not sure if this is good or bad, possible or not.");
                }
            } else if (LinkedList.class.isInstance(o1)) {
                if (typeRemembrance != null) {
                    @SuppressWarnings("unchecked")
                    LinkedList<Object> tmpListInList = (LinkedList<Object>) o1;
                    int tmpListInList_size = tmpListInList.size();
                    for (int jj = 0; jj < tmpListInList_size; jj++) {
                        Object o2 = tmpListInList.get(jj);
                        if (Character.class.isInstance(o2)) {
                            if ((Character) o2 != ',') {
                                LOG.error("*ERROR 20*: We found a character that is not a comma. That should not be possible");
                            }
                        } else if (String.class.isInstance(o2)) {
                            literals.add(IfcOWLUtils.filterExtras((String) o2));
                        } else if (IFCVO.class.isInstance(o2)) {
                            // Lists of IFC entities
                            LOG.warn("*WARNING 30: Nothing happened. Not sure if this is good or bad, possible or not.");
                        } else if (LinkedList.class.isInstance(o2)) {
                            // this happens only for types that are equivalent
                            // to lists (e.g. IfcLineIndex in IFC4_ADD1)
                            // in this case, the elements of the list should be
                            // treated as new instances that are equivalent to
                            // the correct lists
                            @SuppressWarnings("unchecked")
                            LinkedList<Object> tmpListInListInList = (LinkedList<Object>) o2;
                            int tmpListInListInList_size = tmpListInListInList.size();
                            for (int jjj = 0; jjj < tmpListInListInList_size; jjj++) {
                                Object o3 = tmpListInListInList.get(jjj);
                                if (Character.class.isInstance(o3)) {
                                    if ((Character) o3 != ',') {
                                        LOG.error("*ERROR 24*: We found a character that is not a comma. That should not be possible");
                                    }
                                } else if (String.class.isInstance(o3)) {
                                    literals.add(IfcOWLUtils.filterExtras((String) o3));
                                } else {
                                    LOG.warn("*WARNING 31: Nothing happened. Not sure if this is good or bad, possible or not.");
                                }
                            }

                            // exception. when a list points to a number of
                            // linked lists, it could be that there are multiple
                            // different entities are referenced
                            // example: #308=
                            // IFCINDEXEDPOLYCURVE(#309,(IFCLINEINDEX((1,2)),IFCARCINDEX((2,3,4)),IFCLINEINDEX((4,5)),IFCARCINDEX((5,6,7))),.F.);
                            // in this case, it is better to immediately print
                            // all relevant entities and properties for each
                            // case (e.g. IFCLINEINDEX((1,2))),
                            // and reset typeremembrance for the next case (e.g.
                            // IFCARCINDEX((4,5))).

                            if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

                                OntClass cl = ontModel.getOntClass(ontNS + typeRemembrance.getName());
                                Resource r1 = getResource(ttlWriter,baseURI + typeRemembrance.getName() + "_" + idCounter, cl);
                                idCounter++;
                                OntResource range = ontModel.getOntResource(ontNS + typeRemembrance.getName());

                                // finding listrange
                                String[] primTypeArr = typeRemembrance.getPrimarytype().split(" ");
                                String primType = ontNS + primTypeArr[primTypeArr.length - 1].replace(";", "");
                                OntResource listrange = ontModel.getOntResource(primType);

                                List<Object> literalObjects = new ArrayList<>();
                                literalObjects.addAll(literals);
                                addDirectRegularListProperty(ttlWriter,r1, range, listrange, literalObjects, 0);

                                // put relevant top list items in a list, which
                                // can then be parsed at the end of this method
                                listRemembranceResources.add(r1);
                            }

                            typeRemembrance = null;
                            literals.clear();
                        } else {
                            LOG.warn("*WARNING 35: Nothing happened. Not sure if this is good or bad, possible or not.");
                        }
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    LinkedList<Object> tmpListInList = (LinkedList<Object>) o1;
                    int tmpListInList_size = tmpListInList.size();
                    for (int jj = 0; jj < tmpListInList_size; jj++) {
                        Object o2 = tmpListInList.get(jj);
                        if (Character.class.isInstance(o2)) {
                            if ((Character) o2 != ',') {
                                LOG.error("*ERROR 21*: We found a character that is not a comma. That should not be possible");
                            }
                        } else if (String.class.isInstance(o2)) {
                            literals.add(IfcOWLUtils.filterExtras((String) o2));
                        } else if (IFCVO.class.isInstance(o2)) {
                            ifcVOs.add((IFCVO) o2);
                        } else if (LinkedList.class.isInstance(o2)) {
                            LOG.error("*ERROR 19*: Found List of List of List. Code cannot handle that.");
                        } else {
                            LOG.warn("*WARNING 32*: Nothing happened. Not sure if this is good or bad, possible or not.");
                        }
                    }
                    if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

                        String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                        OntProperty p = ontModel.getOntProperty(propURI);
                        OntClass typerange = p.getRange().asClass();

                        if (typerange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                            String listvaluepropURI = typerange.getLocalName().substring(0, typerange.getLocalName().length() - 5);
                            OntResource listrange = ontModel.getOntResource(ontNS + listvaluepropURI);
                            Resource r1 = getResource(ttlWriter,baseURI + listvaluepropURI + "_" + idCounter, listrange);
                            idCounter++;
                            List<Object> objects = new ArrayList<>();
                            if (!ifcVOs.isEmpty()) {
                                objects.addAll(ifcVOs);
                                OntResource listcontentrange = IfcOWLUtils.getListContentType(listrange.asClass(),this.ontModel,this.ontNS);
                                addDirectRegularListProperty(ttlWriter,r1, listrange, listcontentrange, objects, 1);
                            } else if (!literals.isEmpty()) {
                                objects.addAll(literals);
                                OntResource listcontentrange = IfcOWLUtils.getListContentType(listrange.asClass(),this.ontModel,this.ontNS);
                                addDirectRegularListProperty(ttlWriter,r1, listrange, listcontentrange, objects, 0);
                            }
                            listRemembranceResources.add(r1);
                        } else {
                            LOG.error("*ERROR 23*: Impossible: found a list that is actually not a list.");
                        }
                    }

                    literals.clear();
                    ifcVOs.clear();
                }
            } else {
                LOG.error("*ERROR 11*: We found something that is not an IFC entity, not a list, not a string, and not a character. Check!");
            }
        }

        // interpret parse
        if (!literals.isEmpty()) {
            String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
            OntProperty p = ontModel.getOntProperty(propURI);
            OntResource typerange = p.getRange();
            if (typeRemembrance != null) {
                if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {
                    if (typerange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList")))
                        addRegularListProperty(ttlWriter,r, p, literals, typeRemembrance);
                    else {
                        addSinglePropertyFromTypeRemembrance(ttlWriter,r, p, literals.getFirst(), typeRemembrance);
                        if (literals.size() > 1) {
                            LOG.warn("*WARNING 37*: We are ignoring a number of literal values here.");
                        }
                    }
                } else {
                    LOG.warn("*WARNING 15*: Nothing happened. Not sure if this is good or bad, possible or not.");
                }
                typeRemembrance = null;
            } else if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {
                if (typerange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList")))
                    addRegularListProperty(ttlWriter,r, p, literals, null);
                else {
                    int literals_size = literals.size();
                    for (int i = 0; i < literals_size; i++)
                        createLiteralProperty(ttlWriter,r, p, typerange, literals.get(i));
                }
            } else {
                LOG.warn("*WARNING 14*: Nothing happened. Not sure if this is good or bad, possible or not.");
            }
        }
        if (!listRemembranceResources.isEmpty()) {
            if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {
                String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                OntProperty p = ontModel.getOntProperty(propURI);
                addListPropertyToGivenEntities(ttlWriter,r, p, listRemembranceResources);
            }
        }

        attributePointer++;
        return attributePointer;
    }

    private void extractPropertiesHandleListObject(StreamRDF ttlWriter,Resource r, TypeVO tvo, Object o)  {
        @SuppressWarnings("unchecked")
        final LinkedList<Object> tmpList = (LinkedList<Object>) o;
        LinkedList<String> literals = new LinkedList<>();

        // process list
        int tmpList_size = tmpList.size();
        for (int j = 0; j < tmpList_size; j++) {
            Object o1 = tmpList.get(j);
            if (Character.class.isInstance(o1)) {
                Character c = (Character) o1;
                if (c != ',') {
                    LOG.error("*ERROR 13*: We found a character that is not a comma. That is odd. Check!");
                }
            } else if (String.class.isInstance(o1)) {
                if (typ.get(ExpressReader.formatClassName((String) o1)) != null && typeRemembrance == null) {
                    typeRemembrance = typ.get(ExpressReader.formatClassName((String) o1));
                } else
                    literals.add(IfcOWLUtils.filterExtras((String) o1));
            } else if (IFCVO.class.isInstance(o1)) {
                if ((tvo != null)) {
                    LOG.warn("*WARNING 16*: found TYPE that is equivalent to a list if IFC entities - below is the code used when this happens for ENTITIES with a list of ENTITIES");
                } else {
                    LOG.warn("*WARNING 19*: Nothing happened. Not sure if this is good or bad, possible or not.");
                }
            } else if (LinkedList.class.isInstance(o1) && typeRemembrance != null) {
                @SuppressWarnings("unchecked")
                LinkedList<Object> tmpListInlist = (LinkedList<Object>) o1;
                int tmpListInlist_size = tmpListInlist.size();
                for (int jj = 0; jj < tmpListInlist_size; jj++) {
                    Object o2 = tmpListInlist.get(jj);
                    if (String.class.isInstance(o2)) {
                        literals.add(IfcOWLUtils.filterExtras((String) o2));
                    } else {
                        LOG.warn("*WARNING 18*: Nothing happened. Not sure if this is good or bad, possible or not.");
                    }
                }
            } else {
                LOG.error("*ERROR 10*: We found something that is not an IFC entity, not a list, not a string, and not a character. Check!");
            }
        }

        // interpret parse
        if (literals.isEmpty()) {
            if (typeRemembrance != null) {
                if ((tvo != null)) {
                    LOG.warn("*WARNING 20*: this part of the code has not been checked - it can't be correct");

                    String[] primtypeArr = tvo.getPrimarytype().split(" ");
                    String primType = primtypeArr[primtypeArr.length - 1].replace(";", "") + "_" + primtypeArr[0].substring(0, 1).toUpperCase() + primtypeArr[0].substring(1).toLowerCase();
                    String typeURI = ontNS + primType;
                    OntResource range = ontModel.getOntResource(typeURI);
                    OntResource listrange = IfcOWLUtils.getListContentType(range.asClass(),this.ontModel,this.ontNS);
                    List<Object> literalObjects = new ArrayList<>();
                    literalObjects.addAll(literals);
                    addDirectRegularListProperty(ttlWriter,r, range, listrange, literalObjects, 0);
                } else {
                    LOG.warn("*WARNING 21*: Nothing happened. Not sure if this is good or bad, possible or not.");
                }
                typeRemembrance = null;
            } else if ((tvo != null)) {
                String[] primTypeArr = tvo.getPrimarytype().split(" ");
                String primType = primTypeArr[primTypeArr.length - 1].replace(";", "") + "_" + primTypeArr[0].substring(0, 1).toUpperCase() + primTypeArr[0].substring(1).toLowerCase();
                String typeURI = ontNS + primType;
                OntResource range = ontModel.getOntResource(typeURI);
                List<Object> literalObjects = new ArrayList<>();
                literalObjects.addAll(literals);
                OntResource listrange = IfcOWLUtils.getListContentType(range.asClass(),this.ontModel,this.ontNS);
                addDirectRegularListProperty(ttlWriter,r, range, listrange, literalObjects, 0);
            }
        }
    }

    // --------------------------------------
    // EVERYTHING TO DO WITH LISTS
    // --------------------------------------

    private void addSinglePropertyFromTypeRemembrance(StreamRDF ttlWriter,Resource r, OntProperty p, String literalString, TypeVO typeremembrance) {
        OntResource range = ontModel.getOntResource(ontNS + typeremembrance.getName());

        if (range.isClass()) {
            if (range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "ENUMERATION"))) {
                // Check for ENUM
                addEnumProperty(ttlWriter,r, p, range, literalString);
            } else if (range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.EXPRESS + "SELECT"))) {
                createLiteralProperty(ttlWriter,r, p, range, literalString);
            } else if (range.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                // Check for LIST
                LOG.warn("*WARNING 24*: found LIST property (but doing nothing with it): " + p + " - " + range.getLocalName() + " - " + literalString);
            } else {
                createLiteralProperty(ttlWriter,r, p, range, literalString);
            }
        } else {
            LOG.warn("*WARNING 26*: found other kind of property: " + p + " - " + range.getLocalName());
        }
    }

    private void addEnumProperty(StreamRDF ttlWriter,Resource r, Property p, OntResource range, String literalString) {
        for (ExtendedIterator<? extends OntResource> instances = range.asClass().listInstances(); instances.hasNext();) {
            OntResource rangeInstance = instances.next();
            if (rangeInstance.getProperty(RDFS.label).getString().equalsIgnoreCase(IfcOWLUtils.filterPoints(literalString))) {
                ttlWriter.triple(Triple.create(r.asNode(), p.asNode(), rangeInstance.asNode()));
                LOG.info("*OK 2*: added ENUM statement " + r.getLocalName() + " - " + p.getLocalName() + " - " + rangeInstance.getLocalName());
                return;
            }
        }
        LOG.error("*ERROR 9*: did not find ENUM individual for " + literalString + "\r\nQuitting the application without output!");
    }

    private void addLiteralToResource(StreamRDF ttlWriter,Resource r1, OntProperty valueProp, String xsdType, String literalString){
        if ("integer".equalsIgnoreCase(xsdType))
            addLiteral(ttlWriter,r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDinteger));
        else if ("double".equalsIgnoreCase(xsdType))
            addLiteral(ttlWriter,r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDdouble));
        else if ("hexBinary".equalsIgnoreCase(xsdType))
            addLiteral(ttlWriter,r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDhexBinary));
        else if ("boolean".equalsIgnoreCase(xsdType)) {
            if (".F.".equalsIgnoreCase(literalString))
                addLiteral(ttlWriter,r1, valueProp, ResourceFactory.createTypedLiteral("false", XSDDatatype.XSDboolean));
            else if (".T.".equalsIgnoreCase(literalString))
                addLiteral(ttlWriter,r1, valueProp, ResourceFactory.createTypedLiteral("true", XSDDatatype.XSDboolean));
            else
                LOG.warn("*WARNING 10*: found odd boolean value: " + literalString);
        } else if ("logical".equalsIgnoreCase(xsdType)) {
            if (".F.".equalsIgnoreCase(literalString))
                addProperty(ttlWriter,r1, valueProp, ontModel.getResource(Namespace.EXPRESS + "FALSE"));
            else if (".T.".equalsIgnoreCase(literalString))
                addProperty(ttlWriter,r1, valueProp, ontModel.getResource(Namespace.EXPRESS + "TRUE"));
            else if (".U.".equalsIgnoreCase(literalString))
                addProperty(ttlWriter,r1, valueProp, ontModel.getResource(Namespace.EXPRESS + "UNKNOWN"));
            else
                LOG.warn("*WARNING 9*: found odd logical value: " + literalString);
        } else if ("string".equalsIgnoreCase(xsdType))
            addLiteral(ttlWriter,r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDstring));
        else
            addLiteral(ttlWriter,r1, valueProp, ResourceFactory.createTypedLiteral(literalString));

    }

    // LIST HANDLING
    private void addDirectRegularListProperty(StreamRDF ttlWriter,Resource r, OntResource range, OntResource listrange, List<Object> el, int mySwitch) {

        if (range.isClass()) {
            if (listrange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                LOG.warn("*WARNING 27*: Found unhandled ListOfList");
            } else {
                List<Resource> reslist = new ArrayList<>();
                // createrequirednumberofresources
                int el_size = el.size();
                for (int i = 0; i < el_size; i++) {
                    if (i == 0)
                        reslist.add(r);
                    else {
                        Resource r1 = getResource(ttlWriter,baseURI + range.getLocalName() + "_" + idCounter, range);
                        reslist.add(r1);
                        idCounter++;
                    }
                }

                if (mySwitch == 0) {
                    // bind the properties with literal values only if we are
                    // actually dealing with literals
                    List<String> literals = new ArrayList<>();
                    for (int i = 0; i < el_size; i++) {
                        literals.add((String) el.get(i));
                    }
                    addListInstanceProperties(ttlWriter,reslist, literals, listrange);
                } else {
                    int reslist_size = reslist.size();
                    for (int i = 0; i < reslist_size; i++) {
                        Resource r1 = reslist.get(i);
                        IFCVO vo = (IFCVO) el.get(i);
                        EntityVO evorange = ent.get(ExpressReader.formatClassName((vo).getName()));
                        OntResource rclass = ontModel.getOntResource(ontNS + evorange.getName());
                        Resource r2 = getResource(ttlWriter,baseURI + evorange.getName() + "_" + (vo).getLineNum(), rclass);
                        idCounter++;
                        ttlWriter.triple(Triple.create(r1.asNode(), ontModel.getOntProperty(Namespace.LIST + "hasContents").asNode(), r2.asNode()));

                        if (i < el.size() - 1) {
                            ttlWriter.triple(Triple.create(r1.asNode(), ontModel.getOntProperty(Namespace.LIST + "hasNext").asNode(), reslist.get(i + 1).asNode()));
                        }
                    }
                }
            }
        }
    }

    private void addRegularListProperty(StreamRDF ttlWriter,Resource r, OntProperty p, List<String> el, TypeVO typeRemembranceOverride) {
        OntResource range = p.getRange();
        if (range.isClass()) {
            OntResource listrange = IfcOWLUtils.getListContentType(range.asClass(),this.ontModel,this.baseURI);
            if (typeRemembranceOverride != null) {
                OntClass cla = ontModel.getOntClass(ontNS + typeRemembranceOverride.getName());
                listrange = cla;
            }

            if (listrange == null) {
                LOG.error("*ERROR 14*: We could not find what kind of content is expected in the LIST.");
            } else {
                if (listrange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                    LOG.warn("*WARNING 28*: Found unhandled ListOfList");
                } else {
                    List<Resource> reslist = new ArrayList<>();
                    // createrequirednumberofresources
                    int el_size = el.size();
                    for (int ii = 0; ii < el_size; ii++) {
                        Resource r1 = getResource(ttlWriter,baseURI + range.getLocalName() + "_" + idCounter, range);
                        reslist.add(r1);
                        idCounter++;
                        if (ii == 0) {
                            ttlWriter.triple(Triple.create(r.asNode(), p.asNode(), r1.asNode()));
                        }
                    }
                    // bindtheproperties
                    addListInstanceProperties(ttlWriter,reslist, el, listrange);
                }
            }
        }
    }

    private void createLiteralProperty(StreamRDF ttlWriter,Resource r, OntResource p, OntResource range, String literalString) {
        String xsdType = IfcOWLUtils.getXSDTypeFromRange(range,this.ontModel);
        if (xsdType == null) {
            xsdType = IfcOWLUtils.getXSDTypeFromRangeExpensiveMethod(range,this.ontModel);
        }
        if (xsdType != null) {
            String xsdTypeCAP = Character.toUpperCase(xsdType.charAt(0)) + xsdType.substring(1);
            OntProperty valueProp = ontModel.getOntProperty(Namespace.EXPRESS + "has" + xsdTypeCAP);
            String key = valueProp.toString() + ":" + xsdType + ":" + literalString;

            Resource r1 = propertyResourceMap.get(key);
            if (r1 == null) {
                r1 = ResourceFactory.createResource(baseURI + range.getLocalName() + "_" + idCounter);
                ttlWriter.triple(Triple.create(r1.asNode(), RDF.type.asNode(), range.asNode()));
                idCounter++;
                propertyResourceMap.put(key, r1);
                addLiteralToResource(ttlWriter,r1, valueProp, xsdType, literalString);
            }
            ttlWriter.triple(Triple.create(r.asNode(), p.asNode(), r1.asNode()));
        } else {
            LOG.error("*ERROR 1*: XSD type not found for: " + p + " - " + range.getURI() + " - " + literalString);
        }
    }

    private void addListPropertyToGivenEntities(StreamRDF ttlWriter,Resource r, OntProperty p, List<Resource> el){
        OntResource range = p.getRange();
        if (range.isClass()) {
            OntResource listrange = IfcOWLUtils.getListContentType(range.asClass(),this.ontModel,this.ontNS);

            if (listrange != null) {
                if (listrange.asClass().hasSuperClass(ontModel.getOntClass(Namespace.LIST + "OWLList"))) {
                    listrange = range;
                }
                int el_size = el.size();
                for (int i = 0; i < el_size; i++) {
                    Resource r1 = el.get(i);
                    Resource r2 = ResourceFactory.createResource(baseURI + range.getLocalName() + "_" + idCounter); // was
                    // listrange
                    ttlWriter.triple(Triple.create(r2.asNode(), RDF.type.asNode(), range.asNode()));
                    idCounter++;
                    Resource r3 = ResourceFactory.createResource(baseURI + range.getLocalName() + "_" + idCounter);

                    if (i == 0) {
                        ttlWriter.triple(Triple.create(r.asNode(), p.asNode(), r2.asNode()));
                    }
                    ttlWriter.triple(Triple.create(r2.asNode(), ontModel.getOntProperty(Namespace.LIST + "hasContents").asNode(), r1.asNode()));

                    if (i < el.size() - 1) {
                        ttlWriter.triple(Triple.create(r2.asNode(), ontModel.getOntProperty(Namespace.LIST + "hasNext").asNode(), r3.asNode()));
                    }
                }
            }
        }
    }

    private void fillClassInstanceList(StreamRDF ttlWriter,LinkedList<Object> tmpList, OntResource typerange, OntProperty p, Resource r) {
        List<Resource> reslist = new ArrayList<>();
        List<IFCVO> entlist = new ArrayList<>();

        // createrequirednumberofresources
        int tmpList_size = tmpList.size();
        for (int i = 0; i < tmpList_size; i++) {
            if (IFCVO.class.isInstance(tmpList.get(i))) {
                Resource r1 = getResource(ttlWriter,baseURI + typerange.getLocalName() + "_" + idCounter, typerange);
                reslist.add(r1);
                idCounter++;
                entlist.add((IFCVO) tmpList.get(i));
                if (i == 0) {
                    ttlWriter.triple(Triple.create(r.asNode(), p.asNode(), r1.asNode()));
                }
            }
        }

        addClassInstanceListProperties(ttlWriter,reslist, entlist);
    }

    private void addClassInstanceListProperties(StreamRDF ttlWriter,List<Resource> reslist, List<IFCVO> entlist) {
        OntProperty listp = ontModel.getOntProperty(Namespace.LIST + "hasContents");
        OntProperty isfollowed = ontModel.getOntProperty(Namespace.LIST + "hasNext");

        int reslist_size = reslist.size();
        for (int i = 0; i < reslist_size; i++) {
            Resource r = reslist.get(i);

            OntResource rclass = null;
            EntityVO evorange = ent.get(ExpressReader.formatClassName(entlist.get(i).getName()));
            if (evorange == null) {
                TypeVO typerange = typ.get(ExpressReader.formatClassName(entlist.get(i).getName()));
                rclass = ontModel.getOntResource(ontNS + typerange.getName());
                Resource r1 = getResource(ttlWriter,baseURI + typerange.getName() + "_" + entlist.get(i).getLineNum(), rclass);
                ttlWriter.triple(Triple.create(r.asNode(), listp.asNode(), r1.asNode()));
            } else {
                rclass = ontModel.getOntResource(ontNS + evorange.getName());
                Resource r1 = getResource(ttlWriter,baseURI + evorange.getName() + "_" + entlist.get(i).getLineNum(), rclass);
                ttlWriter.triple(Triple.create(r.asNode(), listp.asNode(), r1.asNode()));
            }

            if (i < reslist.size() - 1) {
                ttlWriter.triple(Triple.create(r.asNode(), isfollowed.asNode(), reslist.get(i + 1).asNode()));
            }
        }
    }

    private void addListInstanceProperties(StreamRDF ttlWriter,List<Resource> reslist, List<String> listelements, OntResource listrange) {
        String xsdType = IfcOWLUtils.getXSDTypeFromRange(listrange,this.ontModel);
        if (xsdType == null)
            xsdType = IfcOWLUtils.getXSDTypeFromRangeExpensiveMethod(listrange,this.ontModel);
        if (xsdType != null) {
            String xsdTypeCAP = Character.toUpperCase(xsdType.charAt(0)) + xsdType.substring(1);
            OntProperty valueProp = ontModel.getOntProperty(Namespace.EXPRESS + "has" + xsdTypeCAP);

            int reslist_size = reslist.size();
            for (int i = 0; i < reslist_size; i++) {
                Resource r = reslist.get(i);
                String literalString = listelements.get(i);
                String key = valueProp.toString() + ":" + xsdType + ":" + literalString;
                Resource r2 = propertyResourceMap.get(key);
                if (r2 == null) {
                    r2 = ResourceFactory.createResource(baseURI + listrange.getLocalName() + "_" + idCounter);
                    ttlWriter.triple(Triple.create(r2.asNode(), RDF.type.asNode(), listrange.asNode()));
                    idCounter++;
                    propertyResourceMap.put(key, r2);
                    addLiteralToResource(ttlWriter,r2, valueProp, xsdType, literalString);
                }
                ttlWriter.triple(Triple.create(r.asNode(), ontModel.getOntProperty(Namespace.LIST + "hasContents").asNode(), r2.asNode()));

                if (i < listelements.size() - 1) {
                    ttlWriter.triple(Triple.create(r.asNode(), ontModel.getOntProperty(Namespace.LIST + "hasNext").asNode(), reslist.get(i + 1).asNode()));
                }
            }
        } else {
            LOG.error("*ERROR 5*: XSD type not found for: " + listrange.getLocalName());
        }
    }

    private Resource getResource(StreamRDF ttlWriter,String uri, OntResource rclass) {
        Resource r = this.resourceMap.get(uri);
        if (r == null) {
            r = ResourceFactory.createResource(uri);
            this.resourceMap.put(uri, r);
            try {
                ttlWriter.triple(Triple.create(r.asNode(), RDF.type.asNode(), rclass.asNode()));
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("rclass: " + rclass);
                LOG.error("*ERROR 2*: getResource failed for " + uri);
                return null;
            }
        }
        return r;
    }
    public boolean isRemoveDuplicates() {
        return removeDuplicates;
    }

    public void setRemoveDuplicates(boolean removeDuplicates) {
        this.removeDuplicates = removeDuplicates;
    }

    private boolean filter4Perfoemance(String typeName) {
        if (this.hasPerformanceBoost) {
            if (typeName.equals("IfcFace"))
                return true;
            if (typeName.equals("IfcPolyLoop"))
                return true;
            if (typeName.equals("IfcCartesianPoint"))
                return true;
            if (typeName.equals("IfcOwnerHistory"))
                return true;
            if (typeName.equals("IfcRelAssociatesMaterial"))
                return true;
            if (typeName.equals("IfcExtrudedAreaSolid"))
                return true;
            if (typeName.equals("IfcCompositeCurve"))
                return true;
            if (typeName.equals("IfcSurfaceStyleRendering"))
                return true;
            if (typeName.equals("IfcStyledItem"))
                return true;
            if (typeName.equals("IfcShapeRepresentation"))
                return true;
        }
        return false;
    }

    private static void addLiteral(StreamRDF ttlWriter,Resource r, OntProperty valueProp, Literal l) {
        ttlWriter.triple(Triple.create(r.asNode(), valueProp.asNode(), l.asNode()));
    }

    private static void addProperty(StreamRDF ttlWriter,Resource r, OntProperty valueProp, Resource r1) {
        ttlWriter.triple(Triple.create(r.asNode(), valueProp.asNode(), r1.asNode()));
    }


}
