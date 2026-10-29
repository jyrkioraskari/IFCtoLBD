package org.linkedbuildingdata.ifc2lbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.GraphRetrievalService.ElementQuery;
import org.linkedbuildingdata.ifc2lbd.GraphRetrievalService.Status;
import org.linkedbuildingdata.ifc2lbd.namespace.BMO;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

class GraphRetrievalServiceTest {
	private static final String EX = "https://example.test/";
	private Model data;
	private Model links;
	private Model provenance;
	private Model linkReport;
	private GraphRetrievalService retrieval;

	@BeforeEach
	void setUp() {
		data = ModelFactory.createDefaultModel();
		links = ModelFactory.createDefaultModel();
		provenance = ModelFactory.createDefaultModel();
		linkReport = ModelFactory.createDefaultModel();
		var storey = data.createResource(EX + "storey").addProperty(RDF.type, BOT.storey);
		var wallA = data.createResource(EX + "wall-a").addProperty(RDF.type, BOT.element)
				.addProperty(RDF.type, data.createResource("https://pi.pauwel.be/voc/buildingelement#Wall"))
				.addProperty(RDFS.label, "North wall");
		var wallB = data.createResource(EX + "wall-b").addProperty(RDF.type, BOT.element)
				.addProperty(RDF.type, data.createResource("https://pi.pauwel.be/voc/buildingelement#Wall"));
		storey.addProperty(BOT.containsElement, wallA).addProperty(BOT.containsElement, wallB);
		wallA.addLiteral(IFCtoLBDMapping.ifcGlobalId, "1hOSvn6df7F8_7GcBWlN4K");
		var property = data.createResource(EX + "property/fire-rating")
				.addProperty(RDF.type, data.createResource("https://w3id.org/opm#Property"))
				.addLiteral(data.createProperty("http://schema.org/value"), "EI60");
		wallA.addProperty(data.createProperty("http://lbd.arch.rwth-aachen.de/props#fireRating_property_simple"), property);
		var material = data.createResource(EX + "material/brick").addProperty(RDF.type, BMO.Material)
				.addProperty(RDFS.label, "Brick");
		wallA.addProperty(IFCtoLBDMapping.assignedMaterial, material);
		links.createResource(wallA.getURI()).addProperty(links.createProperty("http://purl.org/dc/terms/references"),
				links.createResource(EX + "documents/manual.pdf"));
		linkReport.createResource(EX + "mapping/1")
				.addProperty(linkReport.createProperty(ExternalLinkProcessor.NS + "resolvedElement"), linkReport.createResource(wallA.getURI()))
				.addLiteral(linkReport.createProperty(ExternalLinkProcessor.NS + "source"), "maintenance-register")
				.addLiteral(linkReport.createProperty(ExternalLinkProcessor.NS + "recordedAt"), "2026-01-01T00:00:00Z");
		provenance.createResource(wallA.getURI())
				.addLiteral(IFCtoLBDMapping.ifcGlobalId, "1hOSvn6df7F8_7GcBWlN4K")
				.addLiteral(provenance.createProperty("https://w3id.org/ifctolbd/evidence#scope"), "element");
		provenance.createResource("urn:ifctolbd:conversion:test")
				.addLiteral(provenance.createProperty(ConversionManifest.NS + "sourceChecksum"), "abc");
		retrieval = new GraphRetrievalService(data, links, provenance, linkReport);
	}

	@Test
	void findsElementsByTypeAndSpatialLocationWithPagination() {
		var first = retrieval.findElements(new ElementQuery(null, "Wall", EX + "storey"), 0, 1);
		assertEquals(Status.FOUND, first.status());
		assertEquals(2, first.totalAvailable());
		assertEquals(1, first.entities().size());
		assertTrue(first.truncated());

		var second = retrieval.findElements(new ElementQuery(null,
				"https://pi.pauwel.be/voc/buildingelement#Wall", EX + "storey"), 1, 1);
		assertEquals(Status.FOUND, second.status());
		assertEquals(EX + "wall-b", second.entities().get(0).identifier());
		assertFalse(second.truncated());
	}

	@Test
	void resolvesIfcIdentifierAndSeparatesPropertiesMaterialsAndLinks() {
		var result = retrieval.inspectElement("1hOSvn6df7F8_7GcBWlN4K", 100);
		assertEquals(Status.FOUND, result.status());
		assertEquals(EX + "wall-a", result.entities().get(0).identifier());
		assertTrue(result.entities().get(0).properties().stream()
				.anyMatch(statement -> statement.object().lexicalForm().equals("EI60")));
		assertTrue(result.entities().get(0).materials().stream()
				.anyMatch(statement -> statement.object().lexicalForm().equals("Brick")));
		assertEquals(EX + "documents/manual.pdf",
				result.entities().get(0).linkedRecords().get(0).object().lexicalForm());
		assertTrue(result.provenance().stream().anyMatch(item -> item.scope().equals("element")));
		assertTrue(result.provenance().stream().anyMatch(item -> item.scope().equals("external-link")
				&& item.statement().object().lexicalForm().equals("maintenance-register")));
	}

	@Test
	void distinguishesMissingEmptyUnsupportedAndTruncatedResults() {
		assertEquals(Status.NOT_FOUND, retrieval.inspectElement(EX + "missing", 20).status());
		assertEquals(Status.EMPTY, retrieval.linkedRecords(EX + "wall-b", 0, 20).status());
		assertEquals(Status.UNSUPPORTED, retrieval.findElements(new ElementQuery(null, null, null), 0, 20).status());
		var bounded = retrieval.inspectElement(EX + "wall-a", 1);
		assertEquals(Status.FOUND, bounded.status());
		assertTrue(bounded.truncated());
	}
}
