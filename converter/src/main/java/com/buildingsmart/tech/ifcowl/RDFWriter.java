package com.buildingsmart.tech.ifcowl;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

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
import org.lbd.ifc2lbd.EventBusService;
import org.lbd.ifc2lbd.messages.SystemStatusEvent;
import org.lbd.ns.Namespace;

import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;
import com.google.common.eventbus.EventBus;


/*
 * Copyright 2016 Pieter Pauwels, Ghent University; Jyrki Oraskari, Aalto University; Lewis John McGibbney, Apache
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

public class RDFWriter {
	private final EventBus eventBus = EventBusService.getEventBus();
    // input variables
    private final String baseURI;
    private final String ontNS;
    private static final String expressURI = "https://w3id.org/express";
    private static final String expressNS = expressURI + "#";
    private static final String listURI = "https://w3id.org/list";
    private static final String listNS = listURI + "#";

    // EXPRESS basis
    private final Map<String, EntityVO> ent;
    private final Map<String, TypeVO> typ;

    // conversion variables
    private int IDcounter = 0;
    private Map<Long, IFCVO> linemap = new HashMap<Long, IFCVO>();

    private StreamRDF ttlWriter;
    private InputStream inputStream;
    private final OntModel ontModel;
    private final OntModel expressModel;
    private final OntModel listModel;

    private IfcSpfReader myIfcReaderStream;

    // for removing duplicates in line entries
    private Map<String, Resource> listOfUniqueResources = new HashMap<String, Resource>();
    private Map<Long, Long> listOfDuplicateLineEntries = new HashMap<Long, Long>();

    // Taking care of avoiding duplicate resources
    private Map<String, Resource> propertyResourceMap = new HashMap<String, Resource>();
    private Map<String, Resource> resourceMap = new HashMap<String, Resource>();

    private boolean removeDuplicates = true;

    public RDFWriter(OntModel ontModel, OntModel expressModel, OntModel listModel, InputStream inputStream, String baseURI, Map<String, EntityVO> ent, Map<String, TypeVO> typ, String ontURI) {
        this.ontModel = ontModel;
        this.expressModel = expressModel;
        this.listModel = listModel;
        this.inputStream = inputStream;
        this.baseURI = baseURI;
        this.ent = ent;
        this.typ = typ;
        this.ontNS = ontURI + "#";
    }

    public void setIfcReader(IfcSpfReader r) {
        this.myIfcReaderStream = r;
    }

    public void parseModel2Stream(OutputStream out) throws IOException {
        ttlWriter = StreamRDFWriter.getWriterStream(out, RDFFormat.TURTLE_FLAT, Context.emptyContext);
        ttlWriter.base(baseURI);
        ttlWriter.prefix("ifcowl", ontNS);
        ttlWriter.prefix("inst", baseURI);
        ttlWriter.prefix("list", listNS);
        ttlWriter.prefix("express", expressNS);
        ttlWriter.prefix("rdf", Namespace.RDF);
        ttlWriter.prefix("xsd", Namespace.XSD);
        ttlWriter.prefix("owl", Namespace.OWL);
        ttlWriter.start();  

        ttlWriter.triple(new Triple(NodeFactory.createURI(baseURI), RDF.type.asNode(), OWL.Ontology.asNode()));
        ttlWriter.triple(new Triple(NodeFactory.createURI(baseURI), OWL.imports.asNode(), NodeFactory.createURI(ontNS)));

        // Read the whole file into a linemap Map object
        readModel();

   	    eventBus.post(new SystemStatusEvent("IFC model parsed"));


        if (removeDuplicates) {
            resolveDuplicates();
        }

        // map entries of the linemap Map object to the ontology Model and make
        // new instances in the model
        boolean parsedSuccessfully = mapEntries();

        if (!parsedSuccessfully)
            return;

   	    eventBus.post(new SystemStatusEvent("IFC line entries mapped, creating instances."));

        createInstances();

        // Save memory
        linemap.clear();
        linemap = null;

        ttlWriter.finish();
    }

    private void readModel() {
        try {
            DataInputStream in = new DataInputStream(inputStream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            try {
                String strLine;
                long i=0;
                while ((strLine = br.readLine()) != null) {
                	i++;
                	if(i%100000==0)
                   	 eventBus.post(new SystemStatusEvent("Read IFC line: "+i));
                    if (strLine.length() > 0) {
                        if (strLine.charAt(0) == '#') {
                            StringBuffer sb = new StringBuffer();
                            String stmp = strLine;
                            sb.append(stmp.trim());
                            while (!stmp.contains(";")) {
                                stmp = br.readLine();
                                if (stmp == null)
                                    break;
                                sb.append(stmp.trim());
                            }
                            // the whole IFC gets parsed, and everything ends up
                            // as IFCVO objects in the Map<Long, IFCVO> linemap
                            // variable
                            parseIfcLineStatement(sb.toString().substring(1));
                        }
                    }
                }
            } finally {
                br.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseIfcLineStatement(String line) {
        IFCVO ifcvo = new IFCVO();
        ifcvo.setFullLineAfterNum(line.substring(line.indexOf("=") + 1));
        int state = 0;
        StringBuffer sb = new StringBuffer();
        int clCount = 0;
        LinkedList<Object> current = (LinkedList<Object>) ifcvo.getObjectList();
        Stack<LinkedList<Object>> listStack = new Stack<LinkedList<Object>>();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            switch (state) {
                case 0:
                if (ch == '=') {
                    ifcvo.setLineNum(toLong(sb.toString()));
                    sb.setLength(0);
                    state++;
                    continue;
                } else if (Character.isDigit(ch))
                    sb.append(ch);
                    break;
                case 1: // (
                if (ch == '(') {
                    ifcvo.setName(sb.toString());
                    sb.setLength(0);
                    state++;
                    continue;
                } else if (ch == ';') {
                    ifcvo.setName(sb.toString());
                    sb.setLength(0);
                    state = Integer.MAX_VALUE;
                } else if (!Character.isWhitespace(ch))
                    sb.append(ch);
                    break;
                case 2: // (... line started and doing (...
                if (ch == '\'') {
                    state++;
                }
                if (ch == '(') {
                    listStack.push(current);
                    LinkedList<Object> tmp = new LinkedList<Object>();
                    if (sb.toString().trim().length() > 0)
                        current.add(sb.toString().trim());
                    sb.setLength(0);
                    current.add(tmp);
                    current = tmp;
                    clCount++;
                    // sb.append(ch);
                } else if (ch == ')') {
                    if (clCount == 0) {
                        if (sb.toString().trim().length() > 0)
                            current.add(sb.toString().trim());
                        sb.setLength(0);
                        state = Integer.MAX_VALUE; // line is done
                        continue;
                    } else {
                        if (sb.toString().trim().length() > 0)
                            current.add(sb.toString().trim());
                        sb.setLength(0);
                        clCount--;
                        current = listStack.pop();
                    }
                } else if (ch == ',') {
                    if (sb.toString().trim().length() > 0)
                        current.add(sb.toString().trim());
                    current.add(Character.valueOf(ch));

                    sb.setLength(0);
                } else {
                    sb.append(ch);
                }
                    break;
                case 3: // (...
                if (ch == '\'') {
                    state--;
                } else {
                    sb.append(ch);
                }
                    break;
                default:
                // Do nothing
            }
        }
        linemap.put(ifcvo.getLineNum(), ifcvo);
        IDcounter++;
    }

    private void resolveDuplicates() throws IOException {
        Map<String, IFCVO> listOfUniqueResources = new HashMap<String, IFCVO>();
        List<Long> entriesToRemove = new ArrayList<Long>();
        for (Map.Entry<Long, IFCVO> entry : linemap.entrySet()) {
            IFCVO vo = entry.getValue();
            String t = vo.getFullLineAfterNum();
            if (!listOfUniqueResources.containsKey(t))
                listOfUniqueResources.put(t, vo);
            else {
                // found duplicate
                entriesToRemove.add(entry.getKey());// linemap.remove(entry.getKey());
                listOfDuplicateLineEntries.put(vo.getLineNum(), listOfUniqueResources.get(t).getLineNum());
            }
        }
        if (myIfcReaderStream.logToFile)
            myIfcReaderStream.bw.write("MESSAGE: found and removed " + listOfDuplicateLineEntries.size() + " duplicates! \r\n");
        for (Long x : entriesToRemove) {
            linemap.remove(x);
        }
    }

    private boolean mapEntries() throws IOException {
        for (Map.Entry<Long, IFCVO> entry : linemap.entrySet()) {
            IFCVO vo = entry.getValue();

            // mapping properties to IFCVOs
            for (int i = 0; i < vo.getObjectList().size(); i++) {
                Object o = vo.getObjectList().get(i);
                if (Character.class.isInstance(o)) {
                    if ((Character) o != ',') {
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*ERROR 15*: We found a character that is not a comma. That should not be possible" + "\r\n");
                    }
                } else if (String.class.isInstance(o)) {
                    String s = (String) o;
                    if (s.length() < 1)
                        continue;
                    if (s.charAt(0) == '#') {
                        Object or = null;
                        if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
                            or = linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
                        else
                            or = linemap.get(toLong(s.substring(1)));

                        if (or == null) {
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*ERROR 6*: Reference to non-existing line number in line: #" + vo.getLineNum() + "=" + vo.getFullLineAfterNum()
                                                + "\r\nQuitting the application without output!\r\n ");
                            System.err.println("*ERROR 6*: Reference to non-existing line number in line: #" + vo.getLineNum() + " - " + vo.getFullLineAfterNum()
                                            + "\r\nQuitting the application without output!");
                            return false;
                        }
                        vo.getObjectList().set(i, or);
                    }
                } else if (LinkedList.class.isInstance(o)) {
                    @SuppressWarnings("unchecked")
                    LinkedList<Object> tmpList = (LinkedList<Object>) o;

                    for (int j = 0; j < tmpList.size(); j++) {
                        Object o1 = tmpList.get(j);
                        if (Character.class.isInstance(o)) {
                            if ((Character) o != ',') {
                                if (myIfcReaderStream.logToFile)
                                    myIfcReaderStream.bw.write("*ERROR 16*: We found a character that is not a comma. That should not be possible!" + "\r\n");
                            }
                        } else if (String.class.isInstance(o1)) {
                            String s = (String) o1;
                            if (s.length() < 1)
                                continue;
                            if (s.charAt(0) == '#') {
                                Object or = null;
                                if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
                                    or = linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
                                else
                                    or = linemap.get(toLong(s.substring(1)));
                                if (or == null) {
                                    if (myIfcReaderStream.logToFile)
                                        myIfcReaderStream.bw.write("*ERROR 7*: Reference to non-existing line number in line: #" + vo.getLineNum() + "=" + vo.getFullLineAfterNum()
                                                        + "\r\nQuitting the application without output!\r\n ");
                                    System.err.println("*ERROR 7*: Reference to non-existing line number in line: #" + vo.getLineNum() + " - " + vo.getFullLineAfterNum()
                                                    + "\r\nQuitting the application without output!");
                                    tmpList.set(j, "-");
                                    return false;
                                } else
                                    tmpList.set(j, or);
                            } else {
                                // list/set of values
                                tmpList.set(j, s);
                            }
                        } else if (LinkedList.class.isInstance(o1)) {
                            @SuppressWarnings("unchecked")
                            LinkedList<Object> tmp2List = (LinkedList<Object>) o1;
                            for (int j2 = 0; j2 < tmp2List.size(); j2++) {
                                Object o2 = tmp2List.get(j2);
                                if (String.class.isInstance(o2)) {
                                    String s = (String) o2;
                                    if (s.length() < 1)
                                        continue;
                                    if (s.charAt(0) == '#') {
                                        Object or = null;
                                        if (listOfDuplicateLineEntries.containsKey(toLong(s.substring(1))))
                                            or = linemap.get(listOfDuplicateLineEntries.get(toLong(s.substring(1))));
                                        else
                                            or = linemap.get(toLong(s.substring(1)));
                                        if (or == null) {
                                            if (myIfcReaderStream.logToFile)
                                                myIfcReaderStream.bw.write("*ERROR 8*: Reference to non-existing line number in line: #" + vo.getLineNum() + "=" + vo.getFullLineAfterNum()
                                                                + "\r\nQuitting the application without output!\r\n ");
                                            System.err.println("*ERROR 8*: Reference to non-existing line number in line: #" + vo.getLineNum() + " - " + vo.getFullLineAfterNum()
                                                            + "\r\nQuitting the application without output!");
                                            tmp2List.set(j2, "-");
                                            return false;
                                        } else
                                            tmp2List.set(j2, or);
                                    }
                                }
                            }
                            tmpList.set(j, tmp2List);
                        }
                    }
                }
            }
        }
        return true;
    }

    private void createInstances() throws IOException {
    	long i=0;
    	
        for (Map.Entry<Long, IFCVO> entry : linemap.entrySet()) {
        	i++;
        	if(i%100000==0)
           	   eventBus.post(new SystemStatusEvent("instance "+i));
            IFCVO ifcLineEntry = entry.getValue();
            String typeName = "";
            if (ent.containsKey(ifcLineEntry.getName()))
                typeName = ent.get(ifcLineEntry.getName()).getName();
            else if (typ.containsKey(ifcLineEntry.getName()))
                typeName = typ.get(ifcLineEntry.getName()).getName();
            OntClass cl = ontModel.getOntClass(ontNS + typeName);
            Resource r = getResource(baseURI + typeName + "_" + ifcLineEntry.getLineNum(), cl);
            if (r == null) {
                // *ERROR 2 already hit: we can safely stop
                return;
            }
            listOfUniqueResources.put(ifcLineEntry.getFullLineAfterNum(), r);

            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("-------------------------------" + "\r\n");
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write(r.getLocalName() + "\r\n");
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("-------------------------------" + "\r\n");

            fillProperties(ifcLineEntry, r, cl);
        }
        // The map is used only to avoid duplicates.
        // So, it can be cleared here
        propertyResourceMap.clear();
    }

    TypeVO typeRemembrance = null;

    private void fillProperties(IFCVO ifcLineEntry, Resource r, OntClass cl) throws IOException {

        EntityVO evo = ent.get(ExpressReader.formatClassName(ifcLineEntry.getName()));
        TypeVO tvo = typ.get(ExpressReader.formatClassName(ifcLineEntry.getName()));

        if (tvo == null && evo == null) {
            // This can actually never happen
            // Namely, if this is the case, then ERROR 2 should fire first,
            // after which the program stops
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*ERROR 3*: fillProperties 1 - Type nor entity exists: " + ifcLineEntry.getName() + "\r\n");
            System.err.println("ERROR 3*: fillProperties 1 - Type nor entity exists: " + ifcLineEntry.getName());
        }

        if (evo == null && tvo != null) {

            typeRemembrance = null;
            for (Object o : ifcLineEntry.getObjectList()) {

                if (Character.class.isInstance(o)) {
                    if ((Character) o != ',') {
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*ERROR 17*: We found a character that is not a comma. That should not be possible!" + "\r\n");
                    }
                } else if (String.class.isInstance(o)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 1*: fillProperties 2 - WARNING: unhandled type property found." + "\r\n");
                    System.out.println("*WARNING 1*: unhandled type property found.");
                } else if (IFCVO.class.isInstance(o)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 2*: fillProperties 2 - WARNING: unhandled type property found." + "\r\n");
                    System.out.println("*WARNING 2*: unhandled type property found.");
                } else if (LinkedList.class.isInstance(o)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("fillProperties 3 - fillPropertiesHandleListObject(tvo)" + "\r\n");
                    fillPropertiesHandleListObject(r, tvo, o);
                }
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.flush();
            }
        }

        if (tvo == null && evo != null) {
            final String subject = evo.getName() + "_" + ifcLineEntry.getLineNum();

            typeRemembrance = null;
            int attributePointer = 0;
            for (Object o : ifcLineEntry.getObjectList()) {

                if (Character.class.isInstance(o)) {
                    if ((Character) o != ',') {
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*ERROR 18*: We found a character that is not a comma. That should not be possible!" + "\r\n");
                    }
                } else if (String.class.isInstance(o)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("fillProperties 4 - fillPropertiesHandleStringObject(evo)" + "\r\n");
                    attributePointer = fillPropertiesHandleStringObject(r, evo, subject, attributePointer, o);
                } else if (IFCVO.class.isInstance(o)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("fillProperties 5 - fillPropertiesHandleIfcObject(evo)" + "\r\n");
                    attributePointer = fillPropertiesHandleIfcObject(r, evo, attributePointer, o);
                } else if (LinkedList.class.isInstance(o)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("fillProperties 6 - fillPropertiesHandleListObject(evo)" + "\r\n");
                    attributePointer = fillPropertiesHandleListObject(r, evo, attributePointer, o);
                }
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.flush();
            }
        }

        if (myIfcReaderStream.logToFile)
            myIfcReaderStream.bw.flush();
    }

    // --------------------------------------
    // 6 MAIN FILLPROPERTIES METHODS
    // --------------------------------------

    private int fillPropertiesHandleStringObject(Resource r, EntityVO evo, String subject, int attributePointer, Object o) throws IOException {
        if (!((String) o).equals("$") && !((String) o).equals("*")) {

            if (typ.get(ExpressReader.formatClassName((String) o)) == null) {
                if ((evo != null) && (evo.getDerivedAttributeList() != null)) {
                    if (evo.getDerivedAttributeList().size() <= attributePointer) {
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*ERROR 4*: Entity in IFC files has more attributes than it is allowed have: " + subject + "\r\n");
                        System.err.println("*ERROR 4*: Entity in IFC files has more attributes than it is allowed have: " + subject);
                        attributePointer++;
                        return attributePointer;
                    }

                    final String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                    final String literalString = filterExtras((String) o);

                    OntProperty p = ontModel.getOntProperty(propURI);
                    OntResource range = p.getRange();
                    if (range.isClass()) {
                        if (range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "ENUMERATION"))) {
                            // Check for ENUM
                            addEnumProperty(r, p, range, literalString);
                        } else if (range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "SELECT"))) {
                            // Check for SELECT
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*OK 25*: found subClass of SELECT Class, now doing nothing with it: " + p + " - " + range.getLocalName() + " - " + literalString + "\r\n");
                            createLiteralProperty(r, p, range, literalString);
                        } else if (range.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                            // Check for LIST
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*WARNING 5*: found LIST property (but doing nothing with it): " + subject + " -- " + p + " - " + range.getLocalName() + " - "
                                                + literalString + "\r\n");
                        } else {
                            createLiteralProperty(r, p, range, literalString);
                        }
                    } else {
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*WARNING 7*: found other kind of property: " + p + " - " + range.getLocalName() + "\r\n");
                    }
                } else {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 8*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                }
                attributePointer++;
            } else {
                typeRemembrance = typ.get(ExpressReader.formatClassName((String) o));
               
            }
        } else
            attributePointer++;
        return attributePointer;
    }

    private int fillPropertiesHandleIfcObject(Resource r, EntityVO evo, int attributePointer, Object o) throws IOException {
        if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

            final String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
            EntityVO evorange = ent.get(ExpressReader.formatClassName(((IFCVO) o).getName()));

            OntProperty p = ontModel.getOntProperty(propURI);
            OntResource rclass = ontModel.getOntResource(ontNS + evorange.getName());

            Resource r1 = getResource(baseURI + evorange.getName() + "_" + ((IFCVO) o).getLineNum(), rclass);
            ttlWriter.triple(new Triple(r.asNode(), p.asNode(), r1.asNode()));
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*OK 1*: added property: " + r.getLocalName() + " - " + p.getLocalName() + " - " + r1.getLocalName() + "\r\n");
        } else {
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*WARNING 3*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
        }
        attributePointer++;
        return attributePointer;
    }

    @SuppressWarnings("unchecked")
    private int fillPropertiesHandleListObject(Resource r, EntityVO evo, int attributePointer, Object o) throws IOException {

        final LinkedList<Object> tmpList = (LinkedList<Object>) o;
        LinkedList<String> literals = new LinkedList<String>();
        LinkedList<Resource> listRemembranceResources = new LinkedList<Resource>();
        LinkedList<IFCVO> IFCVOs = new LinkedList<IFCVO>();

        // process list
        for (int j = 0; j < tmpList.size(); j++) {
            Object o1 = tmpList.get(j);
            if (Character.class.isInstance(o1)) {
                Character c = (Character) o1;
                if (c != ',') {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*ERROR 12*: We found a character that is not a comma. That is odd. Check!" + "\r\n");
                }
            } else if (String.class.isInstance(o1)) {
                TypeVO t = typ.get(ExpressReader.formatClassName((String) o1));
                if (typeRemembrance == null) {
                    if (t != null) {
                        typeRemembrance = t;
                    } else {
                        literals.add(filterExtras((String) o1));
                    }
                } else {
                    if (t != null) {
                        if (t == typeRemembrance) {
                            // Ignore and continue with life
                        } else {
                            // Panic
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*WARNING 37*: Found two different types in one list. This is worth checking.\r\n ");
                        }
                    } else {
                        literals.add(filterExtras((String) o1));
                    }
                }
            } else if (IFCVO.class.isInstance(o1)) {
                if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

                    String propURI = evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                    OntProperty p = ontModel.getOntProperty(ontNS + propURI);
                    OntResource typerange = p.getRange();

                    if (typerange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                        // EXPRESS LISTs
                        String listvaluepropURI = ontNS + typerange.getLocalName().substring(0, typerange.getLocalName().length() - 5);
                        OntResource listrange = ontModel.getOntResource(listvaluepropURI);

                        if (listrange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*ERROR 22*: Found supposedly unhandled ListOfList, but this should not be possible." + "\r\n");
                        } else {
                            fillClassInstanceList(tmpList, typerange, p, r);
                            j = tmpList.size() - 1;
                        }
                    } else {
                        // EXPRESS SETs
                        EntityVO evorange = ent.get(ExpressReader.formatClassName(((IFCVO) o1).getName()));
                        OntResource rclass = ontModel.getOntResource(ontNS + evorange.getName());

                        Resource r1 = getResource(baseURI + evorange.getName() + "_" + ((IFCVO) o1).getLineNum(), rclass);
                        ttlWriter.triple(new Triple(r.asNode(), p.asNode(), r1.asNode()));
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*OK 5*: added property: " + r.getLocalName() + " - " + p.getLocalName() + " - " + r1.getLocalName() + "\r\n");
                    }
                } else {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 13*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                }
            } else if (LinkedList.class.isInstance(o1)) {
                if (typeRemembrance != null) {
                    LinkedList<Object> tmpListInList = (LinkedList<Object>) o1;
                    for (int jj = 0; jj < tmpListInList.size(); jj++) {
                        Object o2 = tmpListInList.get(jj);
                        if (Character.class.isInstance(o2)) {
                            if ((Character) o2 != ',') {
                                if (myIfcReaderStream.logToFile)
                                    myIfcReaderStream.bw.write("*ERROR 20*: We found a character that is not a comma. That should not be possible" + "\r\n");
                            }
                        } else if (String.class.isInstance(o2)) {
                            literals.add(filterExtras((String) o2));
                        } else if (IFCVO.class.isInstance(o2)) {
                            // Lists of IFC entities
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*WARNING 30*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                            System.out.println("*WARNING 30: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                        } else if (LinkedList.class.isInstance(o2)) {
                            // this happens only for types that are equivalent
                            // to lists (e.g. IfcLineIndex in IFC4_ADD1)
                            // in this case, the elements of the list should be
                            // treated as new instances that are equivalent to
                            // the correct lists
                            LinkedList<Object> tmpListInListInList = (LinkedList<Object>) o2;
                            for (int jjj = 0; jjj < tmpListInListInList.size(); jjj++) {
                                Object o3 = tmpListInListInList.get(jjj);
                                if (Character.class.isInstance(o3)) {
                                    if ((Character) o3 != ',') {
                                        if (myIfcReaderStream.logToFile)
                                            myIfcReaderStream.bw.write("*ERROR 24*: We found a character that is not a comma. That should not be possible" + "\r\n");
                                    }
                                } else if (String.class.isInstance(o3)) {
                                    literals.add(filterExtras((String) o3));
                                } else {
                                    if (myIfcReaderStream.logToFile)
                                        myIfcReaderStream.bw.write("*WARNING 31*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                                    System.out.println("*WARNING 31: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                                }
                            }


                            if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

                                OntClass cl = ontModel.getOntClass(ontNS + typeRemembrance.getName());
                                Resource r1 = getResource(baseURI + typeRemembrance.getName() + "_" + IDcounter, cl);
                                IDcounter++;
                                OntResource range = ontModel.getOntResource(ontNS + typeRemembrance.getName());

                                // finding listrange
                                String[] primTypeArr = typeRemembrance.getPrimarytype().split(" ");
                                String primType = ontNS + primTypeArr[primTypeArr.length - 1].replace(";", "");
                                OntResource listrange = ontModel.getOntResource(primType);

                                List<Object> literalObjects = new ArrayList<Object>();
                                literalObjects.addAll(literals);
                                addDirectRegularListProperty(r1, range, listrange, literalObjects, 0);

                                listRemembranceResources.add(r1);
                            }

                            typeRemembrance = null;
                            literals.clear();
                        } else {
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*WARNING 35*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                            System.out.println("*WARNING 35: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                        }
                    }
                } else {
                    LinkedList<Object> tmpListInList = (LinkedList<Object>) o1;
                    for (int jj = 0; jj < tmpListInList.size(); jj++) {
                        Object o2 = tmpListInList.get(jj);
                        if (Character.class.isInstance(o2)) {
                            if ((Character) o2 != ',') {
                                if (myIfcReaderStream.logToFile)
                                    myIfcReaderStream.bw.write("*ERROR 21*: We found a character that is not a comma. That should not be possible" + "\r\n");
                            }
                        } else if (String.class.isInstance(o2)) {
                            literals.add(filterExtras((String) o2));
                        } else if (IFCVO.class.isInstance(o2)) {
                            IFCVOs.add((IFCVO) o2);
                        } else if (LinkedList.class.isInstance(o2)) {
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*ERROR 19*: Found List of List of List. Code cannot handle that." + "\r\n");
                            System.err.println("*ERROR 19*: Found List of List of List. Code cannot handle that.");
                        } else {
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*WARNING 32*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                            System.out.println("*WARNING 32: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                        }
                    }
                    if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {

                        String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                        OntProperty p = ontModel.getOntProperty(propURI);
                        OntClass typerange = p.getRange().asClass();

                        if (typerange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                            String listvaluepropURI = typerange.getLocalName().substring(0, typerange.getLocalName().length() - 5);
                            OntResource listrange = ontModel.getOntResource(ontNS + listvaluepropURI);
                            Resource r1 = getResource(baseURI + listvaluepropURI + "_" + IDcounter, listrange);
                            IDcounter++;
                            List<Object> objects = new ArrayList<Object>();
                            if (IFCVOs.size() > 0) {
                                objects.addAll(IFCVOs);
                                OntResource listcontentrange = getListContentType(listrange.asClass());
                                addDirectRegularListProperty(r1, listrange, listcontentrange, objects, 1);
                            } else if (literals.size() > 0) {
                                objects.addAll(literals);
                                OntResource listcontentrange = getListContentType(listrange.asClass());
                                addDirectRegularListProperty(r1, listrange, listcontentrange, objects, 0);
                            }
                            listRemembranceResources.add(r1);
                        } else {
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*ERROR 23*: Impossible: found a list that is actually not a list." + "\r\n");
                            System.err.println("*ERROR 23: Impossible: found a list that is actually not a list." + "\r\n");
                        }
                    }

                    literals.clear();
                    IFCVOs.clear();
                }
            } else {
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*ERROR 11*: We found something that is not an IFC entity, not a list, not a string, and not a character. Check!" + "\r\n");
                System.out.println("*ERROR 11*: We found something that is not an IFC entity, not a list, not a string, and not a character. Check!");
            }
        }

        // interpret parse
        if (literals.size() > 0) {
            String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
            OntProperty p = ontModel.getOntProperty(propURI);
            OntResource typerange = p.getRange();
            if (typeRemembrance != null) {
                if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {
                    if (typerange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList")))
                        addRegularListProperty(r, p, literals, typeRemembrance);
                    else {
                        addSinglePropertyFromTypeRemembrance(r, p, literals.getFirst(), typeRemembrance);
                        if (literals.size() > 1) {
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*WARNING 37*: We are ignoring a number of literal values here." + "\r\n");
                        }
                    }
                } else {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 15*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                }
                typeRemembrance = null;
            } else if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {
                if (typerange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList")))
                    addRegularListProperty(r, p, literals, null);
                else
                    for (int i = 0; i < literals.size(); i++)
                        createLiteralProperty(r, p, typerange, literals.get(i));
            } else {
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*WARNING 14*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
            }
        }
        if (listRemembranceResources.size() > 0) {
            if ((evo != null) && (evo.getDerivedAttributeList() != null) && (evo.getDerivedAttributeList().size() > attributePointer)) {
                String propURI = ontNS + evo.getDerivedAttributeList().get(attributePointer).getLowerCaseName();
                OntProperty p = ontModel.getOntProperty(propURI);
                addListPropertyToGivenEntities(r, p, listRemembranceResources);
            }
        }

        attributePointer++;
        return attributePointer;
    }

    @SuppressWarnings({ "unchecked" })
    private void fillPropertiesHandleListObject(Resource r, TypeVO tvo, Object o) throws IOException {

        final LinkedList<Object> tmpList = (LinkedList<Object>) o;
        LinkedList<String> literals = new LinkedList<String>();

        // process list
        for (int j = 0; j < tmpList.size(); j++) {
            Object o1 = tmpList.get(j);
            if (Character.class.isInstance(o1)) {
                Character c = (Character) o1;
                if (c != ',') {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*ERROR 13*: We found a character that is not a comma. That is odd. Check!" + "\r\n");
                }
            } else if (String.class.isInstance(o1)) {
                if (typ.get(ExpressReader.formatClassName((String) o1)) != null && typeRemembrance == null) {
                    typeRemembrance = typ.get(ExpressReader.formatClassName((String) o1));
                } else
                    literals.add(filterExtras((String) o1));
            } else if (IFCVO.class.isInstance(o1)) {
                if ((tvo != null)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw
                                        .write("*WARNING 16*: found TYPE that is equivalent to a list if IFC entities - below is the code used when this happens for ENTITIES with a list of ENTITIES"
                                                        + "\r\n");

                } else {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 19*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                }
            } else if (LinkedList.class.isInstance(o1) && typeRemembrance != null) {
                LinkedList<Object> tmpListInlist = (LinkedList<Object>) o1;
                for (int jj = 0; jj < tmpListInlist.size(); jj++) {
                    Object o2 = tmpListInlist.get(jj);
                    if (String.class.isInstance(o2)) {
                        literals.add(filterExtras((String) o2));
                    } else {
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*WARNING 18*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                    }
                }
            } else {
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*ERROR 10*: We found something that is not an IFC entity, not a list, not a string, and not a character. Check!" + "\r\n");
                System.out.println("*ERROR 10*: We found something that is not an IFC entity, not a list, not a string, and not a character. Check!");
            }
        }

        // interpret parse
        if (literals.size() > 0) {
            if (typeRemembrance != null) {
                if ((tvo != null)) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 20*: this part of the code has not been checked - it can't be correct" + "\r\n");

                    String[] primtypeArr = tvo.getPrimarytype().split(" ");
                    String primType = primtypeArr[primtypeArr.length - 1].replace(";", "") + "_" + primtypeArr[0].substring(0, 1).toUpperCase() + primtypeArr[0].substring(1).toLowerCase();
                    String typeURI = ontNS + primType;
                    OntResource range = ontModel.getOntResource(typeURI);
                    OntResource listrange = getListContentType(range.asClass());
                    List<Object> literalObjects = new ArrayList<Object>();
                    literalObjects.addAll(literals);
                    addDirectRegularListProperty(r, range, listrange, literalObjects, 0);

                } else {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 21*: Nothing happened. Not sure if this is good or bad, possible or not." + "\r\n");
                }
                typeRemembrance = null;
            } else if ((tvo != null)) {
                String[] primTypeArr = tvo.getPrimarytype().split(" ");
                String primType = primTypeArr[primTypeArr.length - 1].replace(";", "") + "_" + primTypeArr[0].substring(0, 1).toUpperCase() + primTypeArr[0].substring(1).toLowerCase();
                String typeURI = ontNS + primType;
                OntResource range = ontModel.getOntResource(typeURI);
                List<Object> literalObjects = new ArrayList<Object>();
                literalObjects.addAll(literals);
                OntResource listrange = getListContentType(range.asClass());
                addDirectRegularListProperty(r, range, listrange, literalObjects, 0);
            }
        }
    }

    // --------------------------------------
    // EVERYTHING TO DO WITH LISTS
    // --------------------------------------

    private void addSinglePropertyFromTypeRemembrance(Resource r, OntProperty p, String literalString, TypeVO typeremembrance) throws IOException {
        OntResource range = ontModel.getOntResource(ontNS + typeremembrance.getName());

        if (range.isClass()) {
            if (range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "ENUMERATION"))) {
                // Check for ENUM
                addEnumProperty(r, p, range, literalString);
            } else if (range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "SELECT"))) {
                // Check for SELECT
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 24*: found subClass of SELECT Class, now doing nothing with it: " + p + " - " + range.getLocalName() + " - " + literalString + "\r\n");
                createLiteralProperty(r, p, range, literalString);
            } else if (range.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                // Check for LIST
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*WARNING 24*: found LIST property (but doing nothing with it): " + p + " - " + range.getLocalName() + " - " + literalString + "\r\n");
            } else {
                createLiteralProperty(r, p, range, literalString);
            }
        } else {
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*WARNING 26*: found other kind of property: " + p + " - " + range.getLocalName() + "\r\n");
        }
    }

    private void addEnumProperty(Resource r, Property p, OntResource range, String literalString) throws IOException {
        for (ExtendedIterator<? extends OntResource> instances = range.asClass().listInstances(); instances.hasNext();) {
            OntResource rangeInstance = instances.next();
            if (rangeInstance.getProperty(RDFS.label).getString().equalsIgnoreCase(filterPoints(literalString))) {
                ttlWriter.triple(new Triple(r.asNode(), p.asNode(), rangeInstance.asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 2*: added ENUM statement " + r.getLocalName() + " - " + p.getLocalName() + " - " + rangeInstance.getLocalName() + "\r\n");
                return;
            }
        }
        if (myIfcReaderStream.logToFile)
            myIfcReaderStream.bw.write("*ERROR 9*: did not find ENUM individual for " + literalString + "\r\nQuitting the application without output!\r\n ");
        System.err.println("*ERROR 9*: did not find ENUM individual for " + literalString + "\r\nQuitting the application without output!");
    }

    private void addLiteralToResource(Resource r1, OntProperty valueProp, String xsdType, String literalString) throws IOException {
        if (xsdType.equalsIgnoreCase("integer"))
            addLiteral(r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDinteger));
        else if (xsdType.equalsIgnoreCase("double"))
            addLiteral(r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDdouble));
        else if (xsdType.equalsIgnoreCase("hexBinary"))
            addLiteral(r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDhexBinary));
        else if (xsdType.equalsIgnoreCase("boolean")) {
            if (literalString.equalsIgnoreCase(".F."))
                addLiteral(r1, valueProp, ResourceFactory.createTypedLiteral("false", XSDDatatype.XSDboolean));
            else if (literalString.equalsIgnoreCase(".T."))
                addLiteral(r1, valueProp, ResourceFactory.createTypedLiteral("true", XSDDatatype.XSDboolean));
            else if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*WARNING 10*: found odd boolean value: " + literalString + "\r\n");
        } else if (xsdType.equalsIgnoreCase("logical")) {
            if (literalString.equalsIgnoreCase(".F."))
                addProperty(r1, valueProp, expressModel.getResource(expressNS + "FALSE"));
            else if (literalString.equalsIgnoreCase(".T."))
                addProperty(r1, valueProp, expressModel.getResource(expressNS + "TRUE"));
            else if (literalString.equalsIgnoreCase(".U."))
                addProperty(r1, valueProp, expressModel.getResource(expressNS + "UNKNOWN"));
            else if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*WARNING 9*: found odd logical value: " + literalString + "\r\n");
        } else if (xsdType.equalsIgnoreCase("string"))
            addLiteral(r1, valueProp, ResourceFactory.createTypedLiteral(literalString, XSDDatatype.XSDstring));
        else
            addLiteral(r1, valueProp, ResourceFactory.createTypedLiteral(literalString));

        if (myIfcReaderStream.logToFile)
            myIfcReaderStream.bw.write("*OK 4*: added literal: " + r1.getLocalName() + " - " + valueProp + " - " + literalString + "\r\n");
    }

    // LIST HANDLING
    private void addDirectRegularListProperty(Resource r, OntResource range, OntResource listrange, List<Object> el, int mySwitch) throws IOException {
        // OntResource range = p.getRange();

        if (range.isClass()) {
            // OntResource listrange = getListContentType(range.asClass());

            if (listrange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*WARNING 27*: Found unhandled ListOfList" + "\r\n");
            } else {
                List<Resource> reslist = new ArrayList<Resource>();
                // createrequirednumberofresources
                for (int i = 0; i < el.size(); i++) {
                    if (i == 0)
                        reslist.add(r);
                    else {
                        Resource r1 = getResource(baseURI + range.getLocalName() + "_" + IDcounter, range);
                        reslist.add(r1);
                        IDcounter++;
                    }
                }

                if (mySwitch == 0) {
                    // bind the properties with literal values only if we are
                    // actually dealing with literals
                    List<String> literals = new ArrayList<String>();
                    for (int i = 0; i < el.size(); i++) {
                        literals.add((String) el.get(i));
                    }
                    addListInstanceProperties(reslist, literals, listrange);
                } else {
                    for (int i = 0; i < reslist.size(); i++) {
                        Resource r1 = reslist.get(i);
                        IFCVO vo = (IFCVO) el.get(i);
                        EntityVO evorange = ent.get(ExpressReader.formatClassName(((IFCVO) vo).getName()));
                        OntResource rclass = ontModel.getOntResource(ontNS + evorange.getName());
                        Resource r2 = getResource(baseURI + evorange.getName() + "_" + ((IFCVO) vo).getLineNum(), rclass);
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*OK 21*: created resource: " + r2.getLocalName() + "\r\n");
                        IDcounter++;
                        ttlWriter.triple(new Triple(r1.asNode(), listModel.getOntProperty(listNS + "hasContents").asNode(), r2.asNode()));
                        if (myIfcReaderStream.logToFile)
                            myIfcReaderStream.bw.write("*OK 22*: added property: " + r1.getLocalName() + " - " + "-hasContents-" + " - " + r2.getLocalName() + "\r\n");

                        if (i < el.size() - 1) {
                            ttlWriter.triple(new Triple(r1.asNode(), listModel.getOntProperty(listNS + "hasNext").asNode(), reslist.get(i + 1).asNode()));
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*OK 23*: added property: " + r1.getLocalName() + " - " + "-hasNext-" + " - " + reslist.get(i + 1).getLocalName() + "\r\n");
                        }
                    }
                }
            }
        }
    }

    private void addRegularListProperty(Resource r, OntProperty p, List<String> el, TypeVO typeRemembranceOverride) throws IOException {
        OntResource range = p.getRange();
        if (range.isClass()) {
            OntResource listrange = getListContentType(range.asClass());
            if (typeRemembranceOverride != null) {
                OntClass cla = ontModel.getOntClass(ontNS + typeRemembranceOverride.getName());
                listrange = cla;
            }

            if (listrange == null) {
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*ERROR 14*: We could not find what kind of content is expected in the LIST." + "\r\n");
            } else {
                if (listrange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*WARNING 28*: Found unhandled ListOfList" + "\r\n");
                } else {
                    List<Resource> reslist = new ArrayList<Resource>();
                    // createrequirednumberofresources
                    for (int ii = 0; ii < el.size(); ii++) {
                        Resource r1 = getResource(baseURI + range.getLocalName() + "_" + IDcounter, range);
                        reslist.add(r1);
                        IDcounter++;
                        if (ii == 0) {
                            ttlWriter.triple(new Triple(r.asNode(), p.asNode(), r1.asNode()));
                            if (myIfcReaderStream.logToFile)
                                myIfcReaderStream.bw.write("*OK 7*: added property: " + r.getLocalName() + " - " + p.getLocalName() + " - " + r1.getLocalName() + "\r\n");
                        }
                    }
                    // bindtheproperties
                    addListInstanceProperties(reslist, el, listrange);
                }
            }
        }
    }

    private void createLiteralProperty(Resource r, OntResource p, OntResource range, String literalString) throws IOException {
        String xsdType = getXSDTypeFromRange(range);
        if (xsdType == null) {
            xsdType = getXSDTypeFromRangeExpensiveMethod(range);
        }
        if (xsdType != null) {
            String xsdTypeCAP = Character.toUpperCase(xsdType.charAt(0)) + xsdType.substring(1);
            OntProperty valueProp = expressModel.getOntProperty(expressNS + "has" + xsdTypeCAP);
            String key = valueProp.toString() + ":" + xsdType + ":" + literalString;

            Resource r1 = propertyResourceMap.get(key);
            if (r1 == null) {
                r1 = ResourceFactory.createResource(baseURI + range.getLocalName() + "_" + IDcounter);
                ttlWriter.triple(new Triple(r1.asNode(), RDF.type.asNode(), range.asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 17*: created resource: " + r1.getLocalName() + "\r\n");
                IDcounter++;
                propertyResourceMap.put(key, r1);
                addLiteralToResource(r1, valueProp, xsdType, literalString);
            }
            ttlWriter.triple(new Triple(r.asNode(), p.asNode(), r1.asNode()));
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*OK 3*: added property: " + r.getLocalName() + " - " + p.getLocalName() + " - " + r1.getLocalName() + "\r\n");
        } else {
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*ERROR 1*: XSD type not found for: " + p + " - " + range.getURI() + " - " + literalString + "\r\n");
        }
    }

    private void addListPropertyToGivenEntities(Resource r, OntProperty p, List<Resource> el) throws IOException {
        OntResource range = p.getRange();
        if (range.isClass()) {
            OntResource listrange = getListContentType(range.asClass());

            if (listrange.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 20*: Handling list of list" + "\r\n");
                listrange = range;
            }
            for (int i = 0; i < el.size(); i++) {
                Resource r1 = el.get(i);
                Resource r2 = ResourceFactory.createResource(baseURI + range.getLocalName() + "_" + IDcounter); // was
                                                                                                                // listrange
                ttlWriter.triple(new Triple(r2.asNode(), RDF.type.asNode(), range.asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 14*: added property: " + r2.getLocalName() + " - rdf:type - " + range.getLocalName() + "\r\n");
                IDcounter++;
                Resource r3 = ResourceFactory.createResource(baseURI + range.getLocalName() + "_" + IDcounter);

                if (i == 0) {
                    ttlWriter.triple(new Triple(r.asNode(), p.asNode(), r2.asNode()));
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*OK 15*: added property: " + r.getLocalName() + " - " + p.getLocalName() + " - " + r2.getLocalName() + "\r\n");
                }
                ttlWriter.triple(new Triple(r2.asNode(), listModel.getOntProperty(listNS + "hasContents").asNode(), r1.asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 16*: added property: " + r2.getLocalName() + " - " + "-hasContents-" + " - " + r1.getLocalName() + "\r\n");

                if (i < el.size() - 1) {
                    ttlWriter.triple(new Triple(r2.asNode(), listModel.getOntProperty(listNS + "hasNext").asNode(), r3.asNode()));
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*OK 17*: added property: " + r2.getLocalName() + " - " + "-hasNext-" + " - " + r3.getLocalName() + "\r\n");
                }
            }
        }
    }

    private void fillClassInstanceList(LinkedList<Object> tmpList, OntResource typerange, OntProperty p, Resource r) throws IOException {
        List<Resource> reslist = new ArrayList<Resource>();
        List<IFCVO> entlist = new ArrayList<IFCVO>();

        // createrequirednumberofresources
        for (int i = 0; i < tmpList.size(); i++) {
            if (IFCVO.class.isInstance(tmpList.get(i))) {
                Resource r1 = getResource(baseURI + typerange.getLocalName() + "_" + IDcounter, typerange);
                reslist.add(r1);
                IDcounter++;
                entlist.add((IFCVO) tmpList.get(i));
                if (i == 0) {
                    ttlWriter.triple(new Triple(r.asNode(), p.asNode(), r1.asNode()));
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*OK 13*: added property: " + r.getLocalName() + " - " + p.getLocalName() + " - " + r1.getLocalName() + "\r\n");
                }
            }
        }

        // bindtheproperties
        String listvaluepropURI = ontNS + typerange.getLocalName().substring(0, typerange.getLocalName().length() - 5);
        OntResource listrange = ontModel.getOntResource(listvaluepropURI);

        addClassInstanceListProperties(reslist, entlist, listrange);
    }

    private void addClassInstanceListProperties(List<Resource> reslist, List<IFCVO> entlist, OntResource listrange) throws IOException {
        OntProperty listp = listModel.getOntProperty(listNS + "hasContents");
        OntProperty isfollowed = listModel.getOntProperty(listNS + "hasNext");

        for (int i = 0; i < reslist.size(); i++) {
            Resource r = reslist.get(i);

            OntResource rclass = null;
            EntityVO evorange = ent.get(ExpressReader.formatClassName(entlist.get(i).getName()));
            if (evorange == null) {
                TypeVO typerange = typ.get(ExpressReader.formatClassName(entlist.get(i).getName()));
                rclass = ontModel.getOntResource(ontNS + typerange.getName());
                Resource r1 = getResource(baseURI + typerange.getName() + "_" + entlist.get(i).getLineNum(), rclass);
                ttlWriter.triple(new Triple(r.asNode(), listp.asNode(), r1.asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 8*: created property: " + r.getLocalName() + " - " + listp.getLocalName() + " - " + r1.getLocalName() + "\r\n");
            } else {
                rclass = ontModel.getOntResource(ontNS + evorange.getName());
                Resource r1 = getResource(baseURI + evorange.getName() + "_" + entlist.get(i).getLineNum(), rclass);
                ttlWriter.triple(new Triple(r.asNode(), listp.asNode(), r1.asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 9*: created property: " + r.getLocalName() + " - " + listp.getLocalName() + " - " + r1.getLocalName() + "\r\n");
            }

            if (i < reslist.size() - 1) {
                ttlWriter.triple(new Triple(r.asNode(), isfollowed.asNode(), reslist.get(i + 1).asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 10*: created property: " + r.getLocalName() + " - " + isfollowed.getLocalName() + " - " + reslist.get(i + 1).getLocalName() + "\r\n");
            }
        }
    }

    private void addListInstanceProperties(List<Resource> reslist, List<String> listelements, OntResource listrange) throws IOException {
        // GetListType
        String xsdType = getXSDTypeFromRange(listrange);
        if (xsdType == null)
            xsdType = getXSDTypeFromRangeExpensiveMethod(listrange);
        if (xsdType != null) {
            String xsdTypeCAP = Character.toUpperCase(xsdType.charAt(0)) + xsdType.substring(1);
            OntProperty valueProp = expressModel.getOntProperty(expressNS + "has" + xsdTypeCAP);

            // Adding Content only if found
            for (int i = 0; i < reslist.size(); i++) {
                Resource r = reslist.get(i);
                String literalString = listelements.get(i);
                String key = valueProp.toString() + ":" + xsdType + ":" + literalString;
                Resource r2 = propertyResourceMap.get(key);
                if (r2 == null) {
                    r2 = ResourceFactory.createResource(baseURI + listrange.getLocalName() + "_" + IDcounter);
                    ttlWriter.triple(new Triple(r2.asNode(), RDF.type.asNode(), listrange.asNode()));
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*OK 19*: created resource: " + r2.getLocalName() + "\r\n");
                    IDcounter++;
                    propertyResourceMap.put(key, r2);
                    addLiteralToResource(r2, valueProp, xsdType, literalString);
                }
                ttlWriter.triple(new Triple(r.asNode(), listModel.getOntProperty(listNS + "hasContents").asNode(), r2.asNode()));
                if (myIfcReaderStream.logToFile)
                    myIfcReaderStream.bw.write("*OK 11*: added property: " + r.getLocalName() + " - " + "-hasContents-" + " - " + r2.getLocalName() + "\r\n");

                if (i < listelements.size() - 1) {
                    ttlWriter.triple(new Triple(r.asNode(), listModel.getOntProperty(listNS + "hasNext").asNode(), reslist.get(i + 1).asNode()));
                    if (myIfcReaderStream.logToFile)
                        myIfcReaderStream.bw.write("*OK 12*: added property: " + r.getLocalName() + " - " + "-hasNext-" + " - " + reslist.get(i + 1).getLocalName() + "\r\n");
                }
            }
        } else {
            if (myIfcReaderStream.logToFile)
                myIfcReaderStream.bw.write("*ERROR 5*: XSD type not found for: " + listrange.getLocalName() + "\r\n");
        }
    }

    // HELPER METHODS
    private String filterExtras(String txt) {
        StringBuffer sb = new StringBuffer();
        for (int n = 0; n < txt.length(); n++) {
            char ch = txt.charAt(n);
            switch (ch) {
                case '\'':
                    break;
                case '=':
                    break;
                default:
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String filterPoints(String txt) {
        StringBuffer sb = new StringBuffer();
        for (int n = 0; n < txt.length(); n++) {
            char ch = txt.charAt(n);
            switch (ch) {
                case '.':
                    break;
                default:
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private Long toLong(String txt) {
        try {
            return Long.valueOf(txt);
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    private void addLiteral(Resource r, OntProperty valueProp, Literal l) {
        ttlWriter.triple(new Triple(r.asNode(), valueProp.asNode(), l.asNode()));
    }

    private void addProperty(Resource r, OntProperty valueProp, Resource r1) {
        ttlWriter.triple(new Triple(r.asNode(), valueProp.asNode(), r1.asNode()));
    }

    private OntResource getListContentType(OntClass range) throws IOException {
        if (range.asClass().getURI().equalsIgnoreCase(expressNS + "STRING_List") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "STRING_List")))
            return expressModel.getOntResource(expressNS + "STRING");
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "REAL_List") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "REAL_List")))
            return expressModel.getOntResource(expressNS + "REAL");
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "INTEGER_List") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "INTEGER_List")))
            return expressModel.getOntResource(expressNS + "INTEGER");
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "BINARY_List") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "BINARY_List")))
            return expressModel.getOntResource(expressNS + "BINARY");
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "BOOLEAN_List") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "BOOLEAN_List")))
            return expressModel.getOntResource(expressNS + "BOOLEAN");
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "LOGICAL_List") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "LOGICAL_List")))
            return expressModel.getOntResource(expressNS + "LOGICAL");
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "NUMBER_List") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "NUMBER_List")))
            return expressModel.getOntResource(expressNS + "NUMBER");
        else if (range.asClass().hasSuperClass(listModel.getOntClass(listNS + "OWLList"))) {
            String listvaluepropURI = ontNS + range.getLocalName().substring(0, range.getLocalName().length() - 5);
            return ontModel.getOntResource(listvaluepropURI);
        } else {
            if (myIfcReaderStream.logToFile) {
                myIfcReaderStream.bw.write("*WARNING 29*: did not find listcontenttype for : " + range.getLocalName() + "\r\n");
            }
            return null;
        }
    }

    private String getXSDTypeFromRange(OntResource range) {
        if (range.asClass().getURI().equalsIgnoreCase(expressNS + "STRING") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "STRING")))
            return "string";
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "REAL") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "REAL")))
            return "double";
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "INTEGER") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "INTEGER")))
            return "integer";
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "BINARY") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "BINARY")))
            return "hexBinary";
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "BOOLEAN") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "BOOLEAN")))
            return "boolean";
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "LOGICAL") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "LOGICAL")))
            return "logical";
        else if (range.asClass().getURI().equalsIgnoreCase(expressNS + "NUMBER") || range.asClass().hasSuperClass(expressModel.getOntClass(expressNS + "NUMBER")))
            return "double";
        else
            return null;
    }

    private String getXSDTypeFromRangeExpensiveMethod(OntResource range) {
        ExtendedIterator<OntClass> iter = range.asClass().listSuperClasses();
        while (iter.hasNext()) {
            OntClass superc = iter.next();
            if (!superc.isAnon()) {
                String type = getXSDTypeFromRange(superc);
                if (type != null)
                    return type;
            }
        }
        return null;
    }

    private Resource getResource(String uri, OntResource rclass) {
        Resource r = resourceMap.get(uri);
        if (r == null) {
            r = ResourceFactory.createResource(uri);
            resourceMap.put(uri, r);
            try {
                ttlWriter.triple(new Triple(r.asNode(), RDF.type.asNode(), rclass.asNode()));
            } catch (Exception e) {
            	e.printStackTrace();
                if (myIfcReaderStream.logToFile)
                    try {
                        myIfcReaderStream.bw.write("*ERROR 2*: getResource failed for " + uri + "\r\n");
                        System.err.println("*ERROR 2*: getResource failed for " + uri);
                    } catch (IOException e1) {
                        // near to impossible to happen. This point would not
                        // have been reached if it were possible.
                        e1.printStackTrace();
                    }
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

}
