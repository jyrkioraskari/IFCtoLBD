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

import java.util.ArrayList;
import java.util.List;

public class IfcRelContainedInSpatialStructure {

    private IFCVO lineEntry;
    public static List<IfcRelContainedInSpatialStructure> relContainedInSpatialStructureList  = new ArrayList<IfcRelContainedInSpatialStructure>();

    private IfcObjectDefinition relatingStructure;
    private List<IfcObjectDefinition> relatedElements = new ArrayList<IfcObjectDefinition>();

    public IfcRelContainedInSpatialStructure(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        relContainedInSpatialStructureList.add(this);
        this.parse();
    }

    private void parse(){
        //relatingStructure
        relatingStructure = new IfcObjectDefinition((IFCVO)lineEntry.getObjectList().get(10));
        //relatedElements
        List<Object> lvo = (List<Object>)lineEntry.getObjectList().get(8);
        for(IFCVO j : removeClutterFromList(lvo)) {
            relatedElements.add(new IfcObjectDefinition(j));
        }
    }

    public static List<IfcObjectDefinition> getRelatedElementsForRelatingStructure(long lineNum){
        for(IfcRelContainedInSpatialStructure irciss : relContainedInSpatialStructureList){
            if(irciss.getRelatingStructure().getLineNum() == lineNum)
                return irciss.getRelatedElements();
        }
        return null;
    }

    private List<IFCVO> removeClutterFromList(List<Object> lvo){
        List<IFCVO> theRealList = new ArrayList<IFCVO>();
        for(Object o : lvo) {
            if(o.getClass().equals(IFCVO.class))
                theRealList.add((IFCVO)o);
        }
        return theRealList;
    }

    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public IfcObjectDefinition getRelatingStructure() {
        return relatingStructure;
    }

    public List<IfcObjectDefinition> getRelatedElements() {
        return relatedElements;
    }
}
