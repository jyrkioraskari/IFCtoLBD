package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;

class ReviewedInterfaceImporterTest {
	@Test void importsOneVerifiedContactFromTwoConversions(@TempDir Path temp) throws Exception {
		Path existing = Files.copy(Path.of("..", "examples", "verified-interfaces", "existing.ifc"),
				temp.resolve("existing.ifc"));
		Path renovation = Files.copy(Path.of("..", "examples", "verified-interfaces", "renovation.ifc"),
				temp.resolve("renovation.ifc"));
		try (ConversionSession aSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				ConversionSession bSession = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter aConverter = new IFCtoLBDConverter(aSession, "https://example.org/interface/");
				IFCtoLBDConverter bConverter = new IFCtoLBDConverter(bSession, "https://example.org/interface/");
				ConversionResult a = aConverter.convert(new ConversionRequest(existing.toString(),
						ConversionProfiles.REVISION_READY).withModelScope("existing"));
				ConversionResult b = bConverter.convert(new ConversionRequest(renovation.toString(),
						ConversionProfiles.REVISION_READY).withModelScope("renovation"))) {
			Resource wall = element(a.getModel(), "Existing wall");
			Resource insulation = element(b.getModel(), "Renovation insulation");
			assertNotEquals(wall.getURI(), insulation.getURI());
			var wallEndpoint = new ReviewedInterfaceImporter.Endpoint("existing", revision(a), wall.getURI());
			var insulationEndpoint = new ReviewedInterfaceImporter.Endpoint("renovation", revision(b),
					insulation.getURI());
			var reviewed = new ReviewedInterfaceImporter.ReviewedPair(wallEndpoint, insulationEndpoint,
					"site-inspection", "review:contact-42");
			var reversed = new ReviewedInterfaceImporter.ReviewedPair(insulationEndpoint, wallEndpoint,
					"site-inspection", "review:contact-42");
			try (var imported = new ReviewedInterfaceImporter().importPairs(a.getModel(), a.getManifestModel(),
					b.getModel(), b.getManifestModel(), List.of(reviewed, reversed))) {
				assertTrue(imported.diagnostics().isEmpty());
				Model graph = imported.graph();
				assertEquals(1, graph.listResourcesWithProperty(RDF.type, BOT.bot_interface).toList().size());
				Resource contact = graph.listResourcesWithProperty(RDF.type, BOT.bot_interface).next();
				assertTrue(contact.hasProperty(BOT.bot_interfaceOf, graph.createResource(wall.getURI())));
				assertTrue(contact.hasProperty(BOT.bot_interfaceOf, graph.createResource(insulation.getURI())));
				assertTrue(contact.hasLiteral(graph.createProperty(
						"https://w3id.org/ifctolbd/mapping#reviewEvidence"), "review:contact-42"));
				Path companion = temp.resolve("interfaces.ttl");
				try (var output = Files.newOutputStream(companion)) {
					RDFDataMgr.write(output, graph, RDFFormat.TURTLE_PRETTY);
				}
				Model reloaded = ModelFactory.createDefaultModel();
				try {
					RDFDataMgr.read(reloaded, companion.toUri().toString(), Lang.TURTLE);
					Model combined = ModelFactory.createDefaultModel();
					try {
					combined.add(a.getModel()).add(b.getModel()).add(reloaded);
					String sparql = Files.readString(Path.of("..", "examples", "verified-interfaces", "query.rq"));
					try (var query = QueryExecutionFactory.create(QueryFactory.create(sparql), combined)) {
						var results = query.execSelect();
						assertTrue(results.hasNext());
						var row = results.next();
						assertEquals(insulation.getURI(), row.getResource("insulation").getURI());
						assertEquals(wall.getURI(), row.getResource("wall").getURI());
						assertEquals("review:contact-42", row.getLiteral("reviewEvidence").getString());
						assertEquals(Files.readString(Path.of("..", "examples", "verified-interfaces", "expected.txt")).trim(),
								row.getResource("insulation").getURI() + "|" + row.getResource("wall").getURI()
										+ "|" + row.getLiteral("reviewEvidence").getString());
						assertTrue(!results.hasNext());
					}
					} finally { combined.close(); }
				} finally { reloaded.close(); }
			}
			try (var fromFile = new ReviewedInterfaceImporter().importPairs(a, b,
					Path.of("..", "examples", "verified-interfaces", "reviewed-pairs.json"))) {
				assertTrue(fromFile.diagnostics().isEmpty());
				assertEquals(1, fromFile.graph().listResourcesWithProperty(RDF.type, BOT.bot_interface).toList().size());
			}
		}
	}

	@Test void reportsIriCollisionAndMissingEndpoint() {
		Model a = ModelFactory.createDefaultModel();
		Model b = ModelFactory.createDefaultModel();
		Model am = manifest("existing", "sha-a");
		Model bm = manifest("renovation", "sha-b");
		a.createResource("urn:same").addProperty(RDF.type, BOT.element);
		b.createResource("urn:same").addProperty(RDF.type, BOT.element);
		var ae = new ReviewedInterfaceImporter.Endpoint("existing", "sha-a", "urn:same");
		var be = new ReviewedInterfaceImporter.Endpoint("renovation", "sha-b", "urn:same");
		var missing = new ReviewedInterfaceImporter.Endpoint("renovation", "sha-b", "urn:absent");
		try (var result = new ReviewedInterfaceImporter().importPairs(a, am, b, bm, List.of(
				new ReviewedInterfaceImporter.ReviewedPair(ae, be, "inspection", "review:1"),
				new ReviewedInterfaceImporter.ReviewedPair(ae, missing, "inspection", "review:2")))) {
			assertEquals(0, result.graph().size());
			assertEquals(List.of("IRI_COLLISION", "MISSING_ENDPOINT"), result.diagnostics().stream()
					.map(ReviewedInterfaceImporter.Diagnostic::code).toList());
		}
		a.close(); b.close(); am.close(); bm.close();
	}

	private static Resource element(Model model, String label) {
		return model.listResourcesWithProperty(RDFS.label, label).toList().stream()
				.filter(value -> value.hasProperty(RDF.type, BOT.element)).findFirst().orElseThrow();
	}
	private static String revision(ConversionResult result) {
		Resource conversion = result.getManifestModel().listResourcesWithProperty(RDF.type,
				result.getManifestModel().createResource(ConversionManifest.NS + "Conversion")).next();
		return conversion.getProperty(result.getManifestModel().createProperty(
				ConversionManifest.NS + "sourceChecksum")).getString();
	}
	private static Model manifest(String scope, String revision) {
		Model model = ModelFactory.createDefaultModel();
		model.createResource("urn:conversion").addProperty(RDF.type,
				model.createResource(ConversionManifest.NS + "Conversion"))
				.addLiteral(model.createProperty(ConversionManifest.NS + "modelScope"), scope)
				.addLiteral(model.createProperty(ConversionManifest.NS + "sourceChecksum"), revision);
		return model;
	}
}
