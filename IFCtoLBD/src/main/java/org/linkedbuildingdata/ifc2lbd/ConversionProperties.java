package org.linkedbuildingdata.ifc2lbd;

import java.util.ArrayList;
import java.util.List;

public class ConversionProperties {
	public enum PropertyMode {
		DEFAULT,
		SIMPLE,
		OPM
	}
	public enum NamingStrategy {
		LEGACY,
		HIERARCHICAL,
		STABLE_GUID
	}

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
	private boolean hasInterfaces=false;
	private boolean hasWireframe=false;
	private PropertyMode propertyMode = PropertyMode.DEFAULT;
	private boolean stableIdentity;
	private boolean geometryArtifacts;
	private NamingStrategy namingStrategy = NamingStrategy.LEGACY;
	private List<PropertyMappingRule> propertyMappings = List.of();
	
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

	public boolean isHasInterfaces() {
		return hasInterfaces;
	}

	public void setHasInterfaces(boolean hasInterfaces) {
		this.hasInterfaces = hasInterfaces;
	}

	public boolean hasWireframe() {
		return hasWireframe;
	}

	public void setHasWireframe(boolean hasWireframe) {
		this.hasWireframe = hasWireframe;
	}

	public PropertyMode getPropertyMode() {
		return propertyMode;
	}

	public void setPropertyMode(PropertyMode propertyMode) {
		this.propertyMode = java.util.Objects.requireNonNull(propertyMode, "propertyMode");
	}

	public boolean hasStableIdentity() { return stableIdentity; }
	public void setStableIdentity(boolean stableIdentity) { this.stableIdentity = stableIdentity; if (stableIdentity) this.namingStrategy = NamingStrategy.STABLE_GUID; }
	public boolean hasGeometryArtifacts() { return geometryArtifacts; }
	public void setGeometryArtifacts(boolean geometryArtifacts) { this.geometryArtifacts = geometryArtifacts; }
	public List<PropertyMappingRule> getPropertyMappings() { return propertyMappings; }
	public void setPropertyMappings(List<PropertyMappingRule> mappings) {
		this.propertyMappings = List.copyOf(mappings == null ? List.of() : new ArrayList<>(mappings));
	}
	public NamingStrategy getNamingStrategy() { return namingStrategy; }
	public void setNamingStrategy(NamingStrategy strategy) {
		this.namingStrategy = java.util.Objects.requireNonNull(strategy, "strategy");
		this.hasHierarchicalNaming = strategy == NamingStrategy.HIERARCHICAL;
		this.stableIdentity = strategy == NamingStrategy.STABLE_GUID;
	}
	
	
}
