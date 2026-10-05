package org.linkedbuildingdata.ifc2lbd;

import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

/** Sustainability-only normalization and EPD enrichment. */
public final class SustainabilityStage {
	private SustainabilityStage() { }

	public static void enrich(Model ifcModel, IfcOWL ifc, Model output, Map<Resource, Resource> resources) {
		SupplyChainStage.enrichSustainability(ifcModel, ifc, output, resources);
	}
}
