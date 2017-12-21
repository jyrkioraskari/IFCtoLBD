/*
 * Copyright 2016 Pieter Pauwels, Ghent University; Jyrki Oraskari, Aalto University; Lewis John McGibbney, Apache
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

package org.lbd.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public class IfcOwl extends abstract_NS {

	public static final String IFCOWL_Schema = "https://raw.githubusercontent.com/pipauwel/IFCtoRDF/master/src/main/resources/IFC2X3_TC1.rdf";
	public static final String EXPRESS = "https://w3id.org/express#";
	public static final String SIMPLEBIM = "http://ifcowl.openbimstandards.org/SimpleBIM";

	public static final String IFC2x3 = "http://www.buildingsmart-tech.org/ifcOWL/IFC2X3_TC1#";
	public static final Property relatingObject_IfcRelDecomposes = property(IFC2x3, "relatingObject_IfcRelDecomposes");
	public static final Property relatedObjects_IfcRelDecomposes = property(IFC2x3, "relatedObjects_IfcRelDecomposes");
	public static final Property relatingStructure_IfcRelContainedInSpatialStructure = property(IFC2x3,
			"relatingStructure_IfcRelContainedInSpatialStructure");
	public static final Property relatedElements_IfcRelContainedInSpatialStructure = property(IFC2x3,
			"relatedElements_IfcRelContainedInSpatialStructure");
	
	public static final Property relatedObjects_IfcRelDefines = property(IFC2x3, "relatedObjects_IfcRelDefines");
	public static final Property relatingPropertyDefinition_IfcRelDefinesByProperties =property(IFC2x3, "relatingPropertyDefinition_IfcRelDefinesByProperties");
	
	public static final Property name_IfcProperty  =property(IFC2x3, "name_IfcProperty");
	public static final Property hasProperties_IfcPropertySet =property(IFC2x3, "hasProperties_IfcPropertySet");
	public static final Property nominalValue_IfcPropertySingleValue =property(IFC2x3, "nominalValue_IfcPropertySingleValue");
	
	
	public static final Property description = property(IFC2x3, "description_IfcRoot");
	public static final Property name = property(IFC2x3, "name_IfcRoot");
	public static final Property guid = property(IFC2x3, "globalId_IfcRoot");
	public static final Property guid_simple = property(IFC2x3, "guid");

	public static final String IfcBuilding = IFC2x3 + "IfcBuilding";
	public static final String IfcSite = IFC2x3 + "IfcSite";
	public static final String IfcSpace = IFC2x3 + "IfcSpace";
	public static final String IfcProduct = IFC2x3 + "IfcProduct";
	public static final String IfcPropertySet = IFC2x3 + "IfcPropertySet";

	public static void addNameSpace(Model model) {
		model.setNsPrefix("ifc", IFC2x3);
	}

	public static final Property hasString = property(EXPRESS, "hasString");

}
