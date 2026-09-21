package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.PropertySet;
import org.linkedbuildingdata.ifc2lbd.namespace.BSDD;
import org.linkedbuildingdata.ifc2lbd.namespace.OPM;

class PropertiesAsPropertySetsTest {

	@Test
	void writesResolvedBsddReferenceInClassicOpmOutput() {
		String base = "https://example.com/inst#";
		Model model = ModelFactory.createDefaultModel();
		PropertySet propertySet = new PropertySet(base, model, ModelFactory.createDefaultModel(),
				"Pset_WallCommon", 3, false, new HashMap<>(), false);
		propertySet.putPnameValue("LoadBearing", model.createTypedLiteral(true));

		Resource wall = model.createResource(base + "wall_1");
		propertySet.connect(wall, "1xS3BCk291UvhgP2a6eflK");

		Resource property = wall.listProperties().filterKeep(statement -> statement.getObject().isResource()
				&& statement.getResource().hasProperty(RDF.type, OPM.property)).next().getResource();
		Resource reference = property.getPropertyResourceValue(RDFS.seeAlso);
		assertEquals(BSDD.property_ns + "LoadBearing", reference.getURI());
		assertFalse(reference.getURI().contains("linkedbuildingdata.net/IFC4-PSD"));
	}

	@Test
	void writesBsddTypedPropertySetWithCurrentPropertyState() {
		String base = "https://example.com/inst#";
		Model model = ModelFactory.createDefaultModel();
		PropertySet propertySet = new PropertySet(base, model, ModelFactory.createDefaultModel(),
				"Pset_BuildingCommon", 3, false, new HashMap<>(), false);
		propertySet.putPnameValue("NumberOfStoreys", model.createTypedLiteral(4));
		propertySet.setPropertiesAsPropertySets(true);

		Resource building = model.createResource(base + "building_1");
		propertySet.connect(building, "1xS3BCk291UvhgP2a6eflK");

		Resource pset = building.getPropertyResourceValue(BSDD.hasPropertySet);
		assertTrue(model.contains(pset, RDF.type, BSDD.propertySet));
		assertTrue(model.contains(pset, RDF.type,
				BSDD.propertySet(model, "Pset_BuildingCommon").orElseThrow()));

		Resource property = pset.getPropertyResourceValue(BSDD.containsProperty);
		assertTrue(model.contains(property, RDF.type, OPM.property));
		assertTrue(model.contains(property, RDF.type,
				BSDD.property(model, "Pset_BuildingCommon", "NumberOfStoreys").orElseThrow()));

		Resource state = property.getPropertyResourceValue(OPM.hasPropertyState);
		assertTrue(model.contains(state, RDF.type, OPM.currentPropertyState));
		assertEquals(4, state.getProperty(OPM.value).getInt());
		assertEquals(XSDDatatype.XSDdateTime.getURI(),
				state.getProperty(OPM.generatedAtTime).getLiteral().getDatatypeURI());
		assertEquals(BSDD.meta_ns, model.getNsPrefixURI("bsddm"));
	}

	@Test
	void doesNotFabricateBsddIdentifiersForCustomNames() {
		String base = "https://example.com/inst#";
		Model model = ModelFactory.createDefaultModel();
		PropertySet propertySet = new PropertySet(base, model, ModelFactory.createDefaultModel(),
				"Acme_Custom", 3, false, new HashMap<>(), false);
		propertySet.putPnameValue("NumberOfStoreys", model.createTypedLiteral(4));
		propertySet.setPropertiesAsPropertySets(true);

		Resource building = model.createResource(base + "building_1");
		propertySet.connect(building, "1xS3BCk291UvhgP2a6eflK");

		Resource pset = building.getPropertyResourceValue(BSDD.hasPropertySet);
		Resource property = pset.getPropertyResourceValue(BSDD.containsProperty);
		assertFalse(model.contains(pset, RDF.type,
				model.createResource(BSDD.class_ns + "Acme_Custom")));
		assertFalse(model.contains(property, RDF.type,
				model.createResource(BSDD.property_ns + "NumberOfStoreys")));
		assertTrue(model.contains(property, RDF.type, OPM.property));
	}
}
