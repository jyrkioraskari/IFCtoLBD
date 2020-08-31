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
import nl.tue.isbe.ifcspftools.Guid;
import nl.tue.isbe.ifcspftools.GuidHandler;

import java.util.ArrayList;
import java.util.List;

public class Space {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;
    private Storey storey;

    private List<Element> adjacentElements = new ArrayList<Element>(); //Relation between a zone and its adjacent building elements, bounding the zone.
    private List<Element> intersectingElements = new ArrayList<Element>(); //Relation between a Zone and a building Element that intersects it.
    private List<Element> containsElements  = new ArrayList<Element>(); //Relation to a building element contained in a zone.

    private List<Property> properties = new ArrayList<Property>();
    private List<Quantity> quantities = new ArrayList<Quantity>();

    private IFCVO lineEntry;
    public static List<Space> spaceList = new ArrayList<Space>();

    public Space(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        spaceList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "space_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
    }

    //------------
    // ACCESSORS
    //------------

    public void setStorey(Storey bs){
        storey = bs;
    }

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

    public List<Element> getAdjacentElements() {
        return adjacentElements;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void addProperty(Property p){
        properties.add(p);
    }

    public List<Quantity> getQuantities() {
        return quantities;
    }

    public void addQuantity(Quantity q){
        quantities.add(q);
    }

    public void addContainedElement(Element el){
        containsElements.add(el);
    }

    public List<Element> getContainedElements() {
        return containsElements;
    }

}
