/*
 * Copyright 2016  Pieter Pauwels, Ghent University;Lewis John McGibbney, Apache
 * 2016, 2020, Jyrki Oraskari Aalto University, RWTH-Aachen 
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

package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Property;

public class IfcOWL extends abstract_NS {
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
    
    private final Property name_IfcPhysicalQuantity; 
    private final Property quantities_IfcElementQuantity;
    
    private final Property unit_IfcPropertySingleValue;
    private final Property description;
    private final Property name;
    private final Property longName;
    private final Property guid;

    private final Property units_IfcUnitAssignment;
    private final Property unitType_IfcNamedUnit;
    private final Property prefix_IfcSIUnit;
    private final Property name_IfcSIUnit;

    private final Property unitsInContext_IfcProject;
    
    
    private final String IfcRoot;
    private final String IfcProject;
    private final String IfcSite;
    private final String IfcBuilding;
    private final String IfcSpace;
    private final String IfcProduct;
    private final String IfcPropertySet;
    private final String IfcElementQuantity;
    private final String IfcUnitAssignment;
    private final String IfcSIUnit;
    
    private final String IfcDistributionSystem;
    
    private final String LENGTHUNIT;
    private final String AREAUNIT;
    private final String VOLUMEUNIT;
    private final String PLANEANGLEUNIT;
    
    private final String METRE;
    private final String SQUARE_METRE;
    private final String CUBIC_METRE;
    private final String RADIAN;
    
    private final String ifcURI;

    public IfcOWL(String ifcURI) {       
        // There should be no vocabulary that does not end in # or /.  This fixes possible errors
        if(!ifcURI.endsWith("#")&&!ifcURI.endsWith("/"))
            ifcURI=ifcURI+"#";
        this.ifcURI=ifcURI;
        relatingObject_IfcRelDecomposes = property(ifcURI, "relatingObject_IfcRelDecomposes");
        relatedObjects_IfcRelDecomposes = property(ifcURI, "relatedObjects_IfcRelDecomposes");
        relatingStructure_IfcRelContainedInSpatialStructure = property(ifcURI,
                "relatingStructure_IfcRelContainedInSpatialStructure");
        relatedElements_IfcRelContainedInSpatialStructure = property(ifcURI,
                "relatedElements_IfcRelContainedInSpatialStructure");
        
        relatedObjects_IfcRelDefines = property(ifcURI, "relatedObjects_IfcRelDefines");
        relatingPropertyDefinition_IfcRelDefinesByProperties =property(ifcURI, "relatingPropertyDefinition_IfcRelDefinesByProperties");
        
        name_IfcRoot  =property(ifcURI, "name_IfcRoot");
        name_IfcProperty  =property(ifcURI, "name_IfcProperty");
        hasProperties_IfcPropertySet =property(ifcURI, "hasProperties_IfcPropertySet");
        nominalValue_IfcPropertySingleValue =property(ifcURI, "nominalValue_IfcPropertySingleValue");
        
        quantities_IfcElementQuantity =property(ifcURI, "quantities_IfcElementQuantity");
        name_IfcPhysicalQuantity =property(ifcURI, "name_IfcPhysicalQuantity");
        
        
        unit_IfcPropertySingleValue = property(ifcURI, "unit_IfcPropertySingleValue");
        
        description = property(ifcURI, "description_IfcRoot");
        name = property(ifcURI, "name_IfcRoot");
        longName = property(ifcURI, "longName_IfcSpatialStructureElement");
        units_IfcUnitAssignment=property(ifcURI, "units_IfcUnitAssignment");
        
        unitsInContext_IfcProject = property(ifcURI, "unitsInContext_IfcProject");
        
        
        guid = property(ifcURI, "globalId_IfcRoot");
        
        IfcRoot = ifcURI + "IfcRoot";
        IfcProject = ifcURI + "IfcProject";
        IfcSite = ifcURI + "IfcSite";
        IfcBuilding = ifcURI + "IfcBuilding";
        IfcSpace = ifcURI + "IfcSpace";
        IfcProduct = ifcURI + "IfcProduct";
        IfcPropertySet = ifcURI + "IfcPropertySet";
        IfcElementQuantity = ifcURI +"IfcElementQuantity";
        IfcUnitAssignment = ifcURI + "IfcUnitAssignment";
        IfcSIUnit = ifcURI + "IfcSIUnit";
        
        IfcDistributionSystem = ifcURI + "IfcDistributionSystem";
        
        
        unitType_IfcNamedUnit = property(ifcURI, "unitType_IfcNamedUnit");
        prefix_IfcSIUnit = property(ifcURI, "prefix_IfcSIUnit");
        name_IfcSIUnit = property(ifcURI, "name_IfcSIUnit");
        LENGTHUNIT= ifcURI + "LENGTHUNIT";
        AREAUNIT= ifcURI + "AREAUNIT";
        VOLUMEUNIT= ifcURI + "VOLUMEUNIT";
        PLANEANGLEUNIT= ifcURI + "PLANEANGLEUNIT";
        
        METRE= ifcURI + "METRE";
        SQUARE_METRE= ifcURI + "SQUARE_METRE";
        CUBIC_METRE= ifcURI + "CUBIC_METRE";
        RADIAN= ifcURI + "RADIAN";

    }

    public String getIfcURI() {
        return ifcURI;
    }

    public Property getProperty(String name) {
        return property(ifcURI, name);
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

    
    public Property getQuantities_IfcElementQuantity() {
		return quantities_IfcElementQuantity;
	}

    
    public Property getNominalValue_IfcPropertySingleValue() {
        return nominalValue_IfcPropertySingleValue;
    }


	public Property getName_IfcPhysicalQuantity() {
		return name_IfcPhysicalQuantity;
	}

	public Property getUnit_IfcPropertySingleValue() {
        return unit_IfcPropertySingleValue;
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

    public Property getUnits_IfcUnitAssignment() {
        return units_IfcUnitAssignment;
    }

    public Property getUnitType_IfcNamedUnit() {
        return unitType_IfcNamedUnit;
    }

    public Property getName_IfcSIUnit() {
        return name_IfcSIUnit;
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

    public String getIfcUnitAssignment() {
        return IfcUnitAssignment;
    }

    public String getIfcSIUnit() {
        return IfcSIUnit;
    }
    
    public String getIfcDistributionSystem() {
        return IfcDistributionSystem;
    }

    public String getLENGTHUNIT() {
        return LENGTHUNIT;
    }

    public String getAREAUNIT() {
        return AREAUNIT;
    }

    public String getVOLUMEUNIT() {
        return VOLUMEUNIT;
    }

    public String getPLANEANGLEUNIT() {
        return PLANEANGLEUNIT;
    }

    public String getMETRE() {
        return METRE;
    }

    public String getSQUARE_METRE() {
        return SQUARE_METRE;
    }

    public String getCUBIC_METRE() {
        return CUBIC_METRE;
    }

    public String getRADIAN() {
        return RADIAN;
    }

    public String getIfcPropertySet() {
        return IfcPropertySet;
    }

    

	public String getIfcElementQuantity() {
		return IfcElementQuantity;
	}

	public Property getHasProperties_IfcPropertySet() {
        return hasProperties_IfcPropertySet;
    }
    
    public Property getPrefix_IfcSIUnit() {
        return prefix_IfcSIUnit;
    }



    public Property getUnitsInContext_IfcProject() {
        return unitsInContext_IfcProject;
    }

    

    public String getIfcRoot() {
        return IfcRoot;
    }
    
    public String getIfcProject() {
        return IfcProject;
    }

    public static class Express
    {
        public static final String EXPRESS = "https://w3id.org/express#";
        public static final Property hasString = property(EXPRESS, "hasString");
        public static final Property hasDouble = property(EXPRESS, "hasDouble");
        public static final Property hasInteger = property(EXPRESS, "hasInteger");
        public static final Property hasBoolean = property(EXPRESS, "hasBoolean");
        public static final Property hasLogical = property(EXPRESS, "hasLogical");

        static public Property getHasString() {
            return hasString;
        }

        static public Property getHasDouble() {
            return hasDouble;
        }

        static public Property getHasInteger() {
            return hasInteger;
        }

        static public Property getHasBoolean() {
            return hasBoolean;
        }

        static  public Property getHasLogical() {
            return hasLogical;
        }


    }
}
