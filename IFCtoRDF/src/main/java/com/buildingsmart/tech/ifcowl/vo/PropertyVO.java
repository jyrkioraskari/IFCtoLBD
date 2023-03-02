/*
 Copyright (c) 2014 Jyrki Oraskari, Aalto University (jyrki [dot] oraskari [at] aalto [dot] fi)
 Copyright (c) 2014 Pieter Pauwels, Ghent University (modifications - pipauwel [dot] pauwels [at] ugent [dot] be / pipauwel [at] gmail [dot] com)
 Copyright (c) 2016 Lewis John McGibbney, Apache (mavenized - lewismc [at] apache [dot] org)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.buildingsmart.tech.ifcowl.vo;

import java.io.Serializable;
import java.util.List;

public class PropertyVO implements Serializable {
    private static final long serialVersionUID = 3706277260287854899L;
    private String name;
    private String originalName;
    private EntityVO domain;

    public enum propertyType {
        TYPEVO, ENTITYVO, SELECT
    }

    private propertyType type;
    private boolean optional = false;
    private String rangeNS;

    private boolean isArray = false;
    private boolean isSet = false;
    private boolean isList = false;
    private int minCardinality = -1;
    private int maxCardinality = -1;

    private boolean listOfList = false;
    private int minCardinalityListOfList = -1;
    private int maxCardinalityListOfList = -1;

    private PropertyVO inverseProperty = null;

    private transient List<String> rangeSelectEntities = null;
    private String range = null;

    public PropertyVO() {
        // basic constructor
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLowerCaseName() {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public String getOriginalNameLowerCase() {
        return Character.toLowerCase(originalName.charAt(0)) + originalName.substring(1);
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public EntityVO getDomain() {
        return domain;
    }

    public void setDomain(EntityVO domain) {
        this.domain = domain;
    }

    public propertyType getType() {
        return type;
    }

    public void setType(propertyType type) {
        this.type = type;
    }

    public boolean isArray() {
        return isArray;
    }

    public void setArray(boolean isArray) {
        this.isArray = isArray;
    }

    public boolean isSet() {
        return isSet;
    }

    public void setSet(boolean isSet) {
        this.isSet = isSet;
    }

    public boolean isList() {
        return isList;
    }

    public void setList(boolean isList) {
        this.isList = isList;
    }

    public boolean isListOfList() {
        return listOfList;
    }

    public void setListOfList(boolean listOfList) {
        this.listOfList = listOfList;
    }

    public List<String> getSelectEntities() {
        return rangeSelectEntities;
    }

    public void setSelectEntities(List<String> selectEntities) {
        this.rangeSelectEntities = selectEntities;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public int getMinCardinality() {
        return minCardinality;
    }

    public void setMinCardinality(int minCardinality) {
        this.minCardinality = minCardinality;
    }

    public int getMaxCardinality() {
        return maxCardinality;
    }

    public void setMaxCardinality(int maxCardinality) {
        this.maxCardinality = maxCardinality;
    }

    public int getMinCardinalityListOfList() {
        return minCardinalityListOfList;
    }

    public void setMinCardinalityListOfList(int minCardinalityListOfList) {
        this.minCardinalityListOfList = minCardinalityListOfList;
    }

    public int getMaxCardinalityListOfList() {
        return maxCardinalityListOfList;
    }

    public void setMaxCardinalityListOfList(int maxCardinalityListOfList) {
        this.maxCardinalityListOfList = maxCardinalityListOfList;
    }

    public PropertyVO getInverseProperty() {
        return inverseProperty;
    }

    public void setInverseProp(PropertyVO inverseProperty) {
        this.inverseProperty = inverseProperty;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public String getRangeNS() {
        return rangeNS;
    }

    public void setRangeNS(String rangeNS) {
        this.rangeNS = rangeNS;
    }

}
