package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Optional;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.AttributeSet;
import org.linkedbuildingdata.ifc2lbd.core.valuesets.PropertySet;
import org.linkedbuildingdata.ifc2lbd.namespace.PROPS;

class ModelOwnershipTest {

	@Test
	void writesIfcAttributesToTheSelectedResourceModel() {
		Model general = ModelFactory.createDefaultModel();
		Model elements = ModelFactory.createDefaultModel();
		try {
			Resource wall = general.createResource("https://example.test/wall");
			AttributeSet attributes = new AttributeSet("https://example.test/", elements, 1, false,
					UnitResolver.empty(), false, new HashMap<>());
			attributes.putAnameValue("name_IfcRoot", elements.createLiteral("Wall 1"), Optional.empty());

			attributes.connect(wall, "guid");

			assertTrue(elements.contains(elements.createResource(wall.getURI()), RDFS.label, "Wall 1"));
			assertFalse(general.contains(wall, RDFS.label, "Wall 1"));
		} finally {
			general.close();
			elements.close();
		}
	}

	@Test
	void writesThePropertySetRootLinkToThePropertyModel() {
		Model general = ModelFactory.createDefaultModel();
		Model properties = ModelFactory.createDefaultModel();
		Model ontology = ModelFactory.createDefaultModel();
		try {
			Resource wall = general.createResource("https://example.test/wall");
			PropertySet propertySet = new PropertySet("https://example.test/", properties, ontology,
					"Pset_WallCommon", 1, false, UnitResolver.empty(), false);
			propertySet.putPnameValue("LoadBearing", properties.createTypedLiteral(true));

			propertySet.connect(wall, "guid");

			Property loadBearing = properties.createProperty(PROPS.ns + "loadBearing_property_simple");
			assertTrue(properties.contains(properties.createResource(wall.getURI()), loadBearing));
			assertFalse(general.contains(wall, loadBearing));
		} finally {
			general.close();
			properties.close();
			ontology.close();
		}
	}
}
