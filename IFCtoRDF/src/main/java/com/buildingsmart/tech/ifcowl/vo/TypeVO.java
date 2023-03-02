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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TypeVO implements Serializable {
    private static final long serialVersionUID = -3366648676376786356L;
    private String name;
    private String primarytype;
    private List<String> selectEntities = new LinkedList<>();
    private List<String> enumEntities = new LinkedList<>();
    private List<TypeVO> parentSelect;
    private int[] listCardinalities = new int[2];
    private static List<TypeVO> listOfTypes = new ArrayList<>();

    public TypeVO(String name) {
        super();
        this.name = name;
        this.primarytype = name;
        listOfTypes.add(this);
    }

    public TypeVO(String name, String primarytype) {
        super();
        this.name = name;
        this.primarytype = primarytype;
        listOfTypes.add(this);
    }

    public static TypeVO getTypeVO(String typeName) {
        for (TypeVO t : listOfTypes) {
            if (t.getName().equalsIgnoreCase(typeName))
                return t;
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSelectEntities() {
        return selectEntities;
    }

    public void setSelectEntities(List<String> selectEntities) {
        this.selectEntities = selectEntities;
    }

    public List<TypeVO> getParentSelectTypes() {
        return parentSelect;
    }

    public void addParentSelectType(TypeVO parentSelect) {
        if (this.parentSelect == null)
            this.parentSelect = new ArrayList<>();
        this.parentSelect.add(parentSelect);
    }

    public String getPrimarytype() {
        return primarytype;
    }

    public void setPrimarytype(String primarytype) {
        this.primarytype = primarytype;
    }

    public List<String> getEnumEntities() {
        return enumEntities;
    }

    public void setEnumEntities(List<String> enumEntities) {
        this.enumEntities = enumEntities;
    }

    public int[] getListCardinalities() {
        return listCardinalities;
    }

    public void setListCardinalities(int[] listCardinalities) {
        this.listCardinalities = listCardinalities;
    }

    public static boolean checkIfType(String ptype) {
        for (TypeVO pt : listOfTypes) {
            if (pt.name.equalsIgnoreCase(ptype))
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "TypeVO [name=" + name + ", primarytype=" + primarytype + ", select_entities=" + selectEntities + ", enum_entities=" + enumEntities + "]";
    }
}