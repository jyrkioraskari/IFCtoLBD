package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;
import org.linkedbuildingdata.ifc2lbd.namespace.IfcOWL;

class IfcTopologyMappingStageTest {
	private static final String IFC_NS = "https://example.com/ifc#";

	@Test
	void explicitBoundariesRemainDistinctAndPreserveIfcEvidence() {
		Model ifcModel = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC_NS);
		Resource sourceSpace = ifcModel.createResource("urn:ifc:space");
		Resource sourceWall = ifcModel.createResource("urn:ifc:wall");
		Resource sourceOtherSpace = ifcModel.createResource("urn:ifc:other-space");
		Resource outputSpace = output.createResource("urn:lbd:space");
		Resource outputWall = output.createResource("urn:lbd:wall");
		Resource outputOtherSpace = output.createResource("urn:lbd:other-space");
		var mapped = new HashMap<Resource, Resource>();
		mapped.put(sourceSpace, outputSpace);
		mapped.put(sourceWall, outputWall);
		mapped.put(sourceOtherSpace, outputOtherSpace);

		Resource first = boundary(ifcModel, ifc, "first", "guid-first", sourceSpace, sourceWall,
				"PHYSICAL", "INTERNAL");
		Resource second = boundary(ifcModel, ifc, "second", "guid-second", sourceOtherSpace, sourceWall,
				"PHYSICAL", "INTERNAL");
		first.addProperty(ifc.getProperty("correspondingBoundary_IfcRelSpaceBoundary2ndLevel"), second);
		Resource connectionGeometry = ifcModel.createResource("urn:ifc:connection-geometry");
		first.addProperty(ifc.getProperty("connectionGeometry_IfcRelSpaceBoundary"), connectionGeometry);

		IfcSpaceBoundaryStage.map(ifcModel, ifc, output, mapped,
				(source, type) -> output.createResource("urn:lbd:" + source.getLocalName()), (source, target) -> { });

		Resource mappedFirst = mapped.get(first);
		Resource mappedSecond = mapped.get(second);
		assertTrue(output.contains(mappedFirst, RDF.type, BOT.bot_interface));
		assertTrue(output.contains(mappedFirst, BOT.bot_interfaceOf, outputSpace));
		assertTrue(output.contains(mappedFirst, BOT.bot_interfaceOf, outputWall));
		assertTrue(output.contains(mappedFirst, BOT.bot_interfaceOf, outputOtherSpace));
		assertTrue(output.contains(mappedFirst, IFCtoLBDMapping.correspondingBoundary, mappedSecond));
		assertTrue(output.contains(mappedFirst, IFCtoLBDMapping.connectionGeometry, connectionGeometry));
		assertTrue(output.contains(mappedFirst, IFCtoLBDMapping.interfaceOrigin,
				IFCtoLBDMapping.ifcSpaceBoundaryOrigin));
		assertTrue(output.contains(mappedFirst, IFCtoLBDMapping.ifcGlobalId, "guid-first"));
		assertTrue(output.contains(mappedFirst, IFCtoLBDMapping.boundaryLevel, "2ndLevel"));
		assertTrue(output.contains(mappedFirst, IFCtoLBDMapping.physicalOrVirtualBoundary, "PHYSICAL"));
		assertTrue(output.contains(mappedFirst, IFCtoLBDMapping.internalOrExternalBoundary, "INTERNAL"));
		assertEquals(2, output.listResourcesWithProperty(RDF.type, BOT.bot_interface).toList().size());
	}

	@Test
	void zoneMembershipUsesAssignsToGroupAndPreservesOverlap() {
		Model ifcModel = ModelFactory.createDefaultModel();
		Model output = ModelFactory.createDefaultModel();
		IfcOWL ifc = new IfcOWL(IFC_NS);
		Resource sourceSpace = ifcModel.createResource("urn:ifc:space");
		Resource outputSpace = output.createResource("urn:lbd:space");
		Resource thermal = zone(ifcModel, "thermal");
		Resource apartment = zone(ifcModel, "apartment");
		Resource parent = zone(ifcModel, "parent");
		group(ifcModel, ifc, "thermal-members", thermal, sourceSpace);
		group(ifcModel, ifc, "apartment-members", apartment, sourceSpace);
		group(ifcModel, ifc, "parent-members", parent, apartment);
		var mapped = new HashMap<Resource, Resource>();
		mapped.put(sourceSpace, outputSpace);

		IfcZoneStage.map(ifcModel, ifc, output, mapped,
				(source, type) -> output.createResource("urn:lbd:" + source.getLocalName()), (source, target) -> { });

		assertTrue(output.contains(mapped.get(thermal), BOT.containsZone, outputSpace));
		assertTrue(output.contains(mapped.get(apartment), BOT.containsZone, outputSpace));
		assertTrue(output.contains(mapped.get(parent), BOT.containsZone, mapped.get(apartment)));
		assertEquals(3, output.listResourcesWithProperty(RDF.type, BOT.zone).toList().size());
	}

	private static Resource boundary(Model model, IfcOWL ifc, String id, String guid, Resource space,
			Resource element, String physical, String location) {
		Resource boundary = model.createResource("urn:ifc:" + id)
				.addProperty(RDF.type, model.createResource(IFC_NS + "IfcRelSpaceBoundary2ndLevel"))
				.addProperty(ifc.getProperty("relatingSpace_IfcRelSpaceBoundary"), space)
				.addProperty(ifc.getProperty("relatedBuildingElement_IfcRelSpaceBoundary"), element)
				.addProperty(ifc.getProperty("physicalOrVirtualBoundary_IfcRelSpaceBoundary"),
						model.createResource(IFC_NS + physical))
				.addProperty(ifc.getProperty("internalOrExternalBoundary_IfcRelSpaceBoundary"),
						model.createResource(IFC_NS + location));
		boundary.addProperty(ifc.getGuid(), model.createResource().addLiteral(IfcOWL.Express.getHasString(), guid));
		return boundary;
	}

	private static Resource zone(Model model, String id) {
		return model.createResource("urn:ifc:" + id)
				.addProperty(RDF.type, model.createResource(IFC_NS + "IfcZone"));
	}

	private static void group(Model model, IfcOWL ifc, String id, Resource zone, Resource... members) {
		Resource relationship = model.createResource("urn:ifc:" + id)
				.addProperty(ifc.getProperty("relatingGroup_IfcRelAssignsToGroup"), zone);
		Resource head = null;
		Resource previous = null;
		for (Resource member : members) {
			Resource node = model.createResource();
			node.addProperty(model.createProperty("https://w3id.org/list#hasContents"), member);
			if (head == null) head = node;
			if (previous != null)
				previous.addProperty(model.createProperty("https://w3id.org/list#hasNext"), node);
			previous = node;
		}
		relationship.addProperty(ifc.getProperty("relatedObjects_IfcRelAssigns"), head);
	}
}
