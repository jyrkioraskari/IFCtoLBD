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

public class Site {

    private Guid guid = new Guid();
    private String name;
    private long lineNum;
    private List<Building> buildings = new ArrayList<Building>();

    private String label = "";
    private String description = "";

    private List<Property> properties = new ArrayList<Property>();

    private IFCVO lineEntry;
    public static List<Site> siteList = new ArrayList<Site>();

    public Site(IFCVO lineEntry){
        this.lineEntry = lineEntry;
        siteList.add(this);
        lineNum = lineEntry.getLineNum();
        name = "site_"+lineEntry.getLineNum();
        GuidHandler.getGuidFromCompressedString(String.valueOf(lineEntry.getObjectList().get(0)).substring(1), guid);
        label = ((String) lineEntry.getObjectList().get(4)).substring(1);
        description = ((String) lineEntry.getObjectList().get(6)).substring(1);
    }


    //------------
    // ACCESSORS
    //------------

    public void addBuilding(Building b){
        buildings.add(b);
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

    public List<Building> getBuildings() {
        return buildings;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void addProperty(Property p){
        properties.add(p);
    }
}
