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

package org.lbd.ifc2lbd.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public class IfcOWLNameSpace extends abstract_NS {
	public static final String EXPRESS = "https://w3id.org/express#";
	public static final String SIMPLEBIM = "http://ifcowl.openbimstandards.org/SimpleBIM";

	private final Property relatingObject_IfcRelDecomposes;
	private final Property relatedObjects_IfcRelDecomposes;
	private final Property relatingStructure_IfcRelContainedInSpatialStructure;
	private final Property relatedElements_IfcRelContainedInSpatialStructure;
	
	private final Property relatedObjects_IfcRelDefines;
	private final Property relatingPropertyDefinition_IfcRelDefinesByProperties;
	private final Property name_IfcRoot;
	private final Property name_IfcProperty;
	private final Property hasProperties_IfcPropertySet;
	private final Property nominalValue_IfcPropertySingleValue;
	private final Property description;
	private final Property name;
	private final Property longName;
	
	private final Property guid;

	private final String IfcBuilding;
	private final String IfcSite;
	private final String IfcSpace;
	private final String IfcProduct;
	private final String IfcPropertySet;
	
	private final String ifcURI;
	
	public IfcOWLNameSpace(String ifcURI)
	{
		this.ifcURI=ifcURI;
		relatingObject_IfcRelDecomposes = property(ifcURI, "#relatingObject_IfcRelDecomposes");
		relatedObjects_IfcRelDecomposes = property(ifcURI, "#relatedObjects_IfcRelDecomposes");
		relatingStructure_IfcRelContainedInSpatialStructure = property(ifcURI,
				"#relatingStructure_IfcRelContainedInSpatialStructure");
		relatedElements_IfcRelContainedInSpatialStructure = property(ifcURI,
				"#relatedElements_IfcRelContainedInSpatialStructure");
		
		relatedObjects_IfcRelDefines = property(ifcURI, "#relatedObjects_IfcRelDefines");
		relatingPropertyDefinition_IfcRelDefinesByProperties =property(ifcURI, "#relatingPropertyDefinition_IfcRelDefinesByProperties");
		
		name_IfcRoot  =property(ifcURI, "#name_IfcRoot");
		name_IfcProperty  =property(ifcURI, "#name_IfcProperty");
		hasProperties_IfcPropertySet =property(ifcURI, "#hasProperties_IfcPropertySet");
		nominalValue_IfcPropertySingleValue =property(ifcURI, "#nominalValue_IfcPropertySingleValue");
		
		description = property(ifcURI, "#description_IfcRoot");
		name = property(ifcURI, "#name_IfcRoot");
		longName = property(ifcURI, "#longName_IfcSpatialStructureElement");
		
		guid = property(ifcURI, "#globalId_IfcRoot");

		IfcBuilding = ifcURI + "#IfcBuilding";
		IfcSite = ifcURI + "#IfcSite";
		IfcSpace = ifcURI + "#IfcSpace";
		IfcProduct = ifcURI + "#IfcProduct";
		IfcPropertySet = ifcURI + "#IfcPropertySet";
	}
	
	
	
	public String getIfcURI() {
		return ifcURI;
	}



	public Property getProperty(String name)
	{
		return property(ifcURI, "#"+name);
	}

	public Property getRelatingObject_IfcRelDecomposes() {
		return relatingObject_IfcRelDecomposes;
	}
	public Property getRelatedObjects_IfcRelDecomposes() {
		return relatedObjects_IfcRelDecomposes;
	}
	public Property getRelatingStructure_IfcRelContainedInSpatialStructure() {
		return relatingStructure_IfcRelContainedInSpatialStructure;
	}
	public Property getRelatedElements_IfcRelContainedInSpatialStructure() {
		return relatedElements_IfcRelContainedInSpatialStructure;
	}
	public Property getRelatedObjects_IfcRelDefines() {
		return relatedObjects_IfcRelDefines;
	}
	public Property getRelatingPropertyDefinition_IfcRelDefinesByProperties() {
		return relatingPropertyDefinition_IfcRelDefinesByProperties;
	}
	public Property getName_IfcRoot() {
		return name_IfcRoot;
	}
	public Property getName_IfcProperty() {
		return name_IfcProperty;
	}
	public Property getNominalValue_IfcPropertySingleValue() {
		return nominalValue_IfcPropertySingleValue;
	}
	public Property getDescription() {
		return description;
	}
	public Property getName() {
		return name;
	}
	public Property getLongName() {
		return longName;
	}
	public Property getGuid() {
		return guid;
	}
	public String getIfcBuilding() {
		return IfcBuilding;
	}
	public String getIfcSite() {
		return IfcSite;
	}
	public String getIfcSpace() {
		return IfcSpace;
	}
	public String getIfcProduct() {
		return IfcProduct;
	}
	public String getIfcPropertySet() {
		return IfcPropertySet;
	}
	static public Property getHasString() {
		return hasString;
	}
	public Property getHasDouble() {
		return hasDouble;
	}
	public Property getHasInteger() {
		return hasInteger;
	}
	public Property getHasBoolean() {
		return hasBoolean;
	}
	public Property getHasLogical() {
		return hasLogical;
	}
	public Property getHasProperties_IfcPropertySet() {
		return hasProperties_IfcPropertySet;
	}

	public static final Property hasString = property(EXPRESS, "hasString");
	public static final Property hasDouble = property(EXPRESS, "hasDouble");
	public static final Property hasInteger = property(EXPRESS, "hasInteger");
	public static final Property hasBoolean = property(EXPRESS, "hasBoolean");
	public static final Property hasLogical = property(EXPRESS, "hasLogical");
}
