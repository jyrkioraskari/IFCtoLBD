package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;

/** Runs the pump fixture through the public converter without registry access. */
class EtimPumpExampleTest {
	private static final String ETIM = "https://identifier.buildingsmart.org/uri/etim/etim/10.0";
	private static final String CLASS = ETIM + "/class/EC010980";
	private static final String OTHER_CLASS = ETIM + "/class/EC000054";
	private static final String NS = SupplyChainStage.NS;
	private static final String MEP_PUMP = "https://pi.pauwel.be/voc/distributionelement#Pump";

	@Test void reviewedAcceptanceMovesOnlyTheReviewedPumpIntoInventory(@TempDir Path temp) throws Exception {
		Path fixture = Path.of("..", "examples", "getting-started", "etim-pumps", "model.ifc");
		assertTrue(Files.isRegularFile(fixture));
		Path input = Files.copy(fixture, temp.resolve("model.ifc"));
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.org/pump-review/");
				ConversionResult baseline = converter.convert(new ConversionRequest(input.toString(),
						ConversionProfiles.SUPPLY_CHAIN).withClassificationResolver(new PumpResolver())
							.withEtimClassificationSuggestions())) {
			Model model = baseline.getModel();
			assertEquals(expected("expected-inventory-before.txt"), rows(model, "inventory.rq"));
			assertEquals(expected("expected-review.txt"), rows(model, "review.rq"));
			assertEquals(expected("expected-review-evidence.txt"), evidenceRows(model));
			assertEquals(5, model.listResourcesWithProperty(RDF.type, BOT.element).toList().size());
			assertEquals(5, model.listResourcesWithProperty(RDF.type,
					model.createResource(MEP_PUMP)).toList().size());
			assertEquals(1, model.listResourcesWithProperty(model.createProperty(NS + "classificationStatus"),
					"supplied").toList().size());
			assertTrue(model.contains(null, model.createProperty(NS + "classificationStatus"), "abstained"));
			Resource reviewed = model.listResourcesWithProperty(org.apache.jena.vocabulary.RDFS.label,
					"P-Reviewed").toList().stream().filter(value -> value.hasProperty(RDF.type, BOT.element))
					.findFirst().orElseThrow();
			var acceptance = new EtimClassificationAcceptance(reviewed.getURI(), CLASS, "review:pump-board/42");
			try (ConversionResult accepted = converter.convert(new ConversionRequest(input.toString(),
						ConversionProfiles.SUPPLY_CHAIN).withClassificationResolver(new PumpResolver())
							.withEtimClassificationSuggestions().withAcceptedEtimClassifications(List.of(acceptance)))) {
				Model output = accepted.getModel();
				assertEquals(expected("expected-inventory-after.txt"), rows(output, "inventory.rq"));
				assertFalse(rows(output, "review.rq").stream().anyMatch(row -> row.startsWith("P-Reviewed|")));
				Resource assertion = output.createResource(reviewed.getURI()).getPropertyResourceValue(
						output.createProperty(NS + "hasClassification"));
				assertTrue(assertion.hasLiteral(output.createProperty(NS + "classificationStatus"), "accepted"));
				assertTrue(output.contains(assertion, output.createProperty(NS + "reviewReference"),
						"review:pump-board/42"));
				assertTrue(output.contains(null, output.createProperty(NS + "hasObservation")));
				ByteArrayOutputStream serialized = new ByteArrayOutputStream();
				RDFDataMgr.write(serialized, output, RDFFormat.TURTLE_PRETTY);
				Model reloaded = ModelFactory.createDefaultModel();
				try {
					RDFDataMgr.read(reloaded, new ByteArrayInputStream(serialized.toByteArray()), Lang.TURTLE);
					assertEquals(expected("expected-inventory-after.txt"), rows(reloaded, "inventory.rq"));
					assertTrue(reloaded.contains(null, reloaded.createProperty(NS + "reviewReference"),
							"review:pump-board/42"));
					assertTrue(reloaded.contains(null, reloaded.createProperty(NS + "hasObservation")));
				} finally {
					reloaded.close();
				}
			}
		}
	}

	@Test void reviewExcludesLabelledWallWhenThereAreNoPumps() throws Exception {
		Model model = ModelFactory.createDefaultModel();
		try {
			model.createResource("urn:wall").addProperty(RDF.type, BOT.element).addProperty(RDFS.label,
					"W-Labelled");
			assertTrue(reviewBindings(model).isEmpty());
		} finally { model.close(); }
	}

	@Test void reviewKeepsCandidateAndEvidenceOnTheSamePumpAndSubtype() throws Exception {
		Model model = ModelFactory.createDefaultModel();
		try {
			Resource otherOnly = pump(model, "urn:other-only", "P-OtherOnly");
			suggestion(model, otherOnly, "urn:other-only-suggestion", OTHER_CLASS, "other-only evidence");
			Resource both = pump(model, "urn:both", "P-Both");
			suggestion(model, both, "urn:target-suggestion", CLASS, "target evidence");
			suggestion(model, both, "urn:other-suggestion", OTHER_CLASS, "other evidence");
			Resource unresolved = pump(model, "urn:unresolved", "P-Unresolved");
			Resource abstained = pump(model, "urn:abstained", "P-Abstained");
			Resource abstention = model.createResource("urn:abstention")
					.addLiteral(model.createProperty(NS + "classificationStatus"), "abstained")
					.addLiteral(model.createProperty(NS + "missingInformation"), "recorded missing evidence");
			abstained.addProperty(model.createProperty(NS + "hasClassificationAssessment"), abstention);
			Resource assessed = pump(model, "urn:assessed", "P-Assessed");
			Resource assessment = model.createResource("urn:assessment")
					.addProperty(model.createProperty(NS + "consideredConcept"), model.createResource(CLASS))
					.addLiteral(model.createProperty(NS + "classificationStatus"), "abstained")
					.addLiteral(model.createProperty(NS + "missingInformation"), "assessed pump only");
			assessed.addProperty(model.createProperty(NS + "hasClassificationAssessment"), assessment);
			assertEquals(Set.of(
					"urn:both|" + CLASS + "|target evidence|",
					"urn:assessed|" + CLASS + "||assessed pump only",
					"urn:abstained|||recorded missing evidence",
					"urn:unresolved|||"), reviewBindings(model));
		} finally { model.close(); }
	}

	@Test void mixedIfcModelDoesNotReviewTheWall(@TempDir Path temp) throws Exception {
		Path fixture = Path.of("..", "examples", "getting-started", "etim-pumps", "model.ifc");
		String source = Files.readString(fixture);
		source = source.replace("(#19,#20,#21,#22,#23),#15)", "(#19,#20,#21,#22,#23,#34),#15)");
		source = source.replace("ENDSEC;\nEND-ISO-10303-21;",
				"#34=IFCWALL('1PUMP00000000000000034',#5,'W-Labelled',$,$,#12,$,$,.NOTDEFINED.);\n"
						+ "ENDSEC;\nEND-ISO-10303-21;");
		Path input = Files.writeString(temp.resolve("mixed.ifc"), source);
		try (ConversionSession session = new ConversionSession(NoGeometryProvider.INSTANCE);
				IFCtoLBDConverter converter = new IFCtoLBDConverter(session, "https://example.org/pump-review/");
				ConversionResult result = converter.convert(new ConversionRequest(input.toString(),
						ConversionProfiles.SUPPLY_CHAIN).withClassificationResolver(new PumpResolver())
						.withEtimClassificationSuggestions())) {
			Model model = result.getModel();
			Resource wall = model.listResourcesWithProperty(RDFS.label, "W-Labelled").toList().stream()
					.filter(resource -> resource.hasProperty(RDF.type, BOT.element)).findFirst().orElseThrow();
			assertFalse(wall.hasProperty(RDF.type, model.createResource(MEP_PUMP)));
			assertEquals(expected("expected-review.txt"), rows(model, "review.rq"));
			Resource reviewed = model.listResourcesWithProperty(RDFS.label, "P-Reviewed").toList().stream()
					.filter(resource -> resource.hasProperty(RDF.type, BOT.element)).findFirst().orElseThrow();
			Set<String> bindings = reviewBindings(model);
			assertTrue(bindings.contains(reviewed.getURI() + "|" + CLASS
					+ "|published-ifc-relationship: IfcPump|complete constraints for observed property Max. discharge flow"));
			assertFalse(bindings.stream().anyMatch(row -> row.startsWith(wall.getURI() + "|")));
		}
	}

	private static Resource pump(Model model, String uri, String label) {
		return model.createResource(uri).addProperty(RDF.type, BOT.element)
				.addProperty(RDF.type, model.createResource(MEP_PUMP)).addProperty(RDFS.label, label);
	}

	private static void suggestion(Model model, Resource pump, String uri, String concept, String evidence) {
		Resource suggestion = model.createResource(uri)
				.addProperty(model.createProperty(NS + "suggestedConcept"), model.createResource(concept))
				.addLiteral(model.createProperty(NS + "evidence"), evidence);
		pump.addProperty(model.createProperty(NS + "hasClassificationSuggestion"), suggestion);
	}

	private static Set<String> reviewBindings(Model model) throws Exception {
		Path path = Path.of("..", "examples", "getting-started", "etim-pumps", "review.rq");
		var query = QueryFactory.create(Files.readString(path));
		query.addResultVar("pump");
		try (var execution = QueryExecutionFactory.create(query, model)) {
			TreeSet<String> bindings = new TreeSet<>();
			var results = execution.execSelect();
			while (results.hasNext()) {
				var row = results.next();
				bindings.add(row.getResource("pump").getURI() + "|"
						+ (row.contains("candidate") ? row.getResource("candidate").getURI() : "") + "|"
						+ literal(row, "support") + "|" + literal(row, "missing"));
			}
			return bindings;
		}
	}

	private static List<String> expected(String file) throws Exception {
		return Files.readAllLines(Path.of("..", "examples", "getting-started", "etim-pumps", file));
	}

	private static List<String> rows(Model model, String file) throws Exception {
		Path path = Path.of("..", "examples", "getting-started", "etim-pumps", file);
		try (var execution = QueryExecutionFactory.create(QueryFactory.create(Files.readString(path)), model)) {
			var results = execution.execSelect();
			TreeSet<String> rows = new TreeSet<>();
			while (results.hasNext()) {
				var row = results.next();
				if (file.equals("inventory.rq")) {
					rows.add(row.getLiteral("identifier").getString() + "|" + row.getResource("pump").getURI()
							+ "|" + row.getLiteral("origin").getString());
				} else {
					var value = row.getLiteral("value");
					rows.add(row.getLiteral("identifier").getString() + "|"
							+ (row.contains("candidate") ? row.getResource("candidate").getURI().replace(ETIM + "/class/", "") : "")
							+ "|" + row.getLiteral("status").getString() + "|"
							+ literal(row, "propertySet") + "|" + literal(row, "property") + "|"
							+ (value == null ? "" : value.getLexicalForm()) + "|"
							+ (value == null || value.getDatatypeURI() == null ? "" : value.getDatatypeURI()));
				}
			}
			return new ArrayList<>(rows);
		}
	}
	private static List<String> evidenceRows(Model model) throws Exception {
		Path path = Path.of("..", "examples", "getting-started", "etim-pumps", "review.rq");
		try (var execution = QueryExecutionFactory.create(QueryFactory.create(Files.readString(path)), model)) {
			TreeSet<String> rows = new TreeSet<>();
			var results = execution.execSelect();
			while (results.hasNext()) {
				var row = results.next();
				String identifier = row.getLiteral("identifier").getString();
				for (String field : List.of("support", "conflict", "missing"))
					if (row.contains(field)) rows.add(identifier + "|" + field + "|" + row.getLiteral(field).getString());
			}
			return new ArrayList<>(rows);
		}
	}
	private static String literal(org.apache.jena.query.QuerySolution row, String variable) {
		return row.contains(variable) ? row.getLiteral(variable).getString() : "";
	}

	private static final class PumpResolver implements ClassificationResolver {
		@Override public String id() { return "offline-pump-fixture"; }
		@Override public String version() { return "etim-9.0"; }
		@Override public boolean allowsNetworkAccess() { return false; }
		@Override public Optional<Resolution> resolve(Request request) {
			if (!"EC010980".equals(request.code()) || !CLASS.equals(request.sourceLocation()))
				return Optional.empty();
			return Optional.of(new Resolution(CLASS, id(), "10.0", 1.0));
		}
		@Override public Optional<ClassificationDefinition> definition(Resolution resolution) {
			return Optional.of(EtimPumpExampleTest.definition());
		}
		@Override public List<Candidate> candidates(CandidateRequest request) {
			if (!"IfcPump".equals(request.ifcEntity())) return List.of();
			return List.of(new Candidate(new Resolution(CLASS, id(), "10.0", 1.0), EtimPumpExampleTest.definition(),
					"published-ifc-relationship", null));
		}
	}

	private static ClassificationDefinition definition() {
		var flow = new ClassificationDefinition.ClassProperty(ETIM + "/class/EC010980/prop/Performance/EF000991",
				new ClassificationDefinition.Property(ETIM + "/prop/EF000991", "EF000991",
						"Max. discharge flow", null), "Performance", "Real", null, null, null, null,
				List.of(), List.of(new ClassificationDefinition.Unit("m³/h", null)));
		return new ClassificationDefinition(CLASS, ETIM, "EC010980", "Built-in circulation pump",
				"A circulation pump integrated into a system for continuous water movement.",
				List.of("IfcPump"), List.of(flow), List.of());
	}
}
