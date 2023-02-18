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

public class NamedIndividualVO {

    private String enumName;
    private String namedIndividual;
    private String originalNameOfIndividual;

    public NamedIndividualVO() {
        // UNUSED
    }

    public NamedIndividualVO(String enumName, String namedIndividual) {
        this.enumName = enumName;
        this.namedIndividual = namedIndividual;
        this.originalNameOfIndividual = namedIndividual;
    }

    public NamedIndividualVO(String enumName, String namedIndividual, String originalName) {
        this(enumName, namedIndividual);
        this.originalNameOfIndividual = originalName;
    }

    /**
     * @return the enumName
     */
    public String getEnumName() {
        return enumName;
    }

    /**
     * @param enumName
     *            the enumName to set
     */
    public void setEnumName(String enumName) {
        this.enumName = enumName;
    }

    /**
     * @return the namedIndividual
     */
    public String getNamedIndividual() {
        return namedIndividual;
    }

    /**
     * @param namedIndividual
     *            the namedIndividual to set
     */
    public void setNamedIndividual(String namedIndividual) {
        this.namedIndividual = namedIndividual;
    }

    /**
     * @return the originalNameOfIndividual
     */
    public String getOriginalNameOfIndividual() {
        return originalNameOfIndividual;
    }

    /**
     * @param originalNameOfIndividual
     *            the originalNameOfIndividual to set
     */
    public void setOriginalNameOfIndividual(String originalNameOfIndividual) {
        this.originalNameOfIndividual = originalNameOfIndividual;
    }

}
