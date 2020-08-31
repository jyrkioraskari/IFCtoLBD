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

public class IfcObjectDefinition {

    private Guid guid = new Guid();
    private long lineNum;
    private String name;
    private IFCVO lineEntry;
    public static List<IfcObjectDefinition> IfcObjectDefinitionList  = new ArrayList<IfcObjectDefinition>();

    public IfcObjectDefinition(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        IfcObjectDefinitionList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "IfcObjectDefinition_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
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

    public String getName() {
        return name;
    }

    public IFCVO getLineEntry() {
        return lineEntry;
    }
}
