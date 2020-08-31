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

public class IfcRelDecomposes {

    private IFCVO lineEntry;
    public static List<IfcRelDecomposes> RelDecomposesList  = new ArrayList<IfcRelDecomposes>();

    public IfcRelDecomposes(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        RelDecomposesList.add(this);
    }


    //------------
    // ACCESSORS
    //------------

    public IFCVO getLineEntry() {
        return lineEntry;
    }


}