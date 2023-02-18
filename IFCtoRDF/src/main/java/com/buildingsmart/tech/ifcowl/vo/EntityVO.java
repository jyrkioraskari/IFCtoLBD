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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class EntityVO implements Serializable {
    private static final long serialVersionUID = -3172893316956350157L;
    private String name;
    private List<AttributeVO> attributes = new LinkedList<>();
    private List<InverseVO> inverses = new LinkedList<>();

    private List<AttributeVO> derivedAttributeList = new LinkedList<>();
    private List<InverseVO> derivedInverseList = new LinkedList<>();

    private String superclass;
    private boolean abstractsuperclass = false;
    private List<TypeVO> parentSelect;
    private Set<String> subClassList = new HashSet<>();
    private static List<EntityVO> listOfEntities = new ArrayList<>();

    public EntityVO() {
        //default constructor
    }

    public EntityVO(String name) {
        super();
        this.name = name;
        listOfEntities.add(this);
    }

    public static EntityVO getEntityVO(String entityName) {
        for (EntityVO e : listOfEntities) {
            if (e.getName().equalsIgnoreCase(entityName))
                return e;
        }
        return null;
    }

    public List<InverseVO> getDerivedInverseList() {
        return derivedInverseList;
    }

    public void setDerivedInverseList(List<InverseVO> derivedInverseList) {
        this.derivedInverseList = derivedInverseList;
    }

    public List<InverseVO> getInverses() {
        return inverses;
    }

    public void setInverses(List<InverseVO> inverses) {
        this.inverses = inverses;
    }

    public List<AttributeVO> getDerivedAttributeList() {
        return derivedAttributeList;
    }

    public void setDerivedAttributeList(List<AttributeVO> derivedList) {
        this.derivedAttributeList = derivedList;
    }

    public List<AttributeVO> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<AttributeVO> attributes) {
        this.attributes = attributes;
    }

    public String getSuperclass() {
        return superclass;
    }

    public void setSuperclass(String superclass) {
        this.superclass = superclass;
    }

    public List<TypeVO> getParentSelectTypes() {
        return parentSelect;
    }

    public void addParentSelectType(TypeVO parentSelect) {
        if (this.parentSelect == null)
            this.parentSelect = new ArrayList<>();
        this.parentSelect.add(parentSelect);
    }

    public void setAbstractSuperclass(boolean abstractsuperclass) {
        this.abstractsuperclass = abstractsuperclass;
    }

    public boolean isAbstractSuperclass() {
        return abstractsuperclass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "EntityVO [name=" + name + ", attributes=" + attributes + ", inverses=" + inverses + ", derived_attribute_list=" + derivedAttributeList + ", derived_inverse_list="
                        + derivedInverseList + ", superclass=" + superclass + ", abstractsuperclass=" + abstractsuperclass + "]";
    }

    /**
     * @return the subClassList
     */
    public Set<String> getSubClassList() {
        return subClassList;
    }

    /**
     * @param subClassList
     *            the subClassList to set
     */
    public void setSubClassList(Set<String> subClassList) {
        this.subClassList = subClassList;
    }
}
