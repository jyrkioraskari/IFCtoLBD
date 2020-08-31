package nl.tue.isbe.BOT;

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

public class Quantity {

    private long lineNum;

    private Element relatedElement;
    //private String quantityType;
    private String value;
    private String quantityName;
    private String quantityNameNoSpace;

    private IFCVO lineEntry;
    public static List<Quantity> quantityList = new ArrayList<Quantity>();

    public Quantity(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        lineNum = lineEntry.getLineNum();
        quantityList.add(this);
        this.parse();
    }

    private void parse(){
        quantityName = ((String) lineEntry.getObjectList().get(0)).substring(1);
        quantityName = quantityName.substring(0, 1).toLowerCase() + quantityName.substring(1);
        quantityName = quantityName.replaceAll("[-+.^():/,]","");
        quantityNameNoSpace = quantityName.replaceAll("\\s+","");
        //quantityType = (String) lineEntry.getObjectList().get(4);
        value = (String)lineEntry.getObjectList().get(6);
    }

    //------------
    // ACCESSORS
    //------------

    public long getLineNum() {
        return lineNum;
    }

    public IFCVO getLineEntry(IFCVO lineEntry) {
        return lineEntry;
    }

    public String getValue() {
        return value;
    }

    public String getQuantityName() {
        return quantityName;
    }

    public String getQuantityNameNoSpace() {
        return quantityNameNoSpace;
    }
}
