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
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class RelHostsElement {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;

    private IFCVO lineEntry; //=IFCRELVOIDSELEMENT
    public static List<RelHostsElement> relHostsElementList = new ArrayList<RelHostsElement>();

    private IfcObjectDefinition hostingElement;
    private IfcObjectDefinition hostedElement;
    private IFCVO fillingRel;
    private IFCVO hostingRel;

    public RelHostsElement(IFCVO lineEntry){this.lineEntry = lineEntry;
        lineNum = lineEntry.getLineNum();
        name = "nottobeused_hostsRel_"+lineEntry.getLineNum();
        relHostsElementList.add(this);
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        this.parse();
    }

    private void parse(){
        fillingRel = (IFCVO)lineEntry.getObjectList().get(8);
        hostingRel = (IFCVO)lineEntry.getObjectList().get(10);
        hostingElement = new IfcObjectDefinition(fillingRel);
        hostedElement = new IfcObjectDefinition(hostingRel);
    }

    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }

    public IfcObjectDefinition getHostingElement() {
        return hostingElement;
    }

    public IfcObjectDefinition getHostedElement() {
        return hostedElement;
    }


    /*public static List<IfcObjectDefinition> getRelatedObjectsForRelatingObject(long lineNum){
        for(IfcRelAggregates ira : RelAggregatesList){
            if(ira.getRelatingObject().getLineNum() == lineNum)
                return ira.getRelatedObjects();
        }
        return null;
    }*/
}
