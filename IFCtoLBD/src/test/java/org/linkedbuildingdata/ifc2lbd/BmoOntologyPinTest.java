package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.namespace.BMO;

class BmoOntologyPinTest {
	private static final Resource OPM_PROPERTY =
			ModelFactory.createDefaultModel().createResource("https://w3id.org/opm#Property");

	@Test
	void bundledOntologyMatchesPinnedSource() throws Exception {
		byte[] bytes;
		try (InputStream input = getClass().getResourceAsStream(BMO.SOURCE_RESOURCE)) {
			bytes = input.readAllBytes();
		}
		assertEquals(BMO.SOURCE_SHA256,
				HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
	}

	@Test
	void everyExposedBmoTermHasItsDeclaredKindAndSemantics() {
		Model ontology = loadOntology();
		for (Resource type : List.of(BMO.Material, BMO.MaterialDefinition, BMO.LayerSet, BMO.Layer,
				BMO.MaterialType, BMO.CompositeMaterial, BMO.MetallicMaterial)) {
			assertTrue(ontology.contains(type, RDF.type, OWL.Class), type + " must be an OWL class");
		}
		assertTrue(ontology.contains(BMO.Concrete, RDF.type, OWL.NamedIndividual));
		assertTrue(ontology.contains(BMO.Concrete, RDF.type, BMO.CompositeMaterial));
		assertTrue(ontology.contains(BMO.Steel, RDF.type, OWL.NamedIndividual));
		assertTrue(ontology.contains(BMO.Steel, RDF.type, BMO.MetallicMaterial));

		assertObjectProperty(ontology, BMO.hasMaterialName, BMO.Material, BMO.MaterialType);
		assertObjectProperty(ontology, BMO.hasMaterial, BMO.MaterialDefinition, BMO.Material);
		assertObjectProperty(ontology, BMO.hasLayers, BMO.LayerSet, BMO.Layer);
		assertObjectProperty(ontology, BMO.adjacentLayer, BMO.Layer, BMO.Layer);
		assertTrue(ontology.contains(BMO.hasThickness, RDF.type, OWL.DatatypeProperty));
		assertTrue(ontology.contains(BMO.hasThickness, RDFS.domain, BMO.Layer));
		assertObjectProperty(ontology, BMO.hasUnit, OPM_PROPERTY,
				ontology.createResource("http://qudt.org/schema/qudt/Unit"));
	}

	private static void assertObjectProperty(Model ontology, Property property, Resource domain, Resource range) {
		assertTrue(ontology.contains(property, RDF.type, OWL.ObjectProperty));
		assertTrue(ontology.contains(property, RDFS.domain, domain));
		assertTrue(ontology.contains(property, RDFS.range, range));
	}

	private Model loadOntology() {
		Model ontology = ModelFactory.createDefaultModel();
		try (InputStream input = getClass().getResourceAsStream(BMO.SOURCE_RESOURCE)) {
			RDFDataMgr.read(ontology, input, Lang.TURTLE);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot read pinned BMO ontology", e);
		}
		return ontology;
	}
}
