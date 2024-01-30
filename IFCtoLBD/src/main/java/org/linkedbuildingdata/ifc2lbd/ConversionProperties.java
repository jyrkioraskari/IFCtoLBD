package org.linkedbuildingdata.ifc2lbd;

public class ConversionProperties {
	private boolean hasBuildingElements = true;
	private boolean hasSeparateBuildingElementsModel = false;
	private boolean hasBuildingProperties = true;
	private boolean hasSeparatePropertiesModel = false;
	private boolean hasGeolocation = false;
	private boolean hasGeometry = false;
	private boolean exportIfcOWL=true;
	private boolean hasUnits=false;
	private boolean hasBoundingBoxWKT=false;
	private boolean hasHierarchicalNaming=false;
	private boolean hasPerformanceBoost=true;
	private boolean hasNonLBDElement=true;
	
	public ConversionProperties() {
		
	}

	public boolean isHasBuildingElements() {
		return hasBuildingElements;
	}


	public void setHasBuildingElements(boolean hasBuildingElements) {
		this.hasBuildingElements = hasBuildingElements;
	}


	public boolean isHasSeparateBuildingElementsModel() {
		return hasSeparateBuildingElementsModel;
	}


	public void setHasSeparateBuildingElementsModel(boolean hasSeparateBuildingElementsModel) {
		this.hasSeparateBuildingElementsModel = hasSeparateBuildingElementsModel;
	}


	public boolean isHasBuildingProperties() {
		return hasBuildingProperties;
	}


	public void setHasBuildingProperties(boolean hasBuildingProperties) {
		this.hasBuildingProperties = hasBuildingProperties;
	}


	public boolean isHasSeparatePropertiesModel() {
		return hasSeparatePropertiesModel;
	}


	public void setHasSeparatePropertiesModel(boolean hasSeparatePropertiesModel) {
		this.hasSeparatePropertiesModel = hasSeparatePropertiesModel;
	}


	public boolean isHasGeolocation() {
		return hasGeolocation;
	}


	public void setHasGeolocation(boolean hasGeolocation) {
		this.hasGeolocation = hasGeolocation;
	}


	public boolean isHasGeometry() {
		return hasGeometry;
	}


	public void setHasGeometry(boolean hasGeometry) {
		this.hasGeometry = hasGeometry;
	}


	public boolean isExportIfcOWL() {
		return exportIfcOWL;
	}


	public void setExportIfcOWL(boolean exportIfcOWL) {
		this.exportIfcOWL = exportIfcOWL;
	}


	public boolean isHasUnits() {
		return hasUnits;
	}


	public void setHasUnits(boolean hasUnits) {
		this.hasUnits = hasUnits;
	}

	public boolean hasBoundingBoxWKT() {
		return hasBoundingBoxWKT;
	}

	public void setHasBoundingBoxWKT(boolean hasBoundingBoxWKT) {
		this.hasBoundingBoxWKT = hasBoundingBoxWKT;
	}

	public boolean hasHierarchicalNaming() {
		return hasHierarchicalNaming;
	}

	public void setHasHierarchicalNaming(boolean hasHierarchicalNaming) {
		this.hasHierarchicalNaming = hasHierarchicalNaming;
	}

	public boolean hasPerformanceBoost() {
		return hasPerformanceBoost;
	}

	public void setHasPerformanceBoost(boolean hasPerformanceBoost) {
		this.hasPerformanceBoost = hasPerformanceBoost;
	}

	public boolean hasNonLBDElement() {
		return hasNonLBDElement;
	}

	public void setHasNonLBDElement(boolean hasNonLBDElement) {
		this.hasNonLBDElement = hasNonLBDElement;
	}
	
	
}
