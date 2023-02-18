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

public class InverseVO implements Serializable {
    private static final long serialVersionUID = -6700903862493542784L;
    private String name;
    private String classRange;
    private String inverseOfProperty;
    private boolean set = false;
    private boolean unique = false;
    private int minCard = -1;
    private int maxCard = -1;
    private PropertyVO associatedProperty = null;

    public InverseVO(String name, String classRange, String inverseOfProperty, boolean set, int minCard, int maxCard) {
        super();
        this.name = name;
        this.classRange = classRange;
        this.inverseOfProperty = inverseOfProperty;
        this.set = set;
        this.minCard = minCard;
        this.maxCard = maxCard;
    }

    public boolean isSet() {
        return set;
    }

    public void setSet(boolean set) {
        this.set = set;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClassRange() {
        return classRange;
    }

    public void setClassRange(String classRange) {
        this.classRange = classRange;
    }

    public String getInverseOfProperty() {
        return inverseOfProperty;
    }

    public void setInverseOfProperty(String inverseOfProperty) {
        this.inverseOfProperty = inverseOfProperty;
    }

    public String getLowerCaseName() {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
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

    public PropertyVO getAssociatedProperty() {
        return associatedProperty;
    }

    public void setAssociatedProperty(PropertyVO associatedProperty) {
        this.associatedProperty = associatedProperty;
    }

    @Override
    public String toString() {
        return "InverseVO [name=" + name + ", classRange=" + classRange + ", inverseOfProperty=" + inverseOfProperty + ", set=" + set + "]";
    }
}
