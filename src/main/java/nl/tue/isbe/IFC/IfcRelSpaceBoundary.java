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
import nl.tue.isbe.BOT.Interface;
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class IfcRelSpaceBoundary {

    private Guid guid = new Guid();
    private IFCVO lineEntry;
    public static List<IfcRelSpaceBoundary> relSpaceBoundaryList = new ArrayList<IfcRelSpaceBoundary>();

    private IfcObjectDefinition relatingSpace;
    private IfcObjectDefinition relatedBuildingElement;
    private IFCVO connectionGeometry; //useful for creating interface geometry potentially
    private Interface theInterface;

    public IfcRelSpaceBoundary(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        relSpaceBoundaryList.add(this);
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        this.parse();
    }

    private void parse(){
        relatingSpace = new IfcObjectDefinition((IFCVO)lineEntry.getObjectList().get(8));
        if(lineEntry.getObjectList().get(10) instanceof IFCVO)
            relatedBuildingElement = new IfcObjectDefinition((IFCVO)lineEntry.getObjectList().get(10));
        else{
            //no related building element = it is likely empty ($)
        }
        connectionGeometry = (IFCVO)lineEntry.getObjectList().get(12);
        theInterface = new Interface(connectionGeometry, guid);
    }

    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public IfcObjectDefinition getRelatingSpace() {
        return relatingSpace;
    }

    public IfcObjectDefinition getRelatedBuildingElement() {
        return relatedBuildingElement;
    }

    public Interface getInterface() {
        return theInterface;
    }
}
