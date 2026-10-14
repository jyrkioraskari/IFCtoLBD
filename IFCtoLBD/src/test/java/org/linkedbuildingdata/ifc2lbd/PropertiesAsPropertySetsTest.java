package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

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
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

class PropertiesAsPropertySetsTest {
	private static final String EVIDENCE = "https://w3id.org/ifctolbd/evidence#";

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
		assertTrue(model.contains(wall, model.createProperty(BSDD.property_ns + "LoadBearing"), property));
		assertFalse(reference.getURI().contains("linkedbuildingdata.net/IFC4-PSD"));
	}

	@Test
	void usesBsddPredicateForKnownSimplePropertyAndPropsForCustomSet() {
		String base = "https://example.com/inst#";
		Model model = ModelFactory.createDefaultModel();
		Resource wall = model.createResource(base + "wall_1");
		PropertySet known = new PropertySet(base, model, ModelFactory.createDefaultModel(),
				"Pset_WallCommon", 1, false, new HashMap<>(), false);
		known.putPnameValue("LoadBearing", model.createTypedLiteral(true));
		known.connect(wall, "wall-guid");
		assertTrue(model.contains(wall, model.createProperty(BSDD.property_ns + "LoadBearing"),
				model.createTypedLiteral(true)));
		assertFalse(model.contains(wall, model.createProperty(PROPS.ns + "loadBearing_property_simple")));

		PropertySet custom = new PropertySet(base, model, ModelFactory.createDefaultModel(),
				"Acme_Custom", 1, false, new HashMap<>(), false);
		custom.putPnameValue("LoadBearing", model.createTypedLiteral(false));
		custom.connect(wall, "wall-guid");
		assertTrue(model.contains(wall, model.createProperty(PROPS.ns + "loadBearing_property_simple"),
				model.createTypedLiteral(false)));
	}

	@Test
	void explicitPropertyMappingOverridesBsddPredicate() {
		String base = "https://example.com/inst#";
		Model model = ModelFactory.createDefaultModel();
		PropertySet propertySet = new PropertySet(base, model, ModelFactory.createDefaultModel(),
				"Pset_WallCommon", 3, false, new HashMap<>(), false);
		propertySet.putPnameValue("LoadBearing", model.createTypedLiteral(true));
		propertySet.setProperty_replace_map(Map.of(PROPS.ns + "loadBearing", "https://example.com/vocab#bearing"));
		Resource wall = model.createResource(base + "wall_1");
		propertySet.connect(wall, "wall-guid");
		assertTrue(model.contains(wall, model.createProperty("https://example.com/vocab#bearing")));
		assertFalse(model.contains(wall, model.createProperty(BSDD.property_ns + "LoadBearing")));
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

	@Test
	void retainsIfcSourceEvidenceForPropertyClaims() {
		String base = "https://example.com/inst#";
		Model model = ModelFactory.createDefaultModel();
		Resource sourceProperty = model.createResource("https://example.com/ifc#IfcPropertySingleValue_42");
		PropertySet propertySet = new PropertySet(base, model, ModelFactory.createDefaultModel(),
				"Pset_DoorCommon", 3, false, UnitResolver.empty(), true,
				"https://example.com/ifc#IfcPropertySet_10", "property");
		propertySet.putPnameValue("Width", model.createTypedLiteral("900", XSDDatatype.XSDdecimal));
		propertySet.putPnameSource("Width", sourceProperty, "Pset_DoorCommon.Width");
		propertySet.setPropertiesAsPropertySets(true);

		Resource door = model.createResource(base + "door_1");
		propertySet.connect(door, "1xS3BCk291UvhgP2a6eflK");
		Resource property = door.getPropertyResourceValue(BSDD.hasPropertySet)
				.getPropertyResourceValue(BSDD.containsProperty);

		assertEquals(sourceProperty, property.getPropertyResourceValue(
				model.createProperty("http://www.w3.org/ns/prov#wasDerivedFrom")));
		assertEquals("Pset_DoorCommon.Width", property.getProperty(model.createProperty(EVIDENCE + "sourcePath")).getString());
		assertEquals("property", property.getProperty(model.createProperty(EVIDENCE + "sourceKind")).getString());
	}
}
