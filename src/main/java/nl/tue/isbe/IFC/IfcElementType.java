package nl.tue.isbe.IFC;

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

import com.buildingsmart.tech.ifcowl.vo.IFCVO;
import nl.tue.isbe.BOT.Element;
import nl.tue.isbe.BOT.Property;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class IfcElementType {

    private Guid guid = new Guid();
    private String ifcName;
    /*private String className;
    private String name;*/
    private long lineNum;

    private String label = "";
    private String description = "";
    private String tag = "";
    private String namespace;

    private String predefinedType = "";

    private List<IFCVO> originalProperties = new ArrayList<IFCVO>();
    private List<Property> properties = new ArrayList<Property>();

    private IFCVO lineEntry;
    public static List<IfcElementType> elementTypeList = new ArrayList<IfcElementType>();

    public IfcElementType(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        lineNum = lineEntry.getLineNum();
        elementTypeList.add(this);
        getCorrectNameBasedOnIFCInput();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        label = ((String) lineEntry.getObjectList().get(4)).substring(1);
        description = ((String) lineEntry.getObjectList().get(6)).substring(1);
        tag = ((String) lineEntry.getObjectList().get(14)).substring(1);
        getPropertiesFromInput((LinkedList)lineEntry.getObjectList().get(10));
        predefinedType = ((String) lineEntry.getObjectList().get(18)).replaceAll("\\.", "");
    }

    //------------
    // ACCESSORS
    //------------

    public Guid getGuid() {
        return guid;
    }

    public long getLineNum() {
        return lineNum;
    }

    /*public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }*/

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getTag() {
        return tag;
    }

    public String getPredefinedType(){ return predefinedType; }

    public String getNamespace(){ return namespace; }

    public IFCVO getLineEntry(IFCVO lineEntry) {
        return lineEntry;
    }

    private void getCorrectNameBasedOnIFCInput(){
        //It has already been checked that these elements are contained in one of the enums with class names
        //System.out.println("Checking Element : " + lineEntry.getName());

        //set IFC name
        for (Element.ifc4_add2_tc1_BEO_classes c : Element.ifc4_add2_tc1_BEO_classes.values()) {
            if (c.name().equalsIgnoreCase(lineEntry.getName().substring(0,lineEntry.getName().length()-4))) {
                ifcName = c.name() + "Type";
                namespace = "beo";
                break;
            }
        }
        if(ifcName==null) {
            for (Element.ifc4_add2_tc1_MEP_classes c : Element.ifc4_add2_tc1_MEP_classes.values()) {
                if (c.name().equalsIgnoreCase(lineEntry.getName().substring(0,lineEntry.getName().length()-4))) {
                    ifcName = c.name() + "Type";
                    namespace = "mep";
                    break;
                }
            }
        }

        //set name
        if(ifcName==null){
            System.out.println("ERROR. No Ifc Name assigned, but that should really be there for : " + lineEntry.getName());
            return;
        }

        /*//TODO: check for predefinedType

        // map to BEO or MEP ontologies
        String nameNoIFC = ifcName.substring(3);

        //rename standard cases
        if(nameNoIFC.endsWith("StandardCase"))
            nameNoIFC = nameNoIFC.substring(0,nameNoIFC.indexOf("StandardCase"));

        if(nameNoIFC.equalsIgnoreCase("BuildingElementProxy")) {
            className = null;
            name = "element" + "_"+lineEntry.getLineNum();
            return;
        }

        for (Element.BEO_classes c : Element.BEO_classes.values()) {
            if (c.name().equalsIgnoreCase(nameNoIFC)) {
                className = c.name();
                name = toLowerCase(c.name()) + "_"+lineEntry.getLineNum();
                return;
            }
        }
        for (Element.MEP_classes c : Element.MEP_classes.values()) {
            if (c.name().equalsIgnoreCase(nameNoIFC)) {
                className = c.name();
                name = toLowerCase(c.name()) + "_"+lineEntry.getLineNum();
                return;
            }
        }

        System.out.println("ERROR. Element with no name : " + lineEntry.getName());*/
    }

    public void getPropertiesFromInput(LinkedList<Object> in){
        for(IFCVO iv : removeClutterFromList(in)){
            //Propertyset
            if(iv.getName().equalsIgnoreCase("IfcPropertySet")) {
                if (iv.getObjectList().get(8).getClass() == LinkedList.class) {
                    List<Object> lvo = (List<Object>) iv.getObjectList().get(8);
                    for (IFCVO j : removeClutterFromList(lvo)) {
                        originalProperties.add(j);
                    }
                } else {
                    System.out.println("found something other : " + iv.getName() + "_" + iv.getLineNum());
                }
            }
            else{
                //Skipping IfcDoorPanelProperties and IfcDoorliningProperties
                //System.out.println("Found not a propertyset where propertyset was expected. Skipped and continuing: " + iv.getName());
            }
        }
    }

    public List<IFCVO> getOriginalProperties() {
        return originalProperties;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void addProperty(Property p){
        properties.add(p);
    }

    private List<IFCVO> removeClutterFromList(List<Object> lvo){
        List<IFCVO> theRealList = new ArrayList<IFCVO>();
        for(Object o : lvo) {
            if(o.getClass().equals(IFCVO.class))
                theRealList.add((IFCVO)o);
        }
        return theRealList;
    }

    private String toLowerCase(String s){
        char c[] = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return(new String(c));
    }

    public static IfcElementType getIfcElementType(Long lineNum){
        for(IfcElementType iet : IfcElementType.elementTypeList){
            if(iet.getLineNum() == lineNum)
                return iet;
        }
        return null;
    }
}
