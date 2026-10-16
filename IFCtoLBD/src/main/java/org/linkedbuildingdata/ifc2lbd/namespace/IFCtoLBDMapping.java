package org.linkedbuildingdata.ifc2lbd.namespace;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/** Converter metadata used to distinguish source-derived and inferred mappings. */
public final class IFCtoLBDMapping extends abstract_NS {
	public static final String ns = "https://w3id.org/ifctolbd/mapping#";

	public static final Property interfaceOrigin = property(ns, "interfaceOrigin");
	public static final Property ifcGlobalId = property(ns, "ifcGlobalId");
	public static final Property sourceIfcType = property(ns, "sourceIfcType");
	public static final Property boundaryLevel = property(ns, "boundaryLevel");
	public static final Property physicalOrVirtualBoundary = property(ns, "physicalOrVirtualBoundary");
	public static final Property internalOrExternalBoundary = property(ns, "internalOrExternalBoundary");
	public static final Property connectionGeometry = property(ns, "connectionGeometry");
	public static final Property correspondingBoundary = property(ns, "correspondingBoundary");
	public static final Property inferenceMethod = property(ns, "inferenceMethod");
	public static final Property inferenceTolerance = property(ns, "inferenceTolerance");
	/** Connects an LBD element to the provenance record for its selected IFC assignment. */
	public static final Property materialAssignment = property(ns, "materialAssignment");
	public static final Property assignedMaterialDefinition = property(ns, "assignedMaterialDefinition");
	public static final Property assignedMaterial = property(ns, "assignedMaterial");
	public static final Property sourceIfcAssociation = property(ns, "sourceIfcAssociation");
	public static final Property sourceIfcTypeResource = property(ns, "sourceIfcTypeResource");
	public static final Property materialAssignmentInherited = property(ns, "materialAssignmentInherited");
	public static final Property layerSetUsage = property(ns, "layerSetUsage");
	public static final Property layerMembership = property(ns, "layerMembership");
	public static final Property layer = property(ns, "layer");
	public static final Property position = property(ns, "position");
	public static final Property layerSetDirection = property(ns, "layerSetDirection");
	public static final Property directionSense = property(ns, "directionSense");
	public static final Property offsetFromReferenceLine = property(ns, "offsetFromReferenceLine");
	public static final Property materialCategory = property(ns, "materialCategory");
	/** Links a material layer to the normalized QUDT thickness quantity/provenance node. */
	public static final Property thicknessQuantity = property(ns, "thicknessQuantity");

	public static final Resource ifcSpaceBoundaryOrigin = resource(ns, "IfcSpaceBoundary");
	public static final Resource geometryInferenceOrigin = resource(ns, "GeometryInference");
	public static final Resource axisAlignedBoundingBoxProximity = resource(ns, "AxisAlignedBoundingBoxProximity");
	public static final Resource MaterialAssignment = resource(ns, "MaterialAssignment");
	public static final Resource LayerMembership = resource(ns, "LayerMembership");
	public static final Resource LayerSetUsage = resource(ns, "LayerSetUsage");

	public static void addNameSpace(Model model) {
		model.setNsPrefix("ifctolbd", ns);
	}

	private IFCtoLBDMapping() { }
}
