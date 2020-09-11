package nl.tue.isbe.ifc2lbd;

/*
 *
 * Copyright 2019 Pieter Pauwels, Eindhoven University of Technology
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

import be.ugent.IfcSpfReader;
import com.buildingsmart.tech.ifcowl.vo.EntityVO;
import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import com.buildingsmart.tech.ifcowl.vo.TypeVO;
import nl.tue.isbe.Namespace;
import nl.tue.isbe.BOT.*;
import nl.tue.isbe.IFC.*;
import nl.tue.isbe.ifcspftools.GuidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class RDFWriter {

    // input variables
    private final String baseURI;

    // EXPRESS basis
    private final Map<String, EntityVO> ent;
    private final Map<String, TypeVO> typ;

    //Writing output
    private final boolean hasBuildingElements;
    private final boolean hasBuildingProperties;

    private Map<Long, IFCVO> linemap;

    private InputStream inputStream;

    private IfcSpfReader myIfcReaderStream;

    private static final Logger LOG = LoggerFactory.getLogger(be.ugent.RDFWriter.class);

    public RDFWriter(InputStream inputStream, String baseURI, Map<String, EntityVO> ent, Map<String, TypeVO> typ,
                     Map<Long, IFCVO> linemap, boolean hasBuildingElements, boolean hasBuildingProperties) {
        this.inputStream = inputStream;
        this.baseURI = baseURI;
        this.ent = ent;
        this.typ = typ;
        this.linemap = linemap;
        this.hasBuildingElements = hasBuildingElements;
        this.hasBuildingProperties = hasBuildingProperties;
    }

    public void setIfcReader(IfcSpfReader r) {
        this.myIfcReaderStream = r;
    }

    public void writeModelToStream(OutputStream out){
        try {
            String s = "# baseURI: " + baseURI + "\r\n\r\n";
            s+= "@prefix inst: <" + baseURI + "> .\r\n";
            s+= "@prefix rdf:  <" + Namespace.RDF + "> .\r\n";
            s+= "@prefix rdfs:  <" + Namespace.RDFS + "> .\r\n";
            s+= "@prefix xsd:  <" + Namespace.XSD + "> .\r\n";
            s+= "@prefix bot:  <" + Namespace.BOT + "> .\r\n";
            s+= "@prefix beo:  <" + Namespace.BEO + "> .\r\n";
            s+= "@prefix mep:  <" + Namespace.MEP + "> .\r\n";
            s+= "@prefix props:  <" + Namespace.PROPS + "> .\r\n\r\n";

            s+= "inst: rdf:type <http://www.w3.org/2002/07/owl#Ontology> .\r\n\r\n";

            out.write(s.getBytes());

            parseToBOTClasses();
            writeBOTdata(out, hasBuildingElements, hasBuildingProperties);

            // Save memory
            linemap.clear();
            linemap = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseToBOTClasses(){
        //First run to collect everything in memory
        for (Map.Entry<Long, IFCVO> entry : linemap.entrySet()) {
            IFCVO ifcLineEntry = entry.getValue();
            String n = ifcLineEntry.getName();
            //System.out.println("line number : " + ifcLineEntry.getLineNum());
            if(Element.containedInBEO(ifcLineEntry.getName().substring(3)) || Element.containedInMEP(ifcLineEntry.getName().substring(3)) ||
            Element.containedInIFC4_ADD2_TC1(ifcLineEntry.getName()) || Element.containedInIFC2x3_TC1(ifcLineEntry.getName())){
                Element el = new Element(ifcLineEntry);
                //LOG.info("Element: "+ el.toString());
                continue;
            }
            if(n.endsWith("TYPE")) {
                String sub1 = n.substring(3, n.length() - 4);
                String sub2 = n.substring(0, n.length() - 4);
                if (Element.containedInBEO(sub1) || Element.containedInMEP(sub1) ||
                        Element.containedInIFC4_ADD2_TC1(sub2) || Element.containedInIFC2x3_TC1(sub2)) {
                    IfcElementType elt = new IfcElementType(ifcLineEntry);
                    //LOG.info("Element: "+ el.toString());
                    continue;
                }
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELVOIDSELEMENT")){
                IfcRelVoidsElement irve = new IfcRelVoidsElement(ifcLineEntry);
                //LOG.info("RelVoids: "+ irve.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELFILLSELEMENT")){
                IfcRelFillsElement irfe = new IfcRelFillsElement(ifcLineEntry);
                //LOG.info("RelFills: "+ irfe.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCSITE")){
                Site s = new Site(ifcLineEntry);
                //LOG.info("Site: "+ s.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCBUILDING")){
                Building b = new Building(ifcLineEntry);
                //LOG.info("Building: "+ b.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCBUILDINGSTOREY")){
                Storey s = new Storey(ifcLineEntry);
                //LOG.info("Storey: "+ s.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCSPACE")){
                Space s = new Space(ifcLineEntry);
                //LOG.info("Space: "+ s.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELAGGREGATES")){
                IfcRelAggregates ira = new IfcRelAggregates(ifcLineEntry);
                //LOG.info("IfcRelAggregates: "+ ira.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELDECOMPOSES")){
                IfcRelDecomposes ird = new IfcRelDecomposes(ifcLineEntry);
                //LOG.info("IfcRelDecomposes: "+ ird.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELCONTAINEDINSPATIALSTRUCTURE")){
                IfcRelContainedInSpatialStructure irciss = new IfcRelContainedInSpatialStructure(ifcLineEntry);
                //LOG.info("IfcRelContainedInSpatialStructure: "+ irciss.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELSPACEBOUNDARY")){
                IfcRelSpaceBoundary irsb = new IfcRelSpaceBoundary(ifcLineEntry);
                //LOG.info("IfcRelSpaceBoundary: "+ irsb.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELDEFINESBYPROPERTIES")){
                IfcRelDefinesByProperties irdbp = new IfcRelDefinesByProperties(ifcLineEntry);
                //LOG.info("IfcRelDefinesByProperties: "+ irdbp.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCRELDEFINESBYTYPE")){
                IfcRelDefinesByType irdbt = new IfcRelDefinesByType(ifcLineEntry);
                //LOG.info("IfcRelDefinesByProperties: "+ irdbp.toString());
                continue;
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCQUANTITYAREA") ||
                    ifcLineEntry.getName().equalsIgnoreCase("IFCQUANTITYLENGTH") ||
                    ifcLineEntry.getName().equalsIgnoreCase("IFCQUANTITYVOLUME") ||
                    ifcLineEntry.getName().equalsIgnoreCase("IFCQUANTITYCOUNT") ||
                    ifcLineEntry.getName().equalsIgnoreCase("IFCQUANTITYTIME") ||
                    ifcLineEntry.getName().equalsIgnoreCase("IFCQUANTITYWEIGHT")){
                //all simple quantities covered
                //List<Object> proplist = (List<Object>)ifcLineEntry.getObjectList().get(5);
                Quantity quan = new Quantity(ifcLineEntry);
            }
            if(ifcLineEntry.getName().equalsIgnoreCase("IFCPROPERTYSINGLEVALUE")){
                if(ifcLineEntry.getObjectList().get(5) instanceof Character){
                    LOG.warn("Found a character where we expected a linked list. This is likely an empty property, which we then skip of course.");
                    continue;
                }
                else if(ifcLineEntry.getObjectList().get(5) instanceof List){
                    List<Object> proplist = (List<Object>)ifcLineEntry.getObjectList().get(5);
                    String value = proplist.get(0).toString();
                    if(value.startsWith("\'") && value.length()==1)
                        continue;
                    else if(!value.startsWith("\'") && value.length()==0)
                        continue;
                    else {
                        Property prop = new Property(ifcLineEntry);
                    }
                }

                //LOG.info("Property: "+ prop.toString());
                continue;
            }
        }

        //Second run to re-order and link everything(
        LOG.info("linking everything");
        linkSitesToBuildings();
        linkBuildingsToBuildingStoreys();
        linkBuildingStoreysToSpaces();
        linkStoreysToContainedElements();
        linkSpacesToContainedElements();
        linkElementsWithSubElements();
        linkElementsWithTypes();
        linkHostedElements();
        linkSpacesWithBoundingObjects();
        linkPropertiesToSpacesAndElements();
        linkPropertiesToTypesAndElements();
        LOG.info("done linking everything");
    }

    private void linkHostedElements(){
        /*#2791= IFCRELVOIDSELEMENT('1Y9nGTxX90dwWkO8DbXfXj',#41,$,$,#696,#2786);
        #2794= IFCRELFILLSELEMENT('0iGRDvAcfDu974L$6ZRZYu',#41,$,$,#2786,#1842);*/
        for(IfcRelVoidsElement irve : IfcRelVoidsElement.relVoidsElementList){
            IFCVO relatingOpeningElement = irve.getRelatingOpeningElement();
            for(IfcRelFillsElement irfe : IfcRelFillsElement.relFillsElementList){
                IFCVO relatingOpeningElement1 = irfe.getRelatingOpeningElement();
                if(relatingOpeningElement.getLineNum() == relatingOpeningElement1.getLineNum()){
                    //found matching opening
                    //then linking elements with a hosting relationship
                    for(Element el : Element.elementList){
                        if(el.getLineNum() == irve.getRelatingBuildingElement().getLineNum()){
                            for(Element el1 : Element.elementList){
                                if(el1.getLineNum() == irfe.getRelatingBuildingElement().getLineNum()){
                                    el.addHostedElement(el1);
                                    el1.setHostingElement(el);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void linkElementsWithSubElements(){
        //e.g. #1379= IFCRELAGGREGATES('30CdxEdnnCieKBcqu32dMo',#41,$,$,#1234,(#1274,#1308,#1342,#1376));
        for(Element el : Element.elementList){
            List<IfcObjectDefinition> iods = IfcRelAggregates.getRelatedObjectsForRelatingObject(el.getLineNum());
            if(iods==null)
                continue;
            for(Element el1 : Element.elementList){
                for(IfcObjectDefinition iod : iods) {
                    if (el1.getLineNum() == iod.getLineNum()) {
                        el.addSubElement(el1);
                        el1.setPartOfElement(el);
                        //break;
                    }
                }
            }
        }
    }

    private void linkElementsWithTypes(){
        for(IfcRelDefinesByType irdbt : IfcRelDefinesByType.relDefinesByTypeList){
            IFCVO typevo = irdbt.getRelatingType();
            List<IFCVO> objectsvo = irdbt.getRelatedObjects();
            IfcElementType iet = IfcElementType.getIfcElementType(typevo.getLineNum());
            if(iet==null)
                continue;
            for(Element el1 : Element.elementList){
                for(IFCVO obj : objectsvo) {
                    if (el1.getLineNum() == obj.getLineNum()) {
                        el1.setIfcElementType(iet);
                        el1.correctTypeBasedOnIfcType();
                        break;
                    }
                }
            }
        }
    }

    private void linkSpacesWithBoundingObjects(){
        for(IfcRelSpaceBoundary rsb : IfcRelSpaceBoundary.relSpaceBoundaryList){
            IfcObjectDefinition relatingSpace = rsb.getRelatingSpace();
            IfcObjectDefinition relatedBuildingElement = rsb.getRelatedBuildingElement();
            if(relatingSpace == null || relatedBuildingElement==null)
                continue;
            for(Space s : Space.spaceList){
                if(s.getLineNum() == relatingSpace.getLineNum()){
                    for(Element el : Element.elementList){
                        if(el.getLineNum() == relatedBuildingElement.getLineNum()){
                            s.getAdjacentElements().add(el);
                            el.setBoundingSpace(s);
                            rsb.getInterface().setRelatedElement(el);
                            rsb.getInterface().setRelatingSpace(s);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void linkSitesToBuildings(){
        for(Site s : Site.siteList){
            List<IfcObjectDefinition> iods = IfcRelAggregates.getRelatedObjectsForRelatingObject(s.getLineNum());
            if(iods==null)
                continue;
            for(Building b : Building.buildingList){
                for(IfcObjectDefinition iod : iods) {
                    if (b.getLineNum() == iod.getLineNum()) {
                        s.addBuilding(b);
                        b.setSite(s);
                        break;
                    }
                }
            }
        }
    }

    private void linkBuildingsToBuildingStoreys(){
        for(Building b : Building.buildingList){
            List<IfcObjectDefinition> iods = IfcRelAggregates.getRelatedObjectsForRelatingObject(b.getLineNum());
            if(iods==null)
                continue;
            for(Storey bs : Storey.storeyList){
                for(IfcObjectDefinition iod : iods) {
                    if (bs.getLineNum() == iod.getLineNum()) {
                        b.addStorey(bs);
                        bs.setBuilding(b);
                        break;
                    }
                }
            }
        }
    }

    private void linkBuildingStoreysToSpaces(){
        for(Storey bs : Storey.storeyList){
            List<IfcObjectDefinition> iods = IfcRelAggregates.getRelatedObjectsForRelatingObject(bs.getLineNum());
            if(iods==null)
                continue;
            for(Space sp : Space.spaceList){
                for(IfcObjectDefinition iod : iods) {
                    if (sp.getLineNum() == iod.getLineNum()) {
                        bs.addSpace(sp);
                        sp.setStorey(bs);
                        break;
                    }
                }
            }
        }
    }

    private void linkStoreysToContainedElements(){
        for(Storey bs : Storey.storeyList){
            List<IfcObjectDefinition> iods = IfcRelContainedInSpatialStructure.getRelatedElementsForRelatingStructure(bs.getLineNum());
            if(iods==null)
                continue;
            for(Element el : Element.elementList){
                for(IfcObjectDefinition iod : iods) {
                    if (el.getLineNum() == iod.getLineNum()) {
                        bs.addContainedElement(el);
                        el.setContainingStorey(bs);
                        break;
                    }
                }
            }
        }
    }

    private void linkSpacesToContainedElements(){
        for(Space sp : Space.spaceList){
            List<IfcObjectDefinition> iods = IfcRelContainedInSpatialStructure.getRelatedElementsForRelatingStructure(sp.getLineNum());
            if(iods==null)
                continue;
            for(Element el : Element.elementList){
                for(IfcObjectDefinition iod : iods) {
                    if (el.getLineNum() == iod.getLineNum()) {
                        sp.addContainedElement(el);
                        el.setContainingSpace(sp);
                        break;
                    }
                }
            }
        }
    }

    private void linkPropertiesToSpacesAndElements(){
        for(IfcRelDefinesByProperties irdbp : IfcRelDefinesByProperties.relDefinesByPropertiesList){
            IFCVO propertySet = irdbp.getRelatingObject();
            if(propertySet.getName().equalsIgnoreCase("IFCELEMENTQUANTITY")){
                //LOG.info("found quantityset:");
                for(IFCVO obj : irdbp.getRelatedObjects()) {
                    if(obj.getName().equalsIgnoreCase("IFCSPACE")){
                        for (Space sp : Space.spaceList) {
                            if (sp.getLineNum() == obj.getLineNum()) {
                                List<IFCVO> props = irdbp.getRelatedQuantities();
                                for (IFCVO prop : props) {
                                    for (Quantity q : Quantity.quantityList) {
                                        if (prop.getLineNum() == q.getLineNum()) {
                                            sp.addQuantity(q);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                for(IFCVO obj : irdbp.getRelatedObjects()) {
                    if(obj.getName().equalsIgnoreCase("IFCBUILDINGSTOREY")){
                        for (Storey st : Storey.storeyList) {
                            if (st.getLineNum() == obj.getLineNum()) {
                                List<IFCVO> props = irdbp.getRelatedQuantities();
                                for (IFCVO prop : props) {
                                    for (Quantity q : Quantity.quantityList) {
                                        if (prop.getLineNum() == q.getLineNum()) {
                                            st.addQuantity(q);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else{
                //LOG.info("found propertyset:");
                for(IFCVO obj : irdbp.getRelatedObjects()) {
                    if(obj.getName().equalsIgnoreCase("IFCSPACE")){
                        for (Space sp : Space.spaceList) {
                            if (sp.getLineNum() == obj.getLineNum()) {
                                List<IFCVO> props = irdbp.getRelatedProperties();
                                for (IFCVO prop : props) {
                                    for (Property p : Property.propertyList) {
                                        if (prop.getLineNum() == p.getLineNum()) {
                                            if(!p.isEmpty())
                                                sp.addProperty(p);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if(obj.getName().equalsIgnoreCase("IFCBUILDINGSTOREY")){
                        for (Storey st : Storey.storeyList) {
                            if (st.getLineNum() == obj.getLineNum()) {
                                List<IFCVO> props = irdbp.getRelatedProperties();
                                for (IFCVO prop : props) {
                                    for (Property p : Property.propertyList) {
                                        if (prop.getLineNum() == p.getLineNum()) {
                                            if(!p.isEmpty())
                                                st.addProperty(p);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if(obj.getName().equalsIgnoreCase("IFCBUILDING")){
                        for (Building b : Building.buildingList) {
                            if (b.getLineNum() == obj.getLineNum()) {
                                List<IFCVO> props = irdbp.getRelatedProperties();
                                for (IFCVO prop : props) {
                                    for (Property p : Property.propertyList) {
                                        if (prop.getLineNum() == p.getLineNum()) {
                                            if(!p.isEmpty())
                                                b.addProperty(p);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if(obj.getName().equalsIgnoreCase("IFCSITE")){
                        for (Site s : Site.siteList) {
                            if (s.getLineNum() == obj.getLineNum()) {
                                List<IFCVO> props = irdbp.getRelatedProperties();
                                for (IFCVO prop : props) {
                                    for (Property p : Property.propertyList) {
                                        if (prop.getLineNum() == p.getLineNum()) {
                                            if(!p.isEmpty())
                                                s.addProperty(p);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else {
                        for (Element el : Element.elementList) {
                            if (el.getLineNum() == obj.getLineNum()) {
                                List<IFCVO> props = irdbp.getRelatedProperties();
                                for (IFCVO prop : props) {
                                    for (Property p : Property.propertyList) {
                                        if (prop.getLineNum() == p.getLineNum()) {
                                            if(!p.isEmpty())
                                                el.addProperty(p);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void linkPropertiesToTypesAndElements(){
        for(IfcElementType type : IfcElementType.elementTypeList){
            List<IFCVO> props = type.getOriginalProperties();
            for (IFCVO prop : props) {
                for (Property p : Property.propertyList) {
                    if (prop.getLineNum() == p.getLineNum()) {
                        type.addProperty(p);
                        //also assign the properties directly to the element!!
                        for (Element el : Element.elementList) {
                            if (el.getIfcElementType() == type) {
                                //TODO: check whether property does not exist yet, and whether the property is not empty
                                el.addProperty(p);
                                //no break; because this need to be done for all relevant elements - more than 1
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    private void writeBOTdata(OutputStream out, boolean prod, boolean props){
        try {
            int counter = 0;

            String output = "";
            for(Site s : Site.siteList){
                output += "inst:site_"+s.getLineNum() + "\r\n";
                output += "\ta bot:Site ;" + "\r\n";
                output += "\trdfs:label \""+s.getLabel()+"\"^^xsd:string ;" + "\r\n";
                output += "\trdfs:comment \""+s.getDescription()+"\"^^xsd:string ;" + "\r\n";
                output += "\tbot:hasGuid \""+ GuidHandler.getUncompressedStringFromGuid(s.getGuid()) +"\"^^xsd:string ";
                for(Building b : s.getBuildings()){
                    output += ";\r\n";
                    output += "\tbot:hasBuilding inst:"+ b.getName() + " ";
                }
                if(props){
                    for(Property p : s.getProperties()){
                        output += ";\r\n";
                        if(p.getValue().equalsIgnoreCase(".T."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" true ";
                        else if(p.getValue().equalsIgnoreCase(".F."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" false ";
                        else if(p.getValue().startsWith("\'"))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue().substring(1) +"\"^^xsd:string ";
                        else
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue() +"\"^^xsd:double ";
                    }
                }
                output += ". \r\n\r\n";
            }

            LOG.debug("written sites");
            out.write(output.getBytes());
            out.flush();
            output = "";

            for(Building b : Building.buildingList){
                output += "inst:building_"+b.getLineNum() + "\r\n";
                output += "\ta bot:Building ;" + "\r\n";
                output += "\trdfs:label \""+b.getLabel()+"\"^^xsd:string ;" + "\r\n";
                output += "\trdfs:comment \""+b.getDescription()+"\"^^xsd:string ;" + "\r\n";
                output += "\tbot:hasGuid \""+ GuidHandler.getUncompressedStringFromGuid(b.getGuid()) +"\"^^xsd:string ";
                for(Storey bs : b.getStoreys()){
                    output += ";\r\n";
                    output += "\tbot:hasStorey inst:"+ bs.getName() + " ";
                }
                if(props){
                    for(Property p : b.getProperties()){
                        output += ";\r\n";
                        if(p.getValue().equalsIgnoreCase(".T."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" true ";
                        else if(p.getValue().equalsIgnoreCase(".F."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" false ";
                        else if(p.getValue().startsWith("\'"))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue().substring(1) +"\"^^xsd:string ";
                        else
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue() +"\"^^xsd:double ";
                    }
                }
                output += ". \r\n\r\n";
            }

            LOG.debug("written buildings");
            out.write(output.getBytes());
            out.flush();
            output = "";

            for(Storey bs : Storey.storeyList){
                output += "inst:storey_"+bs.getLineNum() + "\r\n";
                output += "\ta bot:Storey ;" + "\r\n";
                output += "\tbot:hasGuid \""+ GuidHandler.getUncompressedStringFromGuid(bs.getGuid()) +"\"^^xsd:string ;" + "\r\n";
                output += "\trdfs:label \""+bs.getLabel()+"\"^^xsd:string ;" + "\r\n";
                output += "\trdfs:comment \""+bs.getDescription()+"\"^^xsd:string ";
                counter=0;
                for(Space sp : bs.getSpaces()){
                    if(counter==0) {
                        output += ";\r\n";
                        output += "\tbot:hasSpace inst:" + sp.getName() + " ";
                        counter++;
                    }
                    else{
                        output += ", inst:"+ sp.getName() + " ";
                    }
                }
                counter=0;
                for(Element el : bs.getContainedElements()){
                    if(counter==0) {
                        output += ";\r\n";
                        output += "\tbot:containsElement inst:"+ el.getName() + " ";
                        counter++;
                    }
                    else{
                        output += ", inst:"+ el.getName() + " ";
                    }
                }
                if(props){
                    for(Property p : bs.getProperties()){
                        output += ";\r\n";
                        if(p.getValue().equalsIgnoreCase(".T."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" true ";
                        else if(p.getValue().equalsIgnoreCase(".F."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" false ";
                        else if(p.getValue().startsWith("\'"))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue().substring(1) +"\"^^xsd:string ";
                        else
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue() +"\"^^xsd:double ";
                    }
                    for(Quantity q : bs.getQuantities()){
                        output += ";\r\n";
                        output += "\tprops:"+q.getQuantityNameNoSpace() +" \""+ q.getValue() +"\"^^xsd:double ";
                    }
                }
                output += ". \r\n\r\n";
            }

            LOG.debug("written storeys");
            out.write(output.getBytes());
            out.flush();
            output = "";

            for(Space sp : Space.spaceList){
                output += "inst:space_"+sp.getLineNum() + "\r\n";
                output += "\ta bot:Space ;" + "\r\n";
                output += "\tbot:hasGuid \""+ GuidHandler.getUncompressedStringFromGuid(sp.getGuid()) +"\"^^xsd:string ";
                for(Element el : sp.getAdjacentElements()){
                    output += ";\r\n";
                    output += "\tbot:adjacentElement inst:"+ el.getName() + " ";
                }
                counter=0;
                for(Element el : sp.getContainedElements()){
                    if(counter==0) {
                        output += ";\r\n";
                        output += "\tbot:containsElement inst:"+ el.getName() + " ";
                        counter++;
                    }
                    else{
                        output += ", inst:"+ el.getName() + " ";
                    }
                }
                if(props){
                    for(Property p : sp.getProperties()){
                        output += ";\r\n";
                        if(p.getValue().equalsIgnoreCase(".T."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" true ";
                        else if(p.getValue().equalsIgnoreCase(".F."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" false ";
                        else if(p.getValue().startsWith("\'"))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue().substring(1) +"\"^^xsd:string ";
                        else
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue() +"\"^^xsd:double ";
                    }
                    for(Quantity q : sp.getQuantities()){
                        output += ";\r\n";
                        output += "\tprops:"+q.getQuantityNameNoSpace() +" \""+ q.getValue() +"\"^^xsd:double ";
                    }
                }
                output += ". \r\n\r\n";
            }

            LOG.debug("written spaces");
            out.write(output.getBytes());
            out.flush();
            output = "";

            for(Element el : Element.elementList){
                //LOG.debug("Element : " + el.getName());
                output += "inst:"+el.getName() + "\r\n";
                output += "\ta bot:Element ;" + "\r\n";
                if(prod && el.getClassName()!=null && el.getNamespace() != null){
                    output += "\ta " + el.getNamespace() + ":" + el.getClassName() + " ;" + "\r\n";
                }
                output += "\trdfs:label \""+el.getLabel()+"\"^^xsd:string ;" + "\r\n";
                output += "\trdfs:comment \""+el.getDescription()+"\"^^xsd:string ;" + "\r\n";
                output += "\tbot:hasGuid \""+ GuidHandler.getUncompressedStringFromGuid(el.getGuid()) +"\"^^xsd:string ";
                for(Element el1 : el.getSubElements()){
                    output += ";\r\n";
                    output += "\tbot:hasSubElement inst:"+ el1.getName() + " ";
                }
                for(Element el1 : el.getHostedElements()){
                    output += ";\r\n";
                    output += "\tbot:hostsElement inst:"+ el1.getName() + " ";
                }
                if(props){
                    output += ";\r\n" + "\tprops:tag \""+el.getTag()+"\"^^xsd:string ";
                    for(Property p : el.getProperties()){
                        output += ";\r\n";
                        if(p.getValue().equalsIgnoreCase(".T."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" true ";
                        else if(p.getValue().equalsIgnoreCase(".F."))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" false ";
                        else if(p.getValue().startsWith("\'"))
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue().substring(1) +"\"^^xsd:string ";
                        else
                            output += "\tprops:"+p.getPropertyNameNoSpace()+" \""+ p.getValue() +"\"^^xsd:double ";
                    }
                }
                output += ". \r\n\r\n";

                out.write(output.getBytes());
                out.flush();
                output = "";
            }

            LOG.debug("written properties");
            out.write(output.getBytes());
            out.flush();
            output = "";

            for(Interface iface : Interface.interfaceList){
                output += "inst:"+iface.getName() + "\r\n";
                output += "\ta bot:Interface ;" + "\r\n";
                if(iface.getRelatingSpace() != null && iface.getRelatedElement() != null)
                    output += "\tbot:interfaceOf inst:"+ iface.getRelatingSpace().getName() +", inst:" + iface.getRelatedElement().getName() + " ;" + "\r\n";
                else if(iface.getRelatingSpace() != null && iface.getRelatedElement() == null)
                    output += "\tbot:interfaceOf inst:"+ iface.getRelatingSpace().getName() + " ;" + "\r\n";
                else if(iface.getRelatingSpace() == null && iface.getRelatedElement() != null)
                    output += "\tbot:interfaceOf inst:" + iface.getRelatedElement().getName() + " ;" + "\r\n";
                output += "\tbot:hasGuid \""+ GuidHandler.getUncompressedStringFromGuid(iface.getGuid()) +"\"^^xsd:string . \r\n\r\n";
            }

            LOG.debug("written interfaces");
            out.write(output.getBytes());
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
