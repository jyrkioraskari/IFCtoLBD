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

public class IfcRelDefinesByType {

    private IFCVO lineEntry;
    public static List<IfcRelDefinesByType> relDefinesByTypeList  = new ArrayList<IfcRelDefinesByType>();

    private IFCVO relatingType;
    private List<IFCVO> relatedObjects = new ArrayList<IFCVO>(); // list of objects to which this is attached

    public IfcRelDefinesByType(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        relDefinesByTypeList.add(this);
        this.parse();
    }

    private void parse(){
        //property side
        relatingType = (IFCVO)lineEntry.getObjectList().get(10);
        //if(relatingObject){
        /*if(relatingType.getName().equalsIgnoreCase("IFCELEMENTQUANTITY")){
            relatedQuantities = (List<IFCVO>)relatingType.getObjectList().get(10);
        }
        else {
            relatedProperties = (List<IFCVO>)removeClutterFromList((List<Object>)relatingObject.getObjectList().get(8));
        }*/

        //element side
        List<Object> lvo = (List<Object>)lineEntry.getObjectList().get(8);
        for(IFCVO j : removeClutterFromList(lvo)) {
            relatedObjects.add(j);
        }
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

    public IFCVO getRelatingType() {
        return relatingType;
    }

    public List<IFCVO> getRelatedObjects() {
        return relatedObjects;
    }

}
