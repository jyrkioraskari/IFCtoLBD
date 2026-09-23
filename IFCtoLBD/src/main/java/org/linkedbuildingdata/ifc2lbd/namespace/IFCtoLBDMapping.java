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

	public static final Resource ifcSpaceBoundaryOrigin = resource(ns, "IfcSpaceBoundary");
	public static final Resource geometryInferenceOrigin = resource(ns, "GeometryInference");
	public static final Resource candidateInterface = resource(ns, "CandidateInterface");
	public static final Resource axisAlignedBoundingBoxProximity = resource(ns, "AxisAlignedBoundingBoxProximity");

	public static void addNameSpace(Model model) {
		model.setNsPrefix("ifctolbd", ns);
	}

	private IFCtoLBDMapping() { }
}
