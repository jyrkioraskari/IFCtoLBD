package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IcddPackageWriterTest {
	private static final String CT = IcddPackageWriter.CONTAINER_ONTOLOGY + "#";

	@Test
	void writesIfcAndDistinctModelsWithAnIcddIndex(@TempDir Path directory) throws Exception {
		byte[] ifc = "ISO-10303-21;\nEND-ISO-10303-21;\n".getBytes(StandardCharsets.UTF_8);
		Path source = directory.resolve("example.ifc");
		Files.write(source, ifc);
		Path target = directory.resolve("example.icdd");

		Model general = model("urn:test:general", "urn:test:value:general");
		Model products = model("urn:test:product", "urn:test:value:product");
		Model properties = model("urn:test:property", "urn:test:value:property");
		var attribute = general.createProperty("urn:test:attribute");
		general.createResource(attribute.getURI()).addProperty(RDF.type, OWL.DatatypeProperty)
				.addProperty(RDFS.comment, "IFC standard attribute nameIfcRoot");
		general.createResource("urn:test:general").addLiteral(attribute, "General");
		var property = properties.createProperty("urn:test:pset-property");
		properties.createResource(property.getURI()).addProperty(RDF.type, OWL.DatatypeProperty)
				.addProperty(RDFS.comment, "IFC property set Pset_Test property Value");
		properties.createResource("urn:test:property").addLiteral(property, "value");
		var propertyOccurrence = properties.createResource("urn:test:property-occurrence")
				.addProperty(RDF.type, properties.createResource("https://w3id.org/opm#Property"))
				.addLiteral(RDFS.label, "PSet_Revit_Constraints:topExtensionDistance");
		String instanceNamespace = "https://www.ugent.be/myAwesomeFirstBIMProject#";
		String encodedElementUri = instanceNamespace
				+ "Basic_Wall%3AExterior_-_Brick_on_Block%3A138237";
		general.setNsPrefix("inst", instanceNamespace);
		general.createResource(instanceNamespace + "interface_test")
				.addProperty(general.createProperty("https://w3id.org/bot#interfaceOf"),
						general.createResource(encodedElementUri));
		String geometryUri = encodedElementUri + "_geometry";
		String boundingBoxUri = geometryUri + "_bb";
		var geometry = general.createResource(geometryUri);
		products.createResource(encodedElementUri)
				.addProperty(products.createProperty("https://w3id.org/omg#hasGeometry"),
						products.createResource(geometryUri));
		geometry.addProperty(RDF.type, general.createResource("http://www.opengis.net/ont/geosparql#Geometry"))
				.addLiteral(general.createProperty("http://www.opengis.net/ont/geosparql#asWKT"), "POINT Z (1 2 3)")
				.addProperty(general.createProperty("https://linkedbuildingdata.org/LBD#hasBoundingBox"),
						general.createResource(boundingBoxUri));
		general.createResource(boundingBoxUri)
				.addLiteral(general.createProperty("https://linkedbuildingdata.org/LBD#x-min"), 1.0);
		// Simulate a legacy materialized union and a product/general overlap. ICDD
		// packaging must still produce physically disjoint RDF payloads.
		general.add(properties);
		products.add(properties);
		var sharedProductPredicate = products.createProperty("urn:test:shared-product-predicate");
		products.createResource("urn:test:product").addLiteral(sharedProductPredicate, "shared");
		general.createResource("urn:test:product").addLiteral(sharedProductPredicate, "shared");
		try {
			IcddPackageWriter.write(target, source, general, products, properties);
		} finally {
			general.close();
			products.close();
			properties.close();
		}

		try (ZipFile zip = new ZipFile(target.toFile())) {
			Set<String> entries = zip.stream().map(entry -> entry.getName()).collect(java.util.stream.Collectors.toSet());
			assertTrue(entries.containsAll(Set.of(
					"Index.rdf",
					"Ontology resources/",
					"Payload documents/",
					"Payload triples/",
					"Payload documents/source/example.ifc",
					"Ontology resources/bot.ttl",
					"Ontology resources/props.ttl",
					"Payload documents/lbd/general.ttl",
					"Payload documents/lbd/building-elements.ttl",
					"Payload documents/lbd/geometry.ttl",
					"Payload documents/lbd/properties.ttl")));
			assertArrayEquals(ifc, zip.getInputStream(
					zip.getEntry("Payload documents/source/example.ifc")).readAllBytes());
			assertPayloadSubject(zip, "Payload documents/lbd/general.ttl", "urn:test:general");
			assertPayloadSubject(zip, "Payload documents/lbd/building-elements.ttl", "urn:test:product");
			assertPayloadSubject(zip, "Payload documents/lbd/geometry.ttl", geometryUri);
			assertPayloadSubject(zip, "Payload documents/lbd/properties.ttl", "urn:test:property");
			Model bot = readModel(zip, "Ontology resources/bot.ttl");
			Model props = readModel(zip, "Ontology resources/props.ttl");
			Model generalPayload = readModel(zip, "Payload documents/lbd/general.ttl");
			Model productPayload = readModel(zip, "Payload documents/lbd/building-elements.ttl");
			Model geometryPayload = readModel(zip, "Payload documents/lbd/geometry.ttl");
			Model propertyPayload = readModel(zip, "Payload documents/lbd/properties.ttl");
			try {
				String generalTurtle = new String(zip.getInputStream(
						zip.getEntry("Payload documents/lbd/general.ttl")).readAllBytes(), StandardCharsets.UTF_8);
				assertTrue(generalTurtle.contains("BASE") && generalTurtle.contains("<" + instanceNamespace + ">"),
						"Turtle should declare the instance namespace as its base");
				assertFalse(generalTurtle.contains("<" + encodedElementUri + ">"),
						"Encoded instance IRIs should use the Turtle base instead of repeating the full URI");
				assertTrue(generalPayload.containsResource(generalPayload.createResource(encodedElementUri)),
						"Shortened Turtle must round-trip to the original absolute IRI");
				assertTrue(geometryPayload.contains(
						geometryPayload.createResource(encodedElementUri),
						geometryPayload.createProperty("https://w3id.org/omg#hasGeometry"),
						geometryPayload.createResource(geometryUri)));
				assertTrue(geometryPayload.containsResource(geometryPayload.createResource(boundingBoxUri)));
				assertFalse(generalPayload.contains(null,
						generalPayload.createProperty("https://w3id.org/omg#hasGeometry")));
				assertFalse(productPayload.contains(null,
						productPayload.createProperty("https://w3id.org/omg#hasGeometry")));
				assertFalse(generalPayload.containsResource(generalPayload.createResource(geometryUri)));
				assertTrue(bot.contains(bot.createResource("https://w3id.org/bot#"), RDF.type, OWL.Ontology));
				assertTrue(props.contains(props.createResource(attribute.getURI()), RDF.type, OWL.DatatypeProperty));
				assertTrue(props.contains(props.createResource(property.getURI()), RDF.type, OWL.DatatypeProperty));
				assertTrue(generalPayload.contains(generalPayload.createResource("urn:test:general"),
						generalPayload.createProperty(attribute.getURI())));
				assertTrue(propertyPayload.contains(propertyPayload.createResource("urn:test:property"),
						propertyPayload.createProperty(property.getURI())));
				assertTrue(propertyPayload.contains(propertyPayload.createResource(propertyOccurrence.getURI()), RDFS.label,
						"PSet_Revit_Constraints:topExtensionDistance"));
				assertFalse(generalPayload.containsResource(generalPayload.createResource(propertyOccurrence.getURI())));
				assertFalse(productPayload.containsResource(productPayload.createResource(propertyOccurrence.getURI())));
				assertFalse(generalPayload.contains(generalPayload.createResource(attribute.getURI()), RDF.type));
				assertFalse(propertyPayload.contains(propertyPayload.createResource(property.getURI()), RDF.type));
				assertDisjoint(generalPayload, productPayload);
				assertDisjoint(generalPayload, propertyPayload);
				assertDisjoint(productPayload, propertyPayload);
				assertDisjoint(geometryPayload, generalPayload);
				assertDisjoint(geometryPayload, productPayload);
				assertDisjoint(geometryPayload, propertyPayload);
			} finally {
				bot.close();
				props.close();
				generalPayload.close();
				productPayload.close();
				geometryPayload.close();
				propertyPayload.close();
			}

			Model index = ModelFactory.createDefaultModel();
			try {
				byte[] indexBytes = zip.getInputStream(zip.getEntry("Index.rdf")).readAllBytes();
				RDFDataMgr.read(index, new ByteArrayInputStream(indexBytes), Lang.RDFXML);
				var container = index.listResourcesWithProperty(RDF.type,
						index.createResource(CT + "ContainerDescription")).nextResource();
				assertTrue(container.hasProperty(OWL.imports,
						index.createResource(IcddPackageWriter.CONTAINER_ONTOLOGY)));
				assertEquals("ICDD-Part1-Container",
						container.getProperty(index.createProperty(CT, "conformanceIndicator")).getString());
				Set<String> names = container.listProperties(index.createProperty(CT, "containsDocument"))
						.mapWith(statement -> statement.getResource()
								.getProperty(index.createProperty(CT, "name")).getString())
						.toSet();
				assertTrue(names.containsAll(Set.of(
						"BOT 0.3.2 ontology",
						"Generated properties ontology",
						"BOT topology and spatial IFC attributes",
						"Product/BEO building elements and IFC attributes",
						"Geometry representations and element-to-geometry links",
						"PROPS/OPM property and quantity sets")));
				assertEquals(7, container.listProperties(index.createProperty(CT, "containsDocument")).toList().size());
				container.listProperties(index.createProperty(CT, "containsDocument")).forEachRemaining(statement -> {
					var document = statement.getResource();
					assertNotNull(document.getProperty(index.createProperty(CT, "filename")));
					assertEquals(64, document.getProperty(index.createProperty(CT, "checksum")).getString().length());
				});
			} finally {
				index.close();
			}
		}
	}

	private static void assertDisjoint(Model first, Model second) {
		Model overlap = first.intersection(second);
		try {
			assertTrue(overlap.isEmpty(), "ICDD RDF payloads must not contain duplicate triples");
		} finally {
			overlap.close();
		}
	}

	private static void assertPayloadSubject(ZipFile zip, String entryName, String subject) throws Exception {
		Model model = readModel(zip, entryName);
		try {
			assertTrue(model.containsResource(model.createResource(subject)));
		} finally {
			model.close();
		}
	}

	private static Model readModel(ZipFile zip, String entryName) throws Exception {
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model, zip.getInputStream(zip.getEntry(entryName)), Lang.TURTLE);
		return model;
	}

	private static Model model(String subject, String object) {
		Model model = ModelFactory.createDefaultModel();
		model.createResource(subject).addProperty(model.createProperty("urn:test:predicate"),
				model.createResource(object));
		return model;
	}
}
