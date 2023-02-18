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

public class AttributeVO implements Serializable {
    private static final long serialVersionUID = -7269719435899663513L;
    private String name;
    private String originalName;
    private EntityVO domain;
    private String rangeNS;

    private boolean array = false;
    private boolean set = false;
    private boolean list = false;
    private int minCard = -1;
    private int maxCard = -1;
    private boolean optional = false;

    private boolean listOfList = false;
    private int minCardListOfList = -1;
    private int maxCardListOfList = -1;

    private TypeVO type;
    boolean unique = false;
    private boolean reversePointer; // defined in another class
    boolean isOne2One = false;

    private InverseVO pointsFrom;

    public AttributeVO(String name, TypeVO type, boolean isArray, boolean isSet, boolean isList, boolean isListOfList, int minCard, int maxCard, int tmpListOfListMinCard, int tmpListOfListMaxCard,
                    boolean isOptional) {
        super();
        this.name = name;
        this.type = type;
        this.reversePointer = false;
        this.array = isArray;
        this.set = isSet;
        this.list = isList;
        this.setMinCard(minCard);
        this.setMaxCard(maxCard);
        this.setOptional(isOptional);
        this.setListOfList(isListOfList);
        this.setMinCardListOfList(tmpListOfListMinCard);
        this.setMaxCardListOfList(tmpListOfListMaxCard);
    }

    public boolean isArray() {
        return array;
    }

    public void setArray(boolean array) {
        this.set = array;
    }

    public boolean isSet() {
        return set;
    }

    public void setSet(boolean set) {
        this.set = set;
    }

    public boolean isList() {
        return list;
    }

    public void setList(boolean list) {
        this.list = list;
    }

    public boolean isListOfList() {
        return listOfList;
    }

    public void setListOfList(boolean listOfList) {
        this.listOfList = listOfList;
    }

    public int getMinCard() {
        return minCard;
    }

    public void setMinCard(int minCard) {
        this.minCard = minCard;
    }

    public int getMaxCard() {
        return maxCard;
    }

    public void setMaxCard(int maxCard) {
        this.maxCard = maxCard;
    }

    public int getMinCardListOfList() {
        return minCardListOfList;
    }

    public void setMinCardListOfList(int minCardListOfList) {
        this.minCardListOfList = minCardListOfList;
    }

    public int getMaxCardListOfList() {
        return maxCardListOfList;
    }

    public void setMaxCardListOfList(int maxCardListOfList) {
        this.maxCardListOfList = maxCardListOfList;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
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

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public TypeVO getType() {
        return type;
    }

    public void setType(TypeVO type) {
        this.type = type;
    }

    public boolean isReversePointer() {
        return reversePointer;
    }

    public void setReversePointer(boolean reversePointer) {
        this.reversePointer = reversePointer;
    }

    public InverseVO getPointsFrom() {
        return pointsFrom;
    }

    public void setPointsFrom(InverseVO pointsFrom) {
        this.pointsFrom = pointsFrom;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public boolean isOne2One() {
        return isOne2One;
    }

    public void setOne2One(boolean isOne2One) {
        this.isOne2One = isOne2One;
    }

    public EntityVO getDomain() {
        return domain;
    }

    public void setDomain(EntityVO domain) {
        this.domain = domain;
    }

    @Override
    public String toString() {
        return "AttributeVO [name=" + name + ", type=" + type + ", reverse_pointer=" + reversePointer + ", points_from=" + pointsFrom + ", set=" + set + "]";
    }

    public String getRangeNS() {
        return rangeNS;
    }

    public void setRangeNS(String rangeNS) {
        this.rangeNS = rangeNS;
    }
}
